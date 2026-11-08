package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

public final class HumanControlsRegression {
    public static void main(String[] args){
        var first=new ArrayList<>(waiting(0));first.add(tile(4,2));
        var d=driver(List.of(player(first),player(waiting(1)),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2)));
        for(int seat=0;seat<3;seat++){
            if(seat>0)act(d,seat,PlayerAction.Draw.INSTANCE);
            var buttons=MahjongTableHumanPlayer.tableButtons(d,seat,false);
            check(buttons.stream().anyMatch(b->b instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle r&&!r.active()),"another player's riichi suppressed this player's button");
            var selected=MahjongTableHumanPlayer.tableButtons(d,seat,true);
            check(selected.stream().anyMatch(b->b instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle r&&r.active()),"independent riichi selection missing");
            act(d,seat,new PlayerAction.DiscardWithRiichi(tile(4,2)));passClaims(d);
            check(MahjongTableHumanPlayer.tableButtons(d,seat,true).stream().noneMatch(b->b instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle),"expired riichi selection still creates a button");
        }
        System.out.println("PASS: independent human riichi buttons and expired selection removal");
    }
}
