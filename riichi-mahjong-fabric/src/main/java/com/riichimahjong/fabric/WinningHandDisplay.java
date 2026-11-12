package com.riichimahjong.fabric;

import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MatchPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Result presentation only: the rule library clears its active tile when a win ends the round. */
public record WinningHandDisplay(TheMahjongTile tile,boolean selfDraw) {
    public WinningHandDisplay { Objects.requireNonNull(tile); }

    public record Layout(List<TheMahjongTile> order,List<Integer> slots,List<Integer> gapStarts) {}

    public static WinningHandDisplay capture(TheMahjongRound.ActiveTile active,MatchPhase after) {
        if(!(after instanceof MatchPhase.RoundEnded ended)||ended.winResults().isEmpty())return null;
        return switch(active) {
            case TheMahjongRound.ActiveTile.Drawn drawn -> new WinningHandDisplay(drawn.tile(),true);
            case TheMahjongRound.ActiveTile.HeldDiscard held -> new WinningHandDisplay(held.tile(),false);
            case TheMahjongRound.ActiveTile.HeldKakan held -> new WinningHandDisplay(held.tile(),false);
            case TheMahjongRound.ActiveTile.HeldKita held -> new WinningHandDisplay(held.tile(),false);
            default -> null;
        };
    }

    public Optional<Layout> layout(TheMahjongRound round,MatchPhase phase,int seat) {
        if(!PublicTable.revealedSeats(phase,round).contains(seat)
                ||!(phase instanceof MatchPhase.RoundEnded ended)||ended.winResults().isEmpty())return Optional.empty();
        if(selfDraw&&seat!=round.currentTurnSeat())return Optional.empty();
        var order=new ArrayList<>(round.players().get(seat).currentHand());
        // Tsumo already belongs to the hand; ron/chankan is an extra display tile only.
        // Equality includes red-dora identity, so an ordinary five cannot replace the winning red five.
        if(selfDraw&&!order.remove(tile))return Optional.empty();
        order.sort(TheMahjongTile.DISPLAY_ORDER);
        order.add(tile);
        var slots=IntStream.range(0,order.size()).boxed().toList();
        return Optional.of(new Layout(List.copyOf(order),slots,order.size()>1?List.of(order.size()-1):List.of()));
    }

    public void write(ValueOutput out) {
        MatchNbt.writeTile(out.child("tile"),tile);
        out.putBoolean("selfDraw",selfDraw);
    }

    public static WinningHandDisplay read(ValueInput in) {
        return new WinningHandDisplay(MatchNbt.readTile(in.childOrEmpty("tile")),in.getBooleanOr("selfDraw",false));
    }
}
