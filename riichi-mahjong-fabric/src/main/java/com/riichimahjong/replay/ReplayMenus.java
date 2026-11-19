package com.riichimahjong.replay;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Archive queries use the viewer's UUID from both record items and the table history menu. */
public final class ReplayMenus {
    private static final String[] WINDS = {"东", "南", "西", "北"};
    private ReplayMenus() {}

    public static void overview(ServerPlayer player) {
        open(player, "我的麻将概况", () -> true, menu -> {
            ItemStack head = icon(Items.PLAYER_HEAD, player.getGameProfile().name());
            head.set(DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile()));
            menu.button(13, head, () -> {});
            menu.button(22, icon(tile(33), "我的牌谱", "查看你参与并完整结束的对局"), () -> calendar(player, YearMonth.now(ReplayStore.zone())));
            menu.button(49, icon(Items.BARRIER, "关闭"), player::closeContainer);
        });
    }

    public static void controls(ServerPlayer player, MahjongTableBlockEntity table) {
        ReplaySession session = table.replay();
        if (session == null || !valid(player, table) || table.owner().filter(player.getUUID()::equals).isEmpty()) return;
        int wind=Math.floorMod(session.perspective()-table.driver().currentRound().dealerSeat(),table.driver().match().playerCount());
        open(player, "牌谱回放控制", () -> valid(player, table) && table.replay() == session
                && table.owner().filter(player.getUUID()::equals).isPresent(), menu -> {
            menu.button(4, icon(Items.PAPER, session.frame().label(), "进度 " + (session.index() + 1) + " / " + session.record().frames().size(), "点击刷新当前状态"), () -> controls(player, table));
            control(menu, player, table, 10, Items.ARROW, "回到开始", ReplaySession.Control.FIRST);
            control(menu, player, table, 11, Items.ARROW, "上一局", ReplaySession.Control.PREVIOUS_ROUND);
            control(menu, player, table, 12, Items.ARROW, "上一步", ReplaySession.Control.PREVIOUS);
            control(menu, player, table, 13, Items.CLOCK, session.playing() ? "暂停" : "播放", ReplaySession.Control.PLAY_PAUSE);
            control(menu, player, table, 14, Items.ARROW, "下一步", ReplaySession.Control.NEXT);
            control(menu, player, table, 15, Items.ARROW, "下一局", ReplaySession.Control.NEXT_ROUND);
            control(menu, player, table, 16, Items.ARROW, "跳到结束", ReplaySession.Control.LAST);
            control(menu, player, table, 20, Items.ARROW, "上一巡（当前视角）", ReplaySession.Control.PREVIOUS_TURN);
            control(menu, player, table, 24, Items.ARROW, "下一巡（当前视角）", ReplaySession.Control.NEXT_TURN);
            control(menu, player, table, 28, Items.CLOCK, "播放速度：" + session.speed() + "倍", ReplaySession.Control.SPEED);
            control(menu, player, table, 30, tile(27 + wind), "切换视角：" + WINDS[wind], ReplaySession.Control.PERSPECTIVE);
            control(menu, player, table, 32, Items.ENDER_EYE, "全手牌：" + (session.showAll() ? "显示" : "隐藏"), ReplaySession.Control.REVEAL);
            control(menu, player, table, 34, tile(32), "AI分析：" + (session.analysisEnabled() ? "开启" : "关闭"), ReplaySession.Control.ANALYSIS);
            menu.button(45, icon(Items.BARRIER, "退出回放并恢复牌桌"), () -> { table.controlReplay(player, ReplaySession.Control.EXIT); player.closeContainer(); });
            menu.button(53, icon(Items.BARRIER, "关闭控制面板"), player::closeContainer);
        });
    }
    private static void control(Actions menu, ServerPlayer player, MahjongTableBlockEntity table, int slot, Item item, String label, ReplaySession.Control action) {
        menu.button(slot, icon(item, label), () -> { table.controlReplay(player, action); controls(player, table); });
    }

    public static void calendar(ServerPlayer player, YearMonth requestedMonth) {
        calendar(player, requestedMonth, null);
    }
    public static void calendar(ServerPlayer player, Runnable history) {
        calendar(player, YearMonth.now(ReplayStore.zone()), history);
    }
    private static void calendar(ServerPlayer player, YearMonth requestedMonth, Runnable history) {
        YearMonth today = YearMonth.now(ReplayStore.zone());
        YearMonth month = requestedMonth.isAfter(today) ? today : requestedMonth;
        request(player, "我的牌谱 · " + month, ReplayStore.calendar(player.getUUID(), month), history, counts ->
            open(player, "我的牌谱 · " + month, () -> true, menu -> {
                for (int slot = 0; slot < 6; slot++) menu.button(slot, icon(Items.PAPER, month + " · 牌谱日历", "红中：当天有牌谱", "白板：当天没有牌谱"), () -> {});
                menu.button(6, icon(Items.ARROW, "上个月"), () -> calendar(player, month.minusMonths(1), history));
                menu.button(7, icon(Items.CLOCK, "本月"), () -> calendar(player, YearMonth.now(ReplayStore.zone()), history));
                if (month.isBefore(today)) menu.button(8, icon(Items.ARROW, "下个月"), () -> calendar(player, month.plusMonths(1), history));
                for (int day = 1; day <= month.lengthOfMonth(); day++) {
                    LocalDate date = month.atDay(day);
                    int count = counts.getOrDefault(date, 0);
                    menu.button(9 + day - 1, icon(tile(count > 0 ? 33 : 31), date.toString(), count + " 场完整对局"),
                            () -> { if (count > 0) day(player, date, 0, history); });
                }
                menu.button(45, icon(Items.ARROW, history == null ? "返回我的概况" : "返回历史战绩"), history == null ? () -> overview(player) : history);
                menu.button(49, icon(Items.PAPER, "日期按 " + ReplayStore.zone().getId(), "以对局结束时的自然日期归档"), () -> {});
                menu.button(53, icon(Items.BARRIER, "关闭"), player::closeContainer);
            }));
    }

    private record DayOverview(ReplayStore.DayPage page, Map<UUID, Map<Integer, Integer>> scores) {}
    public static void day(ServerPlayer player, LocalDate date, int page) { day(player, date, page, null); }

    private static void day(ServerPlayer player, LocalDate date, int page, Runnable history) {
        request(player, "我的牌谱 · " + date, ReplayStore.day(player.getUUID(), date, page).thenCompose(result -> {
            Map<UUID, Map<Integer, Integer>> scores = new java.util.concurrent.ConcurrentHashMap<>();
            var caches = result.matches().stream().map(match -> ReplayStore.loadAnalysis(player.getUUID(), match.id()).thenAccept(json -> {
                // Browsing a list must not import full frames into the active analysis LRU.
                try {
                    if (json != null && json.has("complete") && json.get("complete").getAsBoolean())
                        scores.put(match.id(), ReplayAnalysis.completedMatchScores(match.id(), json));
                } catch (RuntimeException ignored) { /* Recompute older/invalid summaries when enabled. */ }
            }).exceptionally(ignored -> null)).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(caches).thenApply(ignored -> new DayOverview(result, new HashMap<>(scores)));
        }), history, result -> dayPage(player, date, page, result.page(), new HashMap<>(), result.scores(), new HashSet<>(), history));
    }
    private static void dayPage(ServerPlayer player, LocalDate date, int page, ReplayStore.DayPage result,
                                Map<UUID, Boolean> analysis, Map<UUID, Map<Integer, Integer>> scores, Set<UUID> failures, Runnable history) {
        open(player, date + " · 第" + (page + 1) + "页", () -> true, menu -> {
            for (int row = 0; row < result.matches().size(); row++) {
                ReplayStore.Summary match = result.matches().get(row);
                int base = row * 9;
                String time = Instant.ofEpochMilli(match.endedAt()).atZone(ReplayStore.zone()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                menu.button(base, icon(Items.BOOK, "载入 " + time, match.preset(), "需要8格内归你所有且无其他真人的空闲牌桌"),
                        () -> load(player, match.id(), analysis.getOrDefault(match.id(), false), history));
                participantScores(menu, match, base, scores, failures);
                boolean enabled = analysis.getOrDefault(match.id(), false);
                ItemStack toggle = icon(tile(32), "AI分析：" + (enabled ? "开启" : "关闭"), "点击切换，再点击本行载入", "使用高级AI评估各次弃牌", "整场一致性评分显示在各玩家物品说明中");
                toggle.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, enabled);
                menu.button(base + 5, toggle, () -> {
                    analysis.put(match.id(), !enabled);
                    failures.remove(match.id());
                    if (!enabled && !scores.containsKey(match.id()) && !ReplayAnalysis.analysisComplete(match.id())) {
                        var server = player.level().getServer();
                        ReplayStore.load(player.getUUID(), match.id()).whenComplete((record, error) -> {
                            if (error == null) ReplayAnalysis.analyzeAll(record, ignored -> {});
                            else server.executeIfPossible(() -> failures.add(match.id()));
                        });
                    }
                    dayPage(player, date, page, result, analysis, scores, failures, history);
                });
            }
            menu.refresh = () -> {
                for (int row = 0; row < result.matches().size(); row++) participantScores(menu, result.matches().get(row), row * 9, scores, failures);
            };
            if (result.matches().isEmpty()) menu.button(22, icon(tile(31), "当天没有你的完整牌谱"), () -> {});
            menu.button(45, icon(Items.ARROW, "返回日历"), () -> calendar(player, YearMonth.from(date), history));
            if (page > 0) menu.button(48, icon(Items.ARROW, "上一页"), () -> day(player, date, page - 1, history));
            menu.button(49, icon(Items.PAPER, "按结束时间从新到旧排列"), () -> {});
            if (result.hasNext()) menu.button(50, icon(Items.ARROW, "下一页"), () -> day(player, date, page + 1, history));
            menu.button(53, icon(Items.BARRIER, "关闭"), player::closeContainer);
        });
    }

    private static void participantScores(Actions menu, ReplayStore.Summary match, int base,
                                          Map<UUID, Map<Integer, Integer>> savedScores, Set<UUID> failures) {
        if (ReplayAnalysis.analysisComplete(match.id())) savedScores.put(match.id(), ReplayAnalysis.matchScores(match.id()));
        boolean complete = savedScores.containsKey(match.id());
        Map<Integer, Integer> scores = savedScores.getOrDefault(match.id(), Map.of());
        for (int seat = 0; seat < 4; seat++) {
            ReplayRecord.Participant participant = match.participantAt(seat);
            if (participant == null) menu.button(base + 1 + seat, icon(tile(27 + seat), "—", WINDS[seat] + "位未启用"), () -> {});
            else menu.button(base + 1 + seat, icon(tile(27 + seat), Integer.toString(participant.points()), participant.name(), WINDS[seat] + "位",
                    failures.contains(match.id()) ? "整场一致性评分：分析暂不可用，请重新启用" : matchScoreLabel(scores, seat, complete)), () -> {});
        }
    }
    private static String matchScoreLabel(Map<Integer, Integer> scores, int seat, boolean complete) {
        if (!complete) return "整场一致性评分：待分析完成（点击发财启用分析）";
        Integer score = scores.get(seat);
        return score == null ? "整场一致性评分：—（没有可评分的出牌）" : "整场一致性评分：" + score;
    }

    private static void load(ServerPlayer player, UUID id, boolean analysis, Runnable history) {
        MahjongTableBlockEntity candidate = nearestAvailable(player);
        if (candidate == null) {
            player.sendSystemMessage(Component.literal("请准备8格内归你所有、没有正在进行的对局且无其他真人在座的牌桌。"), true);
            return;
        }
        long revision = candidate.matchRevision();
        request(player, "载入牌谱", ReplayStore.load(player.getUUID(), id), history, record -> {
            if (!available(player, candidate) || candidate.matchRevision() != revision) {
                player.sendSystemMessage(Component.literal("牌桌状态已变化，请重新选择牌谱。"), true);
                if (history == null) overview(player); else history.run();
                return;
            }
            if (candidate.startReplay(record, player, analysis)) {
                player.closeContainer();
                player.sendSystemMessage(Component.literal("已载入牌谱，请点击桌沿按钮控制回放。"), true);
            } else player.sendSystemMessage(Component.literal("无法载入牌谱，牌桌状态已变化。"), true);
        });
    }
    private static MahjongTableBlockEntity nearestAvailable(ServerPlayer player) {
        MahjongTableBlockEntity best = null; double distance = 65;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
            if (!player.level().hasChunkAt(pos)) continue;
            if (player.level().getBlockEntity(pos) instanceof MahjongTableBlockEntity table && available(player, table)) {
                double candidate = pos.distToCenterSqr(player.position());
                if (candidate < distance) { distance = candidate; best = table; }
            }
        }
        return best;
    }
    private static boolean available(ServerPlayer player, MahjongTableBlockEntity table) {
        return valid(player, table) && table.owner().filter(player.getUUID()::equals).isPresent()
                && !table.hasActiveMatch() && table.seats().stream().noneMatch(seat -> seat.occupant().filter(uuid -> !uuid.equals(player.getUUID())).isPresent());
    }
    private static boolean valid(ServerPlayer player, MahjongTableBlockEntity table) {
        return !player.isSpectator() && table.getLevel() == player.level() && !table.isRemoved()
                && table.getBlockPos().distToCenterSqr(player.position()) <= 64 && player.level().hasChunkAt(table.getBlockPos())
                && player.level().getBlockEntity(table.getBlockPos()) == table;
    }
    private static Item tile(int code) { Item item = MahjongTileItems.itemForCode(code); return item == null ? Items.PAPER : item; }
    private static ItemStack icon(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (lore.length > 0) stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(Component::literal).map(component -> (Component)component).toList()));
        return stack;
    }
    private static Actions open(ServerPlayer player, String title, BooleanSupplier valid, Consumer<Actions> setup) {
        Actions[] holder = new Actions[1];
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
            Actions menu = new Actions(id, inventory, valid);
            holder[0] = menu; setup.accept(menu); return menu;
        }, Component.literal(title)));
        return holder[0];
    }
    private static <T> void request(ServerPlayer player, String title, CompletableFuture<T> request, Runnable history, Consumer<T> success) {
        Actions waiting = open(player, title, () -> true, menu -> {
            menu.button(22, icon(Items.CLOCK, "正在读取牌谱…"), () -> {});
            menu.button(49, icon(history == null ? Items.BARRIER : Items.ARROW, history == null ? "取消" : "返回历史战绩"),
                    history == null ? player::closeContainer : history);
        });
        var server = player.level().getServer();
        request.whenComplete((value, failure) -> server.executeIfPossible(() -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) != player || player.containerMenu != waiting) return;
            if (failure == null) success.accept(value);
            else open(player, title, () -> true, menu -> {
                menu.button(22, icon(Items.BARRIER, "牌谱服务暂不可用", "请稍后再试"), () -> {});
                menu.button(49, icon(history == null ? Items.BARRIER : Items.ARROW, history == null ? "关闭" : "返回历史战绩"),
                        history == null ? player::closeContainer : history);
            });
        }));
    }
    private static final class Actions extends ChestMenu {
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private final BooleanSupplier valid;
        private Runnable refresh = () -> {};
        private int refreshTicks;
        Actions(int id, Inventory inventory, BooleanSupplier valid) {
            super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(54), 6); this.valid = valid;
        }
        void button(int slot, ItemStack item, Runnable action) { getContainer().setItem(slot, item); actions.put(slot, action); }
        @Override public void clicked(int slot, int button, ContainerInput type, Player player) {
            if (player.containerMenu == this && valid.getAsBoolean() && type == ContainerInput.PICKUP && actions.containsKey(slot)) actions.get(slot).run();
        }
        @Override public void broadcastChanges() {
            if (++refreshTicks >= 20) { refreshTicks = 0; refresh.run(); }
            super.broadcastChanges();
        }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return valid.getAsBoolean(); }
    }
}
