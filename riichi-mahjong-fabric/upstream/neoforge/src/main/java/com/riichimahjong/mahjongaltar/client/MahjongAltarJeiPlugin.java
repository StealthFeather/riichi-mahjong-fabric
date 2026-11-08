package com.riichimahjong.mahjongaltar.client;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarRecipe;
import com.riichimahjong.registry.ModBlocks;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;

@JeiPlugin
public class MahjongAltarJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier PLUGIN_UID =
            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "jei_plugin");
    private static final String RECIPE_DIR = "data/" + RiichiMahjong.MOD_ID + "/recipe/mahjong_altar";

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new MahjongAltarRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(MahjongAltarRecipeCategory.TYPE, loadAltarRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(MahjongAltarRecipeCategory.TYPE, ModBlocks.MAHJONG_ALTAR_ITEM.get());
    }

    private static List<MahjongAltarRecipe> loadAltarRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        RegistryOps<JsonElement> ops =
                minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);

        IModFileInfo modFile = ModList.get().getModFileById(RiichiMahjong.MOD_ID);
        if (modFile == null) {
            LOGGER.warn("Mod file {} not found; altar recipes will not be listed in JEI", RiichiMahjong.MOD_ID);
            return List.of();
        }

        List<MahjongAltarRecipe> recipes = new ArrayList<>();
        modFile.getFile().getContents().visitContent(RECIPE_DIR, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            try (Reader reader = resource.bufferedReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                Recipe<?> recipe = Recipe.CODEC.parse(ops, json).getOrThrow();
                if (recipe instanceof MahjongAltarRecipe altar) {
                    recipes.add(altar);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to read altar recipe {}", relativePath, e);
            }
        });
        return recipes;
    }
}
