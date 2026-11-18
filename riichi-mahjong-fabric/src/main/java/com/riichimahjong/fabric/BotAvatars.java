package com.riichimahjong.fabric;

import com.riichimahjong.ai.BotDiscardAnimation;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.ai.RandomThinkingBot;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.TheMahjongDriver;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Public player silhouettes; only a chosen hand position leaves the server. */
final class BotAvatars {
    private static final long FOLLOW_THROUGH_TICKS = 6;
    private record RoundKey(Object wind, int hand, int honba, int dealer) {
        static RoundKey of(TheMahjongRound round) {
            return new RoundKey(round.roundWind(), round.handNumber(), round.honba(), round.dealerSeat());
        }
    }
    private static final class Seat {
        final BotAvatarElement avatar;
        int discards;
        Vec3 target;
        long followThroughUntil;
        Seat(BotAvatarElement avatar, int discards) { this.avatar = avatar; this.discards = discards; }
    }
    private final ElementHolder holder;
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final Map<Integer, Seat> seats = new HashMap<>();
    private TheMahjongDriver previousDriver;
    private RoundKey previousRound;

    BotAvatars(ElementHolder holder, ServerLevel level, BlockPos pos, BlockState state) {
        this.holder = holder; this.level = level; this.pos = pos; this.state = state;
    }

    void update(MahjongTableBlockEntity table) {
        var driver = table.driver();
        var round = driver == null ? null : driver.match().currentRound().orElse(null);
        var key = round == null ? null : RoundKey.of(round);
        boolean reset = driver != previousDriver || !java.util.Objects.equals(key, previousRound);
        var infos = table.seats();
        long tick = level.getGameTime();
        for (int seat = 0; seat < infos.size(); seat++) {
            var info = infos.get(seat);
            var controller = driver != null && seat < driver.match().playerCount() ? driver.playerAt(seat) : null;
            boolean visible = shouldShow(info, driver != null, controller);
            var current = seats.get(seat);
            if (!visible) {
                if (current != null) { current.avatar.destroy(); seats.remove(seat); }
                continue;
            }
            int discards = round == null ? 0 : round.players().get(seat).discards().size();
            var direction = PortViews.outward(seat, state);
            if (current == null) {
                var feet = seatPosition(pos, direction);
                current = new Seat(new BotAvatarElement(holder, level, feet, direction.getOpposite().toYRot()), discards);
                final int index = seat;
                current.avatar.setInteractionHandler(new VirtualElement.InteractionHandler() {
                    @Override public void interact(ServerPlayer player, InteractionHand hand, Vec3 location, boolean sneaking) {
                        if (hand != InteractionHand.MAIN_HAND || PortViews.routeWindPlateUse(player, table, sneaking)) return;
                        TableSeating.join(player, table, index);
                    }
                });
                seats.put(seat, current);
            }
            if (reset || discards < current.discards) {
                current.discards = discards; current.target = null; current.followThroughUntil = 0;
            }
            if (discards > current.discards) {
                // Count accepted engine discards, including called tiles still in the river history.
                // Opening an existing table or taking over a seat establishes a fresh baseline.
                current.avatar.swing();
                current.followThroughUntil = tick + FOLLOW_THROUGH_TICKS;
            }
            current.discards = discards;
            var animation = animation(controller);
            Optional<TheMahjongTile> chosen = round == null || animation == null ? Optional.empty()
                    : animation.tileFor(round, driver.currentPhase());
            if (chosen.isPresent()) {
                var order = table.handDisplayOrder(seat);
                var slots = table.handDisplaySlots(seat);
                int index = handIndex(order, chosen.orElseThrow(), table.hasSeparatedDrawnTile(seat));
                if (index >= 0 && index < slots.size()) {
                    current.target = TableLayout.handAnchor(pos, direction,
                            TableLayout.handAlong(slots.get(index), table.handDisplayGapStarts(seat)), false);
                }
            } else if (tick >= current.followThroughUntil) current.target = null;
            current.avatar.lookAt(current.target == null ? idleTarget(pos, direction) : current.target);
        }
        previousDriver = driver; previousRound = key;
    }

    static boolean shouldShow(MahjongTableBlockEntity.SeatInfo info, boolean active, MahjongPlayerInterface controller) {
        return active && info.enabled() && animation(controller) != null;
    }

    private static BotDiscardAnimation animation(MahjongPlayerInterface controller) {
        return switch (controller) {
            case KobalabBot bot -> bot.discardAnimation();
            case RandomThinkingBot bot -> bot.discardAnimation();
            case null, default -> null;
        };
    }

    static Vec3 seatPosition(BlockPos pos, Direction outward) {
        return PortViews.anchor(pos, outward, 0, -.4, TableLayout.TABLE_WIDTH / 2 + .35);
    }

    static Vec3 idleTarget(BlockPos pos, Direction outward) {
        return PortViews.anchor(pos, outward, 0, TableLayout.TABLE_TOP + .1, .5);
    }

    static int handIndex(List<TheMahjongTile> order, TheMahjongTile tile, boolean separatedDraw) {
        // Match HandDisplayState's removal policy when identical tiles are held.
        if (separatedDraw && !order.isEmpty() && order.getLast().equals(tile)) return order.size() - 1;
        return order.indexOf(tile);
    }

    void destroy() {
        seats.values().forEach(seat -> seat.avatar.destroy()); seats.clear();
        previousDriver = null; previousRound = null;
    }
}
