// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import com.riichimahjong.fabric.DeferredRegister;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, RiichiMahjong.MOD_ID);

    public static final Supplier<RecipeType<MahjongAltarRecipe>> MAHJONG_ALTAR =
            RECIPE_TYPES.register("mahjong_altar",
                    () -> new RecipeType<MahjongAltarRecipe>() {
                        @Override public String toString() { return "riichi_mahjong:mahjong_altar"; }
                    });

    private ModRecipeTypes() {}
}
