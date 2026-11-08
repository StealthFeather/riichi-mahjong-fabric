package com.riichimahjong.mahjongaltar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.registry.ModRecipeSerializers;
import com.riichimahjong.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class MahjongAltarRecipe implements Recipe<AltarRecipeInput> {
    private final List<Ingredient> ingredients;
    private final ItemStackTemplate result;
    private final int minHan;

    public MahjongAltarRecipe(List<Ingredient> ingredients, ItemStackTemplate result, int minHan) {
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        this.minHan = Math.max(0, minHan);
    }

    public int minHan() {
        return minHan;
    }

    public ItemStack displayResult() {
        return result.create();
    }

    @Override
    public boolean matches(AltarRecipeInput input, Level level) {
        ArrayList<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        return matchesInputs(inputs);
    }

    public boolean matchesInputs(List<ItemStack> inputs) {
        if (inputs.size() != ingredients.size()) {
            return false;
        }
        boolean[] used = new boolean[inputs.size()];
        return matchIngredientsRecursive(0, inputs, used);
    }

    private boolean matchIngredientsRecursive(int ingredientIndex, List<ItemStack> inputs, boolean[] used) {
        if (ingredientIndex >= ingredients.size()) {
            return true;
        }
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int i = 0; i < inputs.size(); i++) {
            if (used[i]) continue;
            if (!ingredient.test(inputs.get(i))) continue;
            used[i] = true;
            if (matchIngredientsRecursive(ingredientIndex + 1, inputs, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    @Override
    public ItemStack assemble(AltarRecipeInput input) {
        return result.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<AltarRecipeInput>> getSerializer() {
        return ModRecipeSerializers.MAHJONG_ALTAR_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<AltarRecipeInput>> getType() {
        return ModRecipeTypes.MAHJONG_ALTAR.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public static final MapCodec<MahjongAltarRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Ingredient.CODEC.listOf().fieldOf("ingredients")
                            .forGetter(r -> r.ingredients),
                    ItemStackTemplate.CODEC.fieldOf("result")
                            .forGetter(r -> r.result),
                    com.mojang.serialization.Codec.INT.optionalFieldOf("min_han", 0)
                            .forGetter(MahjongAltarRecipe::minHan)
            ).apply(instance, MahjongAltarRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MahjongAltarRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    r -> r.ingredients,
                    ItemStackTemplate.STREAM_CODEC,
                    r -> r.result,
                    ByteBufCodecs.VAR_INT,
                    MahjongAltarRecipe::minHan,
                    MahjongAltarRecipe::new);
}
