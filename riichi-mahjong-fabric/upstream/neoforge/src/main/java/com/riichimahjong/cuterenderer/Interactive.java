package com.riichimahjong.cuterenderer;

import net.minecraft.world.phys.AABB;

public record Interactive(InteractKey key, AABB localBounds, HoverPolicy policy) {
    public enum HoverPolicy {
        HIGHLIGHT_ONLY,

        LIFT_AND_CLICK
    }

    public static Interactive button(InteractKey key, AABB bounds) {
        return new Interactive(key, bounds, HoverPolicy.LIFT_AND_CLICK);
    }

    public static Interactive highlight(InteractKey key, AABB bounds) {
        return new Interactive(key, bounds, HoverPolicy.HIGHLIGHT_ONLY);
    }
}
