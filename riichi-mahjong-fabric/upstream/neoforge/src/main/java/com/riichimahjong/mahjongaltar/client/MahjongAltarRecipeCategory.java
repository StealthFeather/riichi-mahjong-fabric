package com.riichimahjong.mahjongaltar.client;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import com.riichimahjong.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MahjongAltarRecipeCategory implements IRecipeCategory<MahjongAltarRecipe> {
    public static final RecipeType<MahjongAltarRecipe> TYPE = RecipeType.create(
            RiichiMahjong.MOD_ID, "mahjong_altar", MahjongAltarRecipe.class);

    private static final int WIDTH = 112;
    private static final int HEIGHT = 68;
    private static final int GRID_COLUMNS = 3;
    private static final int SLOT_STEP = 18;
    private static final int OUTPUT_X = 94;
    private static final int OUTPUT_Y = 19;
    private static final int TEXT_Y = 57;
    private static final int TEXT_COLOR = 0xFF404040;

    private final IDrawable icon;

    public MahjongAltarRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.MAHJONG_ALTAR_ITEM.get());
    }

    @Override
    public IRecipeType<MahjongAltarRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("riichi_mahjong.recipe.mahjong_altar.title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MahjongAltarRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            int x = (i % GRID_COLUMNS) * SLOT_STEP;
            int y = (i / GRID_COLUMNS) * SLOT_STEP;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setStandardSlotBackground()
                    .addIngredients(recipe.ingredients().get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.displayResult());
    }

    @Override
    public void draw(MahjongAltarRecipe recipe, IRecipeSlotsView slotsView, GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
        graphics.text(
                net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("riichi_mahjong.recipe.mahjong_altar.min_han", recipe.minHan()),
                0,
                TEXT_Y,
                TEXT_COLOR,
                false);
    }

    @Override
    public Identifier getIdentifier(MahjongAltarRecipe recipe) {
        return Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "mahjong_altar");
    }
}
