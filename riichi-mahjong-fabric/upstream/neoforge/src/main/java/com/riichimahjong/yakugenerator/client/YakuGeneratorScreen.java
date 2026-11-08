package com.riichimahjong.yakugenerator.client;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import com.riichimahjong.yakugenerator.YakuGeneratorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class YakuGeneratorScreen extends AbstractContainerScreen<YakuGeneratorMenu> {
    private static final int TILE_ROW_X = 8;
    private static final int TILE_ROW_Y = 44;
    private static final int TILE_STEP_X = 16;
    private static final int TILE_CLICK_W = 16;
    private static final int TILE_CLICK_H = 20;
    private static final float TILE_SCALE_X = 1.0f;
    private static final float TILE_SCALE_Y = 1.12f;
    private Button autoSortButton;
    private Button tsumoButton;
    private static final int DEBUG_BUTTON_W = 32;
    private static final int DEBUG_BUTTON_H = 14;
    private static final int DEBUG_BUTTON_GAP_Y = 1;
    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 210;

    public YakuGeneratorScreen(YakuGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
        this.inventoryLabelY = 3000;
    }

    @Override
    protected void init() {
        super.init();
        int buttonsTop = topPos + 18;
        addRenderableWidget(Button.builder(
                        Component.translatable("riichi_mahjong.screen.yaku_generator.reset"),
                        b -> sendMenuButton(YakuGeneratorMenu.BTN_RESET))
                .bounds(leftPos + 10, buttonsTop, 72, 20)
                .build());
        tsumoButton = addRenderableWidget(Button.builder(
                        Component.empty(),
                        b -> sendMenuButton(YakuGeneratorMenu.BTN_TSUMO))
                .bounds(leftPos + 84, buttonsTop, 74, 22)
                .build());
        autoSortButton = addRenderableWidget(Button.builder(
                        Component.empty(),
                        b -> sendMenuButton(YakuGeneratorMenu.BTN_TOGGLE_AUTO_SORT))
                .bounds(leftPos + 160, buttonsTop, 80, 20)
                .build());
        if (minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild) {
            addCreativeDebugButtons();
        }
    }

    private void addCreativeDebugButtons() {
        String[] labels = {"NoY", "1-4", "Man", "Hane", "Bai", "San", "Yak"};
        int x = leftPos + imageWidth - DEBUG_BUTTON_W - 8;
        int y0 = topPos + 76;
        for (int i = 0; i < labels.length; i++) {
            int y = y0 + i * (DEBUG_BUTTON_H + DEBUG_BUTTON_GAP_Y);
            final int outcome = i;
            addRenderableWidget(Button.builder(
                            Component.literal(labels[i]),
                            b -> sendMenuButton(YakuGeneratorMenu.BTN_DEBUG_BASE + outcome))
                    .bounds(x, y, DEBUG_BUTTON_W, DEBUG_BUTTON_H)
                    .build());
        }
    }

    private void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (autoSortButton != null) {
            autoSortButton.setMessage(Component.translatable(
                    "riichi_mahjong.screen.yaku_generator.auto_sort",
                    Component.translatable(menu.isAutoSortEnabled()
                            ? "riichi_mahjong.screen.yaku_generator.on"
                            : "riichi_mahjong.screen.yaku_generator.off")));
        }
        if (tsumoButton != null) {
            boolean pulse = minecraft != null
                    && minecraft.level != null
                    && ((minecraft.level.getGameTime() / 8L) % 2L == 0L);
            Component tsumoLabel = Component.translatable("riichi_mahjong.screen.yaku_generator.tsumo")
                    .withStyle(
                            pulse ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
                            ChatFormatting.BOLD);
            tsumoButton.setMessage(tsumoLabel);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int hovered = slotAt(mouseX, mouseY);
        if (hovered >= 0) {
            ItemStack hoveredStack = stackForSlot(hovered);
            if (!hoveredStack.isEmpty()) {
                graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF2A2A2A);

        int slotCount = menu.getSlotCount();
        for (int i = 0; i < slotCount; i++) {
            int x = leftPos + TILE_ROW_X + i * TILE_STEP_X;
            int y = topPos + TILE_ROW_Y;
            renderTileStack(graphics, stackForSlot(i), x, y);
            if (slotAt(mouseX, mouseY) == i) {
                graphics.fill(x, y, x + TILE_CLICK_W, y + TILE_CLICK_H, 0x33FFFFFF);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(
                font,
                Component.translatable("riichi_mahjong.screen.yaku_generator.title_tier", menu.getTierIndex()),
                titleLabelX,
                titleLabelY,
                0xFFE0E0E0,
                false);

        int infoY = 74;
        graphics.text(
                font,
                Component.translatable("riichi_mahjong.screen.yaku_generator.energy", menu.getEnergyStored(), YakuGeneratorBlockEntity.MAX_ENERGY),
                10,
                infoY,
                0xFFCFCFCF,
                false);
        infoY += 11;

        if (menu.getGenerationTicksRemaining() > 0) {
            graphics.text(
                    font,
                    Component.translatable(
                            "riichi_mahjong.screen.yaku_generator.active",
                            menu.getCurrentRfPerTick(),
                            menu.getGenerationTicksRemaining() / 20),
                    10,
                    infoY,
                    0xFF80FF80,
                    false);
        } else {
            graphics.text(
                    font,
                    Component.translatable("riichi_mahjong.screen.yaku_generator.idle"),
                    10,
                    infoY,
                    0xFFCFCFCF,
                    false);
        }
        infoY += 11;
        graphics.text(
                font,
                Component.translatable(
                        "riichi_mahjong.screen.yaku_generator.draws",
                        menu.getDrawsRemaining(),
                        menu.getDrawLimit()),
                10,
                infoY,
                0xFFCFCFCF,
                false);
        infoY += 11;

        if (menu.getLastHan() > 0) {
            Component rank = menu.isLastYakuman()
                    ? Component.translatable("riichi_mahjong.screen.yaku_generator.yakuman")
                    : Component.translatable("riichi_mahjong.screen.yaku_generator.han_value", menu.getLastHan());
            graphics.text(
                    font,
                    Component.translatable(
                            "riichi_mahjong.screen.yaku_generator.last_reward",
                            rank,
                            menu.getLastRfPerTick(),
                            menu.getLastDurationTicks() / 20),
                    10,
                    infoY,
                    0xFFFFE080,
                    false);
        } else {
            graphics.text(
                    font,
                    Component.translatable("riichi_mahjong.screen.yaku_generator.no_win"),
                    10,
                    infoY,
                    0xFFD08080,
                    false);
        }

        int rulesY = 140;
        graphics.text(
                font,
                Component.translatable("riichi_mahjong.screen.yaku_generator.rules_title"),
                10,
                rulesY,
                0xFFEAEAEA,
                false);
        rulesY += 11;
        graphics.text(
                font,
                Component.translatable("riichi_mahjong.screen.yaku_generator.rules_line1"),
                10,
                rulesY,
                0xFFBEBEBE,
                false);
        rulesY += 11;
        graphics.text(
                font,
                Component.translatable("riichi_mahjong.screen.yaku_generator.rules_line2"),
                10,
                rulesY,
                0xFFBEBEBE,
                false);
        rulesY += 11;
        int tier = menu.getTierIndex();
        String rulesLine3Key = "riichi_mahjong.screen.yaku_generator.rules_line3_t"
                + (tier >= 1 && tier <= 3 ? tier : 3);
        graphics.text(
                font,
                Component.translatable(rulesLine3Key),
                10,
                rulesY,
                0xFFBEBEBE,
                false);
        rulesY += 11;
        graphics.text(
                font,
                Component.translatable("riichi_mahjong.screen.yaku_generator.rewards_line"),
                10,
                rulesY,
                0xFFBEBEBE,
                false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int slot = slotAt(event.x(), event.y());
            if (slot >= 0) {
                sendMenuButton(YakuGeneratorMenu.BTN_SLOT_BASE + slot);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int slotAt(double mouseX, double mouseY) {
        int y = topPos + TILE_ROW_Y;
        if (mouseY < y || mouseY >= y + TILE_CLICK_H) {
            return -1;
        }
        int slotCount = menu.getSlotCount();
        int startX = leftPos + TILE_ROW_X;
        int endX = startX + TILE_STEP_X * (slotCount - 1) + TILE_CLICK_W;
        if (mouseX < startX || mouseX >= endX) {
            return -1;
        }
        int localX = (int) mouseX - startX;
        int slot = localX / TILE_STEP_X;
        if (slot < 0 || slot >= slotCount) {
            return -1;
        }
        int within = localX - slot * TILE_STEP_X;
        return within < TILE_CLICK_W ? slot : -1;
    }

    private ItemStack stackForSlot(int slot) {
        Item item = MahjongTileItems.itemForCode(menu.getTileCode(slot));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private void renderTileStack(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(TILE_SCALE_X, TILE_SCALE_Y);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }
}
