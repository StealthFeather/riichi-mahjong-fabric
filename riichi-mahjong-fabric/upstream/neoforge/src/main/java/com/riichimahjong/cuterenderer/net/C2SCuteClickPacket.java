package com.riichimahjong.cuterenderer.net;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.cuterenderer.InteractKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public record C2SCuteClickPacket(
        ResourceKey<Level> dim,
        BlockPos masterPos,
        InteractKey key) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<C2SCuteClickPacket> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "cute_click"));

    public static final StreamCodec<FriendlyByteBuf, C2SCuteClickPacket> STREAM_CODEC =
            StreamCodec.of(C2SCuteClickPacket::write, C2SCuteClickPacket::read);

    private static void write(FriendlyByteBuf buf, C2SCuteClickPacket msg) {
        buf.writeIdentifier(msg.dim.identifier());
        buf.writeBlockPos(msg.masterPos);
        InteractKey.write(buf, msg.key);
    }

    private static C2SCuteClickPacket read(FriendlyByteBuf buf) {
        Identifier dimId = buf.readIdentifier();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos pos = buf.readBlockPos();
        InteractKey key = InteractKey.read(buf);
        return new C2SCuteClickPacket(dim, pos, key);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
