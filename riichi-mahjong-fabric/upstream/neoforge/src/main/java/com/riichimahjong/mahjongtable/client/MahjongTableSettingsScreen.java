package com.riichimahjong.mahjongtable.client;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableSettingsMenu;
import com.riichimahjong.mahjongtable.RuleSetPreset;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MahjongTableSettingsScreen extends AbstractContainerScreen<MahjongTableSettingsMenu> {
    private static final int PANEL_PADDING = 8;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_FROM_BOTTOM = 94;
    private static final int OUTER_BG = 0xC0101010;
    private static final int INNER_BG = 0xFF2C2C2C;
    private static final int LABEL_COLOR = 0xFFE0E0E0;

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTONS_TOP = MahjongTableSettingsMenu.CONTENT_TOP;
    private static final int BACK_BUTTON_WIDTH = 60;
    private static final int PANEL_WIDTH = PANEL_PADDING + BUTTON_WIDTH + PANEL_PADDING;
    private static final int PANEL_HEIGHT = MahjongTableSettingsMenu.CONTENT_TOP
            + MahjongTableSettingsMenu.CONTENT_HEIGHT
            + MahjongTableSettingsMenu.PLAYER_INV_TOP_GAP
            + MahjongTableSettingsMenu.HOTBAR_Y_OFFSET
            + MahjongTableSettingsMenu.HOTBAR_HEIGHT;

    public MahjongTableSettingsScreen(
            MahjongTableSettingsMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, PANEL_WIDTH, PANEL_HEIGHT);
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelY = this.imageHeight - INVENTORY_LABEL_FROM_BOTTOM;
    }

    @Override
    protected void init() {
        super.init();
        RuleSetPreset[] presets = RuleSetPreset.values();
        RuleSetPreset current = currentPreset();
        int x = leftPos + PANEL_PADDING;

        for (int i = 0; i < presets.length; i++) {
            int presetIdx = i;
            RuleSetPreset preset = presets[i];
            int y = topPos + BUTTONS_TOP + i * (BUTTON_HEIGHT + BUTTON_GAP);
            String prefix = preset == current ? "▶ " : "   ";
            addRenderableWidget(Button.builder(
                            Component.literal(prefix).append(Component.translatable(preset.langKey())),
                            b -> sendButton(MahjongTableSettingsMenu.BTN_PRESET_BASE + presetIdx))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }

        int backY = topPos + BUTTONS_TOP + presets.length * (BUTTON_HEIGHT + BUTTON_GAP) + BUTTON_GAP;
        addRenderableWidget(Button.builder(
                        Component.translatable("riichi_mahjong.button.table.back"),
                        b -> sendButton(MahjongTableSettingsMenu.BTN_BACK))
                .bounds(x, backY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private RuleSetPreset currentPreset() {
        if (minecraft == null || minecraft.level == null) return RuleSetPreset.MAHJONG_SOUL_4P;
        var be = minecraft.level.getBlockEntity(menu.getTablePos());
        if (be instanceof MahjongTableBlockEntity table) return table.preset();
        return RuleSetPreset.MAHJONG_SOUL_4P;
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, OUTER_BG);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, INNER_BG);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

}
