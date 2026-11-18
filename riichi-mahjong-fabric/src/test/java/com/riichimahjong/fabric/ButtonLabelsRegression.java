package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.PlayerAction;
import java.util.*;

/** User-facing honor names must follow the rule library's rank mapping. */
public final class ButtonLabelsRegression {
    private static TheMahjongTile tile(TheMahjongTile.Suit suit,int rank,boolean red){return new TheMahjongTile(suit,rank,red);}
    private static String pon(TheMahjongTile tile){return PrivateHand.label(new TableButton.SubmitAction(new PlayerAction.Pon(List.of(tile,tile))));}
    private static void equal(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        equal(pon(tile(TheMahjongTile.Suit.DRAGON,1,false)),"碰 白 白");
        equal(pon(tile(TheMahjongTile.Suit.DRAGON,2,false)),"碰 发 发");
        equal(pon(tile(TheMahjongTile.Suit.DRAGON,3,false)),"碰 中 中");
        equal(pon(tile(TheMahjongTile.Suit.MANZU,5,true)),"碰 赤5万 赤5万");
        equal(pon(tile(TheMahjongTile.Suit.PINZU,2,false)),"碰 2筒 2筒");
        equal(pon(tile(TheMahjongTile.Suit.SOUZU,9,false)),"碰 9条 9条");
        equal(pon(tile(TheMahjongTile.Suit.WIND,1,false)),"碰 东 东");
        equal(pon(tile(TheMahjongTile.Suit.WIND,2,false)),"碰 南 南");
        equal(pon(tile(TheMahjongTile.Suit.WIND,3,false)),"碰 西 西");
        equal(pon(tile(TheMahjongTile.Suit.WIND,4,false)),"碰 北 北");
        var token=UUID.fromString("00000000-0000-0000-0000-000000000013");
        equal(PrivateHand.label(new TableButton.SkipTsumo()),"跳过");
        equal(PrivateHand.label(new TableButton.ConfirmSkip(token)),"确认跳过");
        equal(PrivateHand.label(new TableButton.CancelSkip(token)),"返回");
        System.out.println("PASS: honor labels display 东/南/西/北 and 白/发/中; number tiles and skip-confirmation labels stay correct");
    }
}
