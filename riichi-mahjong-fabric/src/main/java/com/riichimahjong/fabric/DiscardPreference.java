package com.riichimahjong.fabric;

import com.mojang.serialization.Codec;
import com.riichimahjong.RiichiMahjong;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** The player's choice follows them between tables, sessions and respawns. */
public final class DiscardPreference {
    private static AttachmentType<Boolean> confirmation;

    private DiscardPreference() {}

    static void register() {
        confirmation = AttachmentRegistry.create(
                Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "confirm_discard"),
                builder -> builder.persistent(Codec.BOOL).copyOnDeath());
    }

    public static boolean requiresConfirmation(ServerPlayer player) {
        return ((AttachmentTarget) player).getAttachedOrElse(confirmation, false);
    }

    static void setConfirmation(ServerPlayer player, boolean enabled) {
        ((AttachmentTarget) player).setAttached(confirmation, enabled);
    }
}
