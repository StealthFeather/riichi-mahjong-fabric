package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongtable.MahjongTableMenu;
import com.riichimahjong.mahjongtable.MahjongTableSettingsMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, RiichiMahjong.MOD_ID);

    public static final Supplier<MenuType<MahjongTableMenu>> MAHJONG_TABLE_MENU =
            MENU_TYPES.register(
                    "mahjong_table_new",
                    () -> IMenuTypeExtension.create(MahjongTableMenu::fromNetwork));

    public static final Supplier<MenuType<MahjongTableSettingsMenu>> MAHJONG_TABLE_SETTINGS_MENU =
            MENU_TYPES.register(
                    "mahjong_table_new_settings",
                    () -> IMenuTypeExtension.create(MahjongTableSettingsMenu::fromNetwork));

    public static final Supplier<MenuType<com.riichimahjong.yakugenerator.YakuGeneratorMenu>> YAKU_GENERATOR_MENU =
            MENU_TYPES.register(
                    "yaku_generator",
                    () -> IMenuTypeExtension.create((id, inv, buf) ->
                            new com.riichimahjong.yakugenerator.YakuGeneratorMenu(id, inv, buf.readBlockPos())));

    private ModMenus() {}
}
