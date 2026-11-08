package com.riichimahjong.common.client;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

public final class HoverHintOverlay {
    private static final List<Function<Minecraft, String>> RESOLVERS = new CopyOnWriteArrayList<>();
    @Nullable
    private static String activeKey;

    private HoverHintOverlay() {}

    public static void register(Function<Minecraft, String> resolver) {
        RESOLVERS.add(resolver);
    }

    public static void registerTickHandler() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String next = (mc.player == null || mc.level == null) ? null : firstNonNullKey(mc);
        if (Objects.equals(activeKey, next)) return;
        ClientCompat.setOverlayMessage(mc,
                next == null ? Component.empty() : Component.translatable(next),
                false);
        activeKey = next;
    }

    @Nullable
    private static String firstNonNullKey(Minecraft mc) {
        for (Function<Minecraft, String> resolver : RESOLVERS) {
            String key = resolver.apply(mc);
            if (key != null) return key;
        }
        return null;
    }
}
