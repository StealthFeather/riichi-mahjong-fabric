package com.riichimahjong.mahjongtable;

import com.mojang.serialization.Codec;
import com.riichimahjong.RiichiMahjong;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class BloodDrawHealth {
    private static final double HEAL_BLOCK_RADIUS = 32.0;
    private static final Map<MahjongTableBlockEntity, List<UUID>> activeTables = new ConcurrentHashMap<>();
    private static final ScopedValue<ServerPlayer> startingPlayer = ScopedValue.newInstance();

    private BloodDrawHealth() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var debts = server.overworld().getDataStorage().get(Debts.TYPE);
            if (debts != null) debts.collect(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> activeTables.clear());
    }

    public static void start(MahjongTableBlockEntity table, List<UUID> players) {
        if (!(table.getLevel() instanceof ServerLevel level)) return;
        attach(table, players);
        for (UUID id : players) {
            var player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.isAlive()) {
                ScopedValue.where(startingPlayer, player).run(() -> player.setHealth(20.0F));
            }
        }
    }

    public static void attach(MahjongTableBlockEntity table, List<UUID> players) {
        if (table.getLevel() instanceof ServerLevel && !table.isRemoved() && !players.isEmpty()) {
            activeTables.put(table, List.copyOf(players));
        }
    }

    public static void detach(MahjongTableBlockEntity table) {
        activeTables.remove(table);
    }

    public static boolean blocksHealing(ServerPlayer player) {
        if (activeTables.isEmpty() || startingPlayer.isBound() && startingPlayer.get() == player) return false;
        var level = player.level();
        // Login and respawn construct a fresh entity and load its health before it enters play.
        if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return false;
        for (var entry : activeTables.entrySet()) {
            var table = entry.getKey();
            if (table.getLevel() == level && !table.isRemoved() && entry.getValue().contains(player.getUUID())
                    && table.getBlockPos().distToCenterSqr(player.position()) <= HEAL_BLOCK_RADIUS * HEAL_BLOCK_RADIUS) {
                return true;
            }
        }
        return false;
    }

    public static void pay(ServerLevel level, UUID playerId, long points) {
        if (points <= 0) return;
        var server = level.getServer();
        var player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            server.overworld().getDataStorage().computeIfAbsent(Debts.TYPE).add(playerId, points);
        } else {
            deduct(player, points);
        }
    }

    private static void deduct(ServerPlayer player, long points) {
        if (!player.isAlive()) return;
        // Repeated tenths must reach zero exactly even though Minecraft stores health as a float.
        var health = new BigDecimal(Float.toString(player.getHealth()));
        float remaining = health.subtract(BigDecimal.valueOf(points, 3)).max(BigDecimal.ZERO).floatValue();
        player.setHealth(remaining);
        if (remaining == 0) {
            // A point payment can fall due while the client has loading-screen damage immunity.
            player.die(player.damageSources().genericKill());
        }
    }

    /** Debts belong to the world so a finished or removed table can still collect after login. */
    private static final class Debts extends SavedData {
        private static final Codec<Debts> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                .xmap(Debts::new, debts -> Map.copyOf(debts.points));
        private static final SavedDataType<Debts> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "blood_draw_debts"),
                () -> new Debts(Map.of()), CODEC, null);

        private final Map<UUID, Long> points;

        private Debts(Map<UUID, Long> points) {
            this.points = new HashMap<>(points);
        }

        private void add(UUID player, long amount) {
            points.merge(player, amount, Math::addExact);
            setDirty();
        }

        private void collect(ServerPlayer player) {
            Long owed = points.remove(player.getUUID());
            if (owed != null) {
                setDirty();
                deduct(player, owed);
            }
        }
    }
}
