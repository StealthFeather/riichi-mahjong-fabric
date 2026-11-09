package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.ReturningSeats;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.themahjong.driver.*;
import com.themahjong.driver.bots.StupidActiveBot;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

public final class SeatRejoinRegression {
    public static void main(String[] args){
        UUID first=UUID.randomUUID(),other=UUID.randomUUID();var reservations=new ReturningSeats();
        reservations.remember(first,0);
        check(reservations.allows(first,0),"returning player cannot retake original seat");
        check(!reservations.allows(first,1),"returning player can inspect another BOT hand");
        check(!reservations.allows(other,0),"another player can take the returning player's seat");
        check(reservations.allows(other,1),"new player cannot take an unreserved BOT seat");
        var restored=new ReturningSeats();reservations.snapshot().forEach(restored::remember);
        check(restored.seat(first).orElse(-1)==0&&!restored.allows(other,0),"saved reservation was lost");
        var hand=new ArrayList<>(waiting(0));hand.add(tile(4,2));
        var d=driver(List.of(player(hand),player(waiting(1)),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2)));
        var round=d.currentRound();var phase=d.currentPhase();var legal=d.legalActions(0);
        d.replacePlayer(0,StupidActiveBot.humanLike());
        var returning=new MahjongTableHumanPlayer();returning.setOccupant(first);returning.setAutoDrawAfterSeconds(0);
        d.replacePlayer(0,returning);
        check(d.currentRound()==round&&d.currentPhase()==phase,"rejoining changed the hand, points or turn");
        check(d.legalActions(0).equals(legal),"rejoining changed available actions");
        returning.queue(new PlayerAction.Discard(tile(4,2)));d.advance(.05);
        check(d.currentRound().players().get(0).discards().size()==1,"returning human cannot resume play");
        reservations.clear();check(reservations.allows(first,1)&&reservations.allows(other,0),"new match kept stale reservations");
        System.out.println("PASS: original-seat reservations, restored reservations and human control after BOT takeover");
    }
}
