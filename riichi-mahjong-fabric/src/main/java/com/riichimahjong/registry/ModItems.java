// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import com.riichimahjong.fabric.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(RiichiMahjong.MOD_ID);

    public static final Supplier<Item> MAHJONG_FAKE_STAR = ITEMS.registerItem(
            "mahjong_fake_star",
            props -> new com.riichimahjong.fabric.PortItem(props),
            props -> props.stacksTo(64));

    public static final Supplier<Item> MAHJONG_STAR = ITEMS.registerItem(
            "mahjong_star",
            props -> new com.riichimahjong.fabric.PortItem(props),
            props -> props.stacksTo(64));

    public static final Supplier<Item> MAHJONG_WRENCH = ITEMS.registerItem(
            "mahjong_wrench",
            props -> new com.riichimahjong.fabric.PortItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> AKA_MANBOU_PAINTING_ITEM = ITEMS.registerItem(
            "aka_manbou_painting",
            props -> new com.riichimahjong.gimmicks.VariantPaintingItem(
                    props,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "aka_manbou_1"),
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "aka_manbou_2"),
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "aka_manbou_3")),
            p -> p.stacksTo(64));

    public static final Supplier<Item> ZUNDAMON_HAPPY_PAINTING_ITEM = ITEMS.registerItem(
            "zundamon_happy_painting",
            props -> new com.riichimahjong.gimmicks.VariantPaintingItem(
                    props,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "zundamon_happy")),
            p -> p.stacksTo(64));

    public static final Supplier<Item> MAHJONG_RED_DRAGON_SOUL = ITEMS.registerItem(
            "mahjong_red_dragon_soul",
            props -> new com.riichimahjong.fabric.PortItem(props),
            props -> props.stacksTo(64));

    public static final Supplier<Item> MAHJONG_RIICHI_STICK = ITEMS.registerItem(
            "mahjong_riichi_stick",
            props -> new com.riichimahjong.mahjongtools.MahjongRiichiStickItem(props),
            props -> props.stacksTo(64));

    public static final Supplier<Item> LIQUID_EXPERIENCE_BUCKET = ITEMS.registerItem(
            "liquid_experience_bucket",
            props -> new com.riichimahjong.fabric.PortBucketItem(com.riichimahjong.registry.ModFluids.LIQUID_EXPERIENCE.get(), props),
            props -> props.craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));

    public static final Supplier<Item> MAHJONG_TABLE_RECORD = ITEMS.registerItem(
            "mahjong_table_record",
            props -> new com.riichimahjong.mahjongtable.record.MahjongTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_CHUUREN_TABLE_RECORD = ITEMS.registerItem(
            "predefined_chuuren_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedChuurenpoutouTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_KOKUSHIMUSOU_TABLE_RECORD = ITEMS.registerItem(
            "predefined_kokushimusou_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedKokushimusouTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_SUUANKOU_TSUMO_TABLE_RECORD = ITEMS.registerItem(
            "predefined_suuankou_tsumo_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedSuuankouTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_KAZOE_STYLE_TSUMO_TABLE_RECORD = ITEMS.registerItem(
            "predefined_kazoe_style_tsumo_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedKazoeYakumanTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_TRIPLE_KAN_OPTIONS_TSUMO_TABLE_RECORD = ITEMS.registerItem(
            "predefined_triple_kan_options_tsumo_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedTripleKanTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_KAKAN_OPTIONS_TABLE_RECORD = ITEMS.registerItem(
            "predefined_kakan_options_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedKakanOptionsTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_RINSHAN_TABLE_RECORD = ITEMS.registerItem(
            "predefined_rinshan_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedRinshanTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_CHANKAN_TABLE_RECORD = ITEMS.registerItem(
            "predefined_chankan_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedChankanTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_TENHOU_TABLE_RECORD = ITEMS.registerItem(
            "predefined_tenhou_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedTenhouTableRecordItem(props),
            props -> props.stacksTo(1));

    public static final Supplier<Item> PREDEFINED_PINFU_TSUMO_TABLE_RECORD = ITEMS.registerItem(
            "predefined_pinfu_tsumo_table_record",
            props -> new com.riichimahjong.mahjongtable.record.PredefinedPinfuTableRecordItem(props),
            props -> props.stacksTo(1));

    private ModItems() {}
}
