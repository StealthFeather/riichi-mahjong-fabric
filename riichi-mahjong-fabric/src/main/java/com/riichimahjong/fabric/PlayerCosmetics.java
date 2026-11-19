package com.riichimahjong.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.fabric.wineffect.WinEffects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Server-wide UUID preferences remain available to disconnected players' reserved seats. */
public final class PlayerCosmetics extends SavedData {
    public record Selection(RiichiSkin skin, String effect) {
        public static final Selection DEFAULT = new Selection(RiichiSkin.CLASSIC, "");
        static final Codec<Selection> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(RiichiSkin::parse, RiichiSkin::name).optionalFieldOf("stick", RiichiSkin.CLASSIC).forGetter(Selection::skin),
                Codec.STRING.optionalFieldOf("win", "").forGetter(Selection::effect)).apply(i, Selection::new));
        public String winEffect(String fallback) {
            return WinEffects.resolve(effect.isEmpty() ? fallback : effect).id();
        }
    }
    static final Codec<PlayerCosmetics> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Selection.CODEC)
            .xmap(PlayerCosmetics::new, data -> data.players);
    static final SavedDataType<PlayerCosmetics> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("riichi_mahjong", "player_cosmetics"), () -> new PlayerCosmetics(Map.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<UUID, Selection> players;
    PlayerCosmetics(Map<UUID, Selection> players) { this.players = new HashMap<>(players); }

    private static PlayerCosmetics storage(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public static Selection get(MinecraftServer server, UUID player) {
        return player == null ? Selection.DEFAULT : storage(server).selection(player);
    }
    Selection selection(UUID player) { return players.getOrDefault(player, Selection.DEFAULT); }
    void set(UUID player, Selection choice) {
        if (choice.equals(selection(player))) return;
        players.put(player, choice);
        setDirty();
    }
    public static RiichiSkin skin(ServerPlayer p) { return get(p.level().getServer(), p.getUUID()).skin(); }
    public static String winEffect(ServerPlayer p, String fallback) { return get(p.level().getServer(), p.getUUID()).winEffect(fallback); }
    static void chooseSkin(ServerPlayer p, RiichiSkin skin) {
        var data = storage(p.level().getServer());
        data.set(p.getUUID(), new Selection(skin, data.selection(p.getUUID()).effect()));
    }
    static void chooseEffect(ServerPlayer p, String id) {
        var data = storage(p.level().getServer());
        data.set(p.getUUID(), new Selection(data.selection(p.getUUID()).skin(), WinEffects.resolve(id).id()));
    }
}
