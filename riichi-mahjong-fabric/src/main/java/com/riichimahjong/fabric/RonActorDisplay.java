package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.WinEffectFrame.Action;
import com.riichimahjong.fabric.wineffect.WinEffectFrame.Actor;
import com.riichimahjong.fabric.wineffect.WinEffectFrame.ActorPose;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.EntityElement;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** The table owns these visual entities. The server world never spawns or ticks the mobs. */
interface RonActorDisplay {
    double TILE_HEIGHT = TableLayout.TILE_SCALE * (TableLayout.TILE_MODEL_BOUNDS.maxY - TableLayout.TILE_MODEL_BOUNDS.minY);
    double MOB_HEIGHT = TILE_HEIGHT * 1.5;

    void update(ActorPose pose);
    void destroy();

    static RonActorDisplay create(ElementHolder holder, ServerLevel level, ActorPose pose,
                                  Vec3 felt, Vec3 facing, Vec3 tile) {
        if (pose.kind() == Actor.FANGS) {
            // A diagonal bite puts both jaws in profile from all four seats.
            return new SmallFangsDisplay(holder, felt, 0, pose.age(), .6f, (float)(Math.PI / 4));
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
        var wardenFeet = felt.subtract(facing.scale(TableLayout.TILE_WIDTH));
        if (pose.kind() == Actor.SONIC_WAVE) {
            var type = EntityTypes.WARDEN;
            var attachments = type.getDimensions().scale((float)(MOB_HEIGHT / type.getHeight())).attachments();
            var chest = wardenFeet.add(attachments.get(EntityAttachment.WARDEN_CHEST, 0, yaw));
            // Aim at the exposed edge of the discard, in the direction it will fly.
            var impact = tile.add(facing.scale(TableLayout.TILE_WIDTH * .5));
            return new SonicWaveDisplay(holder, chest, impact, pose);
        }
        Mob mob;
        Vec3 feet;
        switch (pose.kind()) {
            case ENDERMAN -> {
                mob = new Enderman(EntityTypes.ENDERMAN, level);
                feet = felt.subtract(facing.scale(TableLayout.TILE_WIDTH * .46));
            }
            case EVOKER -> {
                mob = new Evoker(EntityTypes.EVOKER, level);
                feet = felt;
            }
            case WARDEN -> {
                mob = new Warden(EntityTypes.WARDEN, level);
                feet = wardenFeet;
            }
            default -> throw new IllegalArgumentException("Unknown Ron mob " + pose.kind());
        }
        mob.setSilent(true);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.getAttribute(Attributes.SCALE).setBaseValue(MOB_HEIGHT / mob.getType().getHeight());
        mob.refreshDimensions();
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        return new RonMobDisplay(holder, level, mob, feet, pose);
    }
}

final class RonMobDisplay extends EntityElement<Mob> implements RonActorDisplay {
    private final Vec3 feet;
    private Action action;
    private boolean startsCharge;

    RonMobDisplay(ElementHolder holder, ServerLevel level, Mob mob, Vec3 feet, ActorPose pose) {
        // The tracker snapshots the spawn position and rotation in its constructor.
        super(positionMob(mob, feet, pose), level);
        this.feet = feet;
        setOverridePos(mob.position());
        updateAction(pose);
        holder.addElement(this);
    }

    private static Mob positionMob(Mob mob, Vec3 feet, ActorPose pose) {
        mob.setPos(feet.add(0, pose.y(), 0));
        if (mob instanceof Warden) {
            double depth = pose.action() == Action.EMERGE ? Math.clamp(-pose.y() / MOB_HEIGHT, 0, 1) : 0;
            mob.setXRot((float)(18 - 48 * depth));
        }
        return mob;
    }

    @Override public void update(ActorPose pose) {
        positionMob(entity(), feet, pose);
        setOverridePos(entity().position());
        updateAction(pose);
    }

    private void updateAction(ActorPose pose) {
        startsCharge = pose.action() == Action.SONIC && pose.age() == 0;
        if (action == pose.action()) return;
        action = pose.action();
        if (entity() instanceof Enderman enderman) {
            // A rendered block activates the vanilla carrying arms. Keep the tiny button
            // tucked between its hands while the independent display preserves the exact tile face.
            enderman.setCarriedBlock(action == Action.CARRY ? Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState() : null);
        } else if (entity() instanceof Evoker evoker) {
            // This vanilla pose waves both raised arms. Spell metadata also emits
            // fixed-size client particles that would cover the miniature and the tiles.
            evoker.setCelebrating(action == Action.CAST);
        } else if (getHolder() != null && startsCharge) {
            getHolder().sendPacket(new ClientboundEntityEventPacket(entity(), EntityEvent.SONIC_CHARGE));
        }
    }

    @Override public void startWatching(ServerPlayer player, Consumer<Packet<ClientGamePacketListener>> send) {
        super.startWatching(player, send);
        // The client can't seek a one-shot animation. Replaying it for a late
        // viewer would show a new windup after the table has already fired.
        if (startsCharge) send.accept(new ClientboundEntityEventPacket(entity(), EntityEvent.SONIC_CHARGE));
    }

    @Override public void destroy() { getHolder().removeElement(this); }
}

/** The native boom hits along its full ray on release. Miniature rings mark that ray for four ticks. */
final class SonicWaveDisplay implements RonActorDisplay {
    private static final int RINGS = 3, SIDES = 8;
    private final ElementHolder holder;
    private final Vec3 chest, tile;
    private final Quaternionf plane;
    private final BlockDisplayElement[] segments = new BlockDisplayElement[RINGS * SIDES];

    SonicWaveDisplay(ElementHolder holder, Vec3 chest, Vec3 tile, ActorPose pose) {
        this.holder = holder;
        this.chest = chest;
        this.tile = tile;
        var ray = tile.subtract(chest).normalize();
        plane = new Quaternionf().rotationTo(0, 0, 1, (float) ray.x, (float) ray.y, (float) ray.z);
        for (int i = 0; i < segments.length; i++) {
            var segment = new BlockDisplayElement(Blocks.DIAMOND_BLOCK.defaultBlockState());
            segment.setBrightness(Brightness.FULL_BRIGHT);
            segment.setDisplaySize(.4f, .4f);
            segment.setInterpolationDuration(1);
            segments[i] = segment;
        }
        update(pose);
        for (var segment : segments) holder.addElement(segment);
    }

    @Override public void update(ActorPose pose) {
        for (int ring = 0; ring < RINGS; ring++) {
            double along = (ring + 1.0) / RINGS;
            var center = chest.lerp(tile, along);
            float radius = (float)(TableLayout.TILE_WIDTH * (.18 + .24 * along + .04 * pose.age()));
            float width = (float)(2 * radius * Math.tan(Math.PI / SIDES));
            for (int side = 0; side < SIDES; side++) {
                var segment = segments[ring * SIDES + side];
                segment.setOverridePos(center);
                segment.setTransformation(new Matrix4f().rotate(plane).rotateZ((float)(side * 2 * Math.PI / SIDES))
                        .translate(radius, 0, 0).scale(.01f, width, .01f).translate(-.5f, -.5f, -.5f));
                segment.startInterpolationIfDirty();
            }
        }
    }

    @Override public void destroy() {
        for (var segment : segments) holder.removeElement(segment);
    }
}
