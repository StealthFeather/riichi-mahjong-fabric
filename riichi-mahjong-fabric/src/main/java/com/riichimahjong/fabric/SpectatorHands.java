package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.themahjong.driver.MatchPhase;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** A private, read-only view: concealed identities are sent to exactly one opted-in spectator. */
final class SpectatorHands {
    record Hand(int seat, List<Integer> tiles, List<Integer> slots, List<Integer> gaps) {}
    record Snapshot(Object driver, List<Hand> hands, Set<Integer> glowingTiles, boolean sichuan) {}

    final ServerPlayer owner;
    final Snapshot snapshot;
    private final ElementHolder holder = new ElementHolder();

    static boolean eligible(MahjongTableBlockEntity table, ServerPlayer player) {
        return !table.isRemoved() && table.replay() == null && table.driver() != null
                && table.driver().match().currentRound().isPresent()
                && SpectatorSeats.canSeeHands(table.isWatching(player.getUUID()), table.spectatorHandsVisible(),
                        !table.spectatorCanSeeHands(player.getUUID()),
                        table.getLevel() == player.level() && PortViews.viewingNear(player, player.level(), table.getBlockPos()));
    }

    static Snapshot capture(MahjongTableBlockEntity table, PublicTable board) {
        var driver = table.driver();
        var round = driver.currentRound();
        double fraction = 1;
        if (driver.currentPhase() instanceof MatchPhase.Dealing dealing) fraction = switch (dealing.stage()) {
            case WALL_BUILDING -> 0;
            case DEALING_HANDS -> Math.min(1, dealing.elapsed() / dealing.stage().buildDuration());
            case DORA_FLIP -> 1;
        };
        var hands = new ArrayList<Hand>();
        for (int seat = 0; seat < round.players().size(); seat++) {
            // Winning/revealed hands already use the shared reveal/animation renderer.
            if (!board.seats().get(seat).revealed().isEmpty()) continue;
            var order = table.handDisplayOrder(seat);
            int count = Math.min(order.size(), (int)Math.round(order.size() * fraction));
            hands.add(new Hand(seat, order.stream().limit(count).map(PublicTable::code).toList(),
                    table.handDisplaySlots(seat).stream().limit(count).toList(), table.handDisplayGapStarts(seat)));
        }
        return new Snapshot(driver, List.copyOf(hands), board.glowingTiles(), board.sichuan());
    }

    SpectatorHands(MahjongTableBlockEntity table, ServerPlayer owner, Snapshot snapshot) {
        if (!eligible(table, owner)) throw new IllegalArgumentException("spectator hand audience expired");
        this.owner = owner; this.snapshot = snapshot;
        new ManualAttachment(holder, owner.level(), () -> Vec3.atCenterOf(table.getBlockPos()));
        for (var hand : snapshot.hands()) {
            var direction = PortViews.outward(hand.seat(), table.getBlockState());
            for (int index = 0; index < hand.tiles().size(); index++) {
                int code = hand.tiles().get(index);
                var position = tilePosition(table.getBlockPos(), direction, hand.slots().get(index), hand.gaps());
                var display = PortViews.display(holder, PortViews.tileModel(code, snapshot.sichuan()), position,
                        TableLayout.TILE_SCALE, PortViews.rotation(direction, true, false),
                        snapshot.glowingTiles().contains(code));
                display.setVisibilityPredicate(player -> player == owner);
            }
        }
        holder.startWatching(owner);
    }

    static Vec3 tilePosition(BlockPos pos, Direction direction, int slot, List<Integer> gaps) {
        return TableLayout.handAnchor(pos, direction, TableLayout.handAlong(slot, gaps), true);
    }

    void tick() { holder.tick(); }
    void destroy() { holder.destroy(); }
}
