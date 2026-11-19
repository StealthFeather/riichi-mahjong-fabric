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
    private CosmeticMenus() {}
    public static void open(ServerPlayer player, MahjongTableBlockEntity table) {
        PortMenus.openTablePage(player, table, "个人设置", menu -> {
            var selected = PlayerCosmetics.skin(player);
            for (var skin : RiichiSkin.values()) {
                var icon = PortViews.model(skin.model());
                icon.set(DataComponents.CUSTOM_NAME, Component.literal("立直皮肤 · " + skin.label() + (skin == selected ? " · 已选" : "")));
                menu.button(10 + skin.ordinal(), icon, () -> {
                    PlayerCosmetics.chooseSkin(player, skin);
                    open(player, table);
                });
            }
            String effectId = PlayerCosmetics.winEffect(player, table.winEffectId());
            // Cycle the registry to support additional registered effects without consuming menu slots.
            var effects = WinEffects.all();
            for (int i = 0; i < effects.size() && i < 2; i++) {
                var effect = effects.get(i);
                menu.button(28 + i, PortMenus.icon(i == 0 ? Items.EVOKER_SPAWN_EGG : Items.PAPER,
                        "胡牌效果 · " + effect.displayName() + (effect.id().equals(effectId) ? " · 已选" : "")), () -> {
                    PlayerCosmetics.chooseEffect(player, effect.id());
                    open(player, table);
                });
            }
            if (effects.size() > 2) menu.button(32, PortMenus.icon(Items.COMPARATOR, "切换胡牌效果 · " + WinEffects.resolve(effectId).displayName()), () -> {
                PlayerCosmetics.chooseEffect(player, effects.get((effects.indexOf(WinEffects.resolve(effectId)) + 1) % effects.size()).id());
                open(player, table);
            });
            boolean confirm = DiscardPreference.requiresConfirmation(player);
            var mode = PortMenus.icon(confirm ? Items.GLOW_INK_SAC : Items.INK_SAC, "我的出牌方式：" + (confirm ? "二次确认" : "右键直接出牌"));
            mode.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("二次确认时，右键选牌，再次右键发光的牌出牌。"),
                    Component.literal("右键另一张手牌可改选。"))));
            menu.button(30, mode, () -> {
                DiscardPreference.setConfirmation(player, !DiscardPreference.requiresConfirmation(player));
                table.clearDiscardSelection(player.getUUID());
                open(player, table);
            });
            PortMenus.backToTable(menu, player, table);
        });
    }
}
