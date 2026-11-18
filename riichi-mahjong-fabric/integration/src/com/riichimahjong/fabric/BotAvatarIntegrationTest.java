package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.ai.RandomThinkingBot;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.TheMahjongTile;
import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/** Fabric bootstrap probe: vanilla templates and codecs; no server/world/client acceptance. */
public final class BotAvatarIntegrationTest implements ModInitializer {
    private static final List<String> checks = new ArrayList<>();
    private static final Map<String, Object> result = new LinkedHashMap<>();

    @Override public void onInitialize() {
        if (!Files.exists(Path.of("riichi-bot-avatar-probe-enabled"))) {
            throw new IllegalStateException("Avatar probe requires its disposable-runtime marker");
        }
        int exit = 0;
        try {
            geometry();
            visibility();
            handTargets();
            vanillaTemplatesAndPackets();
            result.put("passed", true);
        } catch (Throwable error) {
            error.printStackTrace();
            result.put("passed", false);
            result.put("failure", error.toString());
            exit = 1;
        }
        result.put("checks", checks);
        result.put("serverWorldStarted", false);
        result.put("clientVisualTest", false);
        result.put("entityElementLifecycleTest", false);
        try {
            Files.writeString(Path.of("bot-avatar-probe-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (Exception failure) {
            failure.printStackTrace(); exit = 2;
        }
        System.out.println("RIICHI_BOT_AVATAR_PROBE " + new GsonBuilder().create().toJson(result));
        System.exit(exit);
    }

    private static void geometry() {
        var pos = new BlockPos(7, 80, -11);
        int count = 0;
        for (var facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            var state = ModBlocks.MAHJONG_TABLE.get().defaultBlockState().setValue(BaseMultipartBlock.FACING, facing);
            var directions = new java.util.HashSet<Direction>();
            for (int seat = 0; seat < 4; seat++) {
                var direction = PortViews.outward(seat, state);
                require(directions.add(direction), "two seats occupy the same side");
                var feet = BotAvatars.seatPosition(pos, direction);
                var target = BotAvatars.idleTarget(pos, direction);
                var relative = feet.subtract(Vec3.atLowerCornerOf(pos).add(.5, 0, .5));
                require(Math.abs(relative.dot(direction.getUnitVec3()) - 1.85) < 1e-8,
                        "avatar does not clear the 3x3 table edge");
                require(Math.abs(relative.dot(direction.getClockWise().getUnitVec3())) < 1e-8,
                        "avatar is not centered on its seat");
                require(target.subtract(feet).dot(direction.getUnitVec3()) < 0,
                        "avatar looks away from the table");
                require(Direction.fromYRot(direction.getOpposite().toYRot()) == direction.getOpposite(),
                        "body yaw faces away from table");
                count++;
            }
        }
        result.put("seatOrientationChecks", count);
        checks.add("all-four-table-rotations-and-four-seat-positions");
    }

    private static void visibility() {
        var open = MahjongTableBlockEntity.SeatInfo.open();
        var closed = MahjongTableBlockEntity.SeatInfo.closed();
        var human = MahjongTableBlockEntity.SeatInfo.claimed(UUID.randomUUID());
        var ai = new KobalabBot(BotDifficulty.NORMAL);
        var sanma = new RandomThinkingBot();
        try {
            require(!BotAvatars.shouldShow(open, false, null), "empty lobby seat renders an avatar before a match");
            require(!BotAvatars.shouldShow(open, false, ai), "lobby retains an avatar after a match ends");
            require(!BotAvatars.shouldShow(closed, false, null), "closed lobby seat has an avatar");
            require(!BotAvatars.shouldShow(human, false, null), "human lobby seat has an avatar");
            require(BotAvatars.shouldShow(open, true, ai), "four-player AI has no avatar");
            require(BotAvatars.shouldShow(open, true, sanma), "three-player BOT has no avatar");
            require(BotAvatars.shouldShow(human, true, ai), "AI takeover of a reserved seat has no avatar");
            require(!BotAvatars.shouldShow(open, true, new MahjongTableHumanPlayer()), "human takeover retains avatar");
            require(!BotAvatars.shouldShow(closed, true, ai), "closed seat renders an active avatar");
            require(!BotAvatars.shouldShow(open, true, null), "seat outside active player count renders an avatar");
        } finally { ai.close(); }
        checks.add("active-only-enabled-seats-human-takeover-sanma-and-offline-ai-visibility");
    }

    private static void handTargets() {
        var ordinary = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false);
        var red = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true);
        var order = List.of(ordinary, red, ordinary);
        require(BotAvatars.handIndex(order, ordinary, false) == 0, "ordinary discard target differs from removed tile");
        require(BotAvatars.handIndex(order, ordinary, true) == 2, "drawn duplicate target differs from removed tile");
        require(BotAvatars.handIndex(order, red, true) == 1, "red five target lost its identity");
        require(BotAvatars.handIndex(List.of(), ordinary, true) == -1, "empty hand creates a target");
        checks.add("discard-look-target-matches-drawn-duplicate-and-red-tile-slot");
    }

