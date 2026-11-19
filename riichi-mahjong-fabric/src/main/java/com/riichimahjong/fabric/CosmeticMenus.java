package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.WinEffects;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class CosmeticMenus {
    private static final int EFFECTS_PER_PAGE = 18;

    private CosmeticMenus() {}
    public static void open(ServerPlayer player, MahjongTableBlockEntity table) {
        open(player, table, 0);
    }

    private static void open(ServerPlayer player, MahjongTableBlockEntity table, int page) {
        PortMenus.openTablePage(player, table, "个人设置", menu -> {
            var selected = PlayerCosmetics.skin(player);
            for (var skin : RiichiSkin.values()) {
                var icon = PortViews.model(skin.model());
                icon.set(DataComponents.CUSTOM_NAME, Component.literal("立直皮肤 · " + skin.label() + (skin == selected ? " · 已选" : "")));
                menu.button(10 + skin.ordinal(), icon, () -> {
                    PlayerCosmetics.chooseSkin(player, skin);
                    open(player, table, page);
                });
            }
            String effectId = PlayerCosmetics.winEffect(player, table.winEffectId());
            var effects = WinEffects.all();
            int offset = page * EFFECTS_PER_PAGE;
            for (int slot = 0; slot < EFFECTS_PER_PAGE && offset + slot < effects.size(); slot++) {
                var effect = effects.get(offset + slot);
                var icon = switch (effect.id()) {
                    case "riichi_mahjong:evoker_fangs" -> Items.EVOKER_SPAWN_EGG;
                    case "riichi_mahjong:enderman" -> Items.ENDERMAN_SPAWN_EGG;
                    case "riichi_mahjong:warden" -> Items.WARDEN_SPAWN_EGG;
                    default -> Items.PAPER;
                };
                menu.button(27 + slot, PortMenus.icon(icon,
                        "胡牌效果 · " + effect.displayName() + (effect.id().equals(effectId) ? " · 已选" : "")), () -> {
                    PlayerCosmetics.chooseEffect(player, effect.id());
                    open(player, table, page);
                });
            }
            if (page > 0) menu.button(45, PortMenus.icon(Items.ARROW, "上一页胡牌效果"), () -> open(player, table, page - 1));
            if (offset + EFFECTS_PER_PAGE < effects.size())
                menu.button(53, PortMenus.icon(Items.ARROW, "下一页胡牌效果"), () -> open(player, table, page + 1));
            boolean confirm = DiscardPreference.requiresConfirmation(player);
            var mode = PortMenus.icon(confirm ? Items.GLOW_INK_SAC : Items.INK_SAC, "我的出牌方式：" + (confirm ? "二次确认" : "右键直接出牌"));
            mode.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("二次确认时，右键选牌，再次右键发光的牌出牌。"),
                    Component.literal("右键另一张手牌可改选。"))));
            menu.button(19, mode, () -> {
                DiscardPreference.setConfirmation(player, !DiscardPreference.requiresConfirmation(player));
                table.clearDiscardSelection(player.getUUID());
                open(player, table, page);
            });
            PortMenus.backToTable(menu, player, table);
        });
    }
}
