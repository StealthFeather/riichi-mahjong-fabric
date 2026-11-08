// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import com.riichimahjong.fabric.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, RiichiMahjong.MOD_ID);

    public static final Supplier<SoundEvent> TILE_PLACE_SOUND =
            SOUND_EVENTS.register("tile.place",
                    () -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "tile.place")));

    public static final Supplier<SoundEvent> TILE_HOVER_TILE_SOUND =
            SOUND_EVENTS.register("tile.hover.tile",
                    () -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "tile.hover.tile")));

    public static final Supplier<SoundEvent> TILE_HOVER_ACTION_SOUND =
            SOUND_EVENTS.register("tile.hover.action",
                    () -> SoundEvent.createVariableRangeEvent(
                            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "tile.hover.action")));

    private ModSounds() {}
}