    private static void vanillaTemplatesAndPackets() {
        require(BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.MANNEQUIN).toString().equals("minecraft:mannequin"),
                "vanilla mannequin type missing");
        // Polymer creates these templates in its own FakeWorld. No game server/world is created.
        var example = InternalEntityHelpers.getEntity(EntityTypes.MANNEQUIN);
        require(example instanceof Mannequin, "Polymer cannot construct a vanilla mannequin template");
        var mannequin = new Mannequin(EntityTypes.MANNEQUIN, example.level());
        var anchor = new ArmorStand(EntityTypes.ARMOR_STAND, example.level());
        // FakeWorld has no entity-ID allocator; assign distinct probe-only packet IDs.
        mannequin.setId(700001);
        anchor.setId(700002);
        anchor.setInvisible(true);
        anchor.setNoGravity(true);
        anchor.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) 0x10);
        anchor.setPos(4, 8, 12);
        require(mannequin.startRiding(anchor, true, true), "mannequin rejects marker mount");
        require(mannequin.isPassenger(), "mannequin has no riding pose state");
        require(mannequin.getProfile().equals(Mannequin.DEFAULT_PROFILE), "default vanilla skin profile changed");
        var mount = new ClientboundSetPassengersPacket(anchor);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientboundSetPassengersPacket.STREAM_CODEC.encode(buffer, mount);
            var decoded = ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer);
            require(decoded.getVehicle() == anchor.getId() && decoded.getPassengers().length == 1
                    && decoded.getPassengers()[0] == mannequin.getId(), "mount packet lost mannequin passenger");
            require(!buffer.isReadable(), "mount codec left unread bytes");
        } finally { buffer.release(); }
        var swing = new ClientboundSwingAnimationPacket(mannequin.getId(), InteractionHand.MAIN_HAND,
                new SwingAnimation(SwingAnimationType.WHACK, 6));
        var swingBuffer = Unpooled.buffer();
        try {
            ClientboundSwingAnimationPacket.STREAM_CODEC.encode(swingBuffer, swing);
            require(swing.equals(ClientboundSwingAnimationPacket.STREAM_CODEC.decode(swingBuffer)),
                    "26.3 attack animation packet failed to round-trip");
            require(!swingBuffer.isReadable(), "swing codec left unread bytes");
        } finally { swingBuffer.release(); }
        result.put("templateMannequinMetadataEntries", mannequin.getEntityData().getNonDefaultValues() == null ? 0
                : mannequin.getEntityData().getNonDefaultValues().size());
        result.put("vanillaAttackAnimation", "main_hand/whack/6_ticks");
        checks.add("real-vanilla-mannequin-template-and-marker-passenger-state");
        checks.add("vanilla-26.3-passengers-and-attack-animation-packet-codecs");
        // These local templates die with the probe. Dismounting would query FakeWorld chunks.
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
