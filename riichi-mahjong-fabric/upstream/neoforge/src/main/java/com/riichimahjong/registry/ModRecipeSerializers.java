package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, RiichiMahjong.MOD_ID);

    public static final Supplier<RecipeSerializer<MahjongAltarRecipe>> MAHJONG_ALTAR_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("mahjong_altar",
                    () -> new RecipeSerializer<>(MahjongAltarRecipe.CODEC, MahjongAltarRecipe.STREAM_CODEC));

    private ModRecipeSerializers() {}
}
