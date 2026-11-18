package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.themahjong.*;
import com.themahjong.driver.MatchPhase;
import com.themahjong.compat.DrawSettlement;

/** The sole allowlist passed to the public tile renderer. No round, wall tiles, seed or legal actions. */
public record PublicTable(List<Seat> seats,int liveWall,int rinshanDrawn,List<Integer> dora,List<Integer> ura,
                          Set<Integer> glowingTiles,String label,String roundLabel,int turnSeat,int riichiSticks,MatchPhase.Stage stage,double progress,int totalTiles,boolean sichuan,int openingWallDraws) {
    public record Tile(int code,boolean sideways,boolean stacked,boolean faceDown){
        Tile(int code,boolean sideways){this(code,sideways,false,false);}
        Tile(int code,boolean sideways,boolean stacked){this(code,sideways,stacked,false);}
    }
    public record Meld(List<Tile> tiles){}
    public record Seat(UUID owner,String name,int wind,int points,int handCount,List<Integer> handSlots,List<Integer> handGapStarts,List<Integer> revealed,List<Tile> river,List<Meld> melds,int kita,boolean riichi,boolean riichiStick,boolean drawnTile){}
    static int code(TheMahjongTile tile){return com.themahjong.variants.Jokers.isJoker(tile)?37:MahjongTileItems.codeForTile(tile);}
    /** Concealed identities become public only after the round has ended. */
    static Set<Integer> revealedSeats(MatchPhase phase,TheMahjongRound round){
        if(round==null||!(phase instanceof MatchPhase.RoundEnded ended))return Set.of();
        Set<Integer> revealed=new HashSet<>();
        if(ended.winResults().isEmpty()){
            for(int seat=0;seat<round.players().size();seat++)
                if(DrawSettlement.inTenpai(round.players().get(seat)))revealed.add(seat);
        }else{
            for(var win:ended.winResults())for(int seat=0;seat<Math.min(round.players().size(),win.pointDeltas().size());seat++)
                if(win.pointDeltas().get(seat)>0)revealed.add(seat);
        }
        return Set.copyOf(revealed);
    }
    /** Merge explicit choices with the existing automatic result visibility. */
    public static Set<Integer> revealedSeats(MahjongTableBlockEntity table){
        var driver=table.driver();
        if(driver==null)return Set.of();
        var round=driver.match().currentRound().orElse(null);
        if(table.replay()!=null){
            var session=table.replay();var visible=new HashSet<Integer>();
            if(round!=null)for(int seat=0;seat<round.players().size();seat++)
                if(session.showAll()||seat==session.perspective())visible.add(seat);
            visible.addAll(revealedSeats(table.displayPhase(),round));
            return Set.copyOf(visible);
        }
        if (driver.sichuanState() != null) {
            var visible=new HashSet<>(table.publicWinningSeats());
            if(ResultHandReveals.canReveal(driver))visible.addAll(table.voluntarilyShownSeats());
            return Set.copyOf(visible);
        }
        if (driver.bloodBattleState()!=null) {
            var visible=new HashSet<>(table.publicWinningSeats());
            if(ResultHandReveals.canReveal(driver))visible.addAll(table.voluntarilyShownSeats());
            return Set.copyOf(visible);
        }
        return revealedSeats(driver.currentPhase(),round,table.voluntarilyShownSeats());
    }
    static Set<Integer> revealedSeats(MatchPhase phase,TheMahjongRound round,Set<Integer> choices){
        var visible=new HashSet<>(revealedSeats(phase,round));
        if(ResultHandReveals.canReveal(phase,round))for(int seat:choices)
            if(seat>=0&&seat<round.players().size())visible.add(seat);
        return Set.copyOf(visible);
    }
    static Set<Integer> claimed(List<TheMahjongPlayer> players,int seat){
        Set<Integer> out=new HashSet<>();
        for(var player:players)for(var meld:player.melds()){
            int source=-1,index=-1;
            switch(meld){
                case TheMahjongMeld.Chi m->{source=m.sourceSeat();index=m.sourceDiscardIndex();}
                case TheMahjongMeld.Pon m->{source=m.sourceSeat();index=m.sourceDiscardIndex();}
                case TheMahjongMeld.Daiminkan m->{source=m.sourceSeat();index=m.sourceDiscardIndex();}
                case TheMahjongMeld.Kakan m->{source=m.upgradedFrom().sourceSeat();index=m.upgradedFrom().sourceDiscardIndex();}
                default->{}
            }if(source==seat)out.add(index);
        }return out;
    }
    public static PublicTable capture(MahjongTableBlockEntity table){
        var driver=table.driver();var round=driver==null?null:driver.match().currentRound().orElse(null);
        var infos=table.seats();var out=new ArrayList<Seat>();
        MatchPhase phase=driver==null?null:table.displayPhase();
        var sichuan=driver==null?null:driver.sichuanState();
        Set<Integer> visibleHands=revealedSeats(table);
        boolean ura=driver!=null&&DoraGlint.uraVisible(round,phase,driver.rules());
        for(int i=0;i<infos.size();i++){
            var info=infos.get(i);UUID owner=info.occupant().orElse(null);var player=owner==null?null:table.getLevel().getPlayerByUUID(owner);
            String name=player==null?(owner==null?(round==null?"空位":"BOT"):"离线玩家"):player.getName().getString();
            if(table.replay()!=null){
                var participant=table.replay().record().participantAt(i);
                owner=null;name=participant==null?"空位":participant.name();
            }
            if(round==null||i>=round.players().size()){out.add(new Seat(owner,info.enabled()?name:"关闭",i,0,0,List.of(),List.of(),List.of(),List.of(),List.of(),0,false,false,false));continue;}
            if(sichuan!=null){
                if(sichuan.won(i))name+=" · 已胡";
                var missing=sichuan.voidSuit(i);
                if(missing!=null&&sichuan.stage()==com.themahjong.sichuan.SichuanState.Stage.PLAY)name+=" · 缺"+switch(missing){case MANZU->"万";case PINZU->"筒";case SOUZU->"索";default->"";};
            }
            if(driver.bloodBattleState()!=null&&driver.bloodBattleState().won(i))name+=" · 已胡";
            var p=round.players().get(i);var river=RiverDisplay.capture(round.players(),i);
            var melds=new ArrayList<Meld>();for(var meld:p.melds()){
                var tiles=new ArrayList<Tile>();for(var t:MeldLayout.arrange(meld,i,round.players().size()))tiles.add(new Tile(t.concealed()?-1:code(t.tile()),t.sideways(),t.stacked(),t.concealed()));
                melds.add(new Meld(List.copyOf(tiles)));
            }
            // The allowlist also includes every tenpai hand at a drawn-round result.
            List<Integer> revealed=visibleHands.contains(i)?table.handDisplayOrder(i).stream().map(PublicTable::code).toList():List.of();
            int count=visibleHands.contains(i)?revealed.size():p.currentHand().size(); // Ron adds a result-only tile.
            boolean deposited=p.riichi()&&driver.snapshot().pendingRiichiCommit().orElse(-1)!=i;
            out.add(new Seat(owner,name,Math.floorMod(i-round.dealerSeat(),round.players().size()),p.points(),count,table.handDisplaySlots(i),table.handDisplayGapStarts(i),revealed,List.copyOf(river),List.copyOf(melds),p.kitaCount(),p.riichi(),deposited,table.hasSeparatedDrawnTile(i)));
        }
        String label="";
        if(table.winEffectSession()!=null)label="胡牌动画播放中";
        else if(phase instanceof MatchPhase.RoundEnded ended){
            if(ended.winResults().isEmpty())label="流局";
            if(table.resultAnimStage()==MahjongTableBlockEntity.ResultAnimStage.AWAITING_ADVANCE)
                label+=(label.isEmpty()?"":" · ")+"右键中心风盘继续下一局";
        }
        var dora=round==null||sichuan!=null?List.<Integer>of():round.doraIndicators().stream().limit(round.revealedDoraCount()).map(PublicTable::code).toList();
        var uras=round!=null&&ura&&sichuan==null?round.uraDoraIndicators().stream().limit(round.revealedDoraCount()).map(PublicTable::code).toList():List.<Integer>of();
        var stage=phase instanceof MatchPhase.Dealing d?d.stage():null;double progress=phase instanceof MatchPhase.Dealing d?Math.min(1,d.elapsed()/d.stage().buildDuration()):1;
        String roundLabel=round==null?"立直麻将 · 右键桌边入座 · 桌主潜行右键中心风盘打开菜单，再点开始对局":TableSeating.wind(round.roundWind().ordinal())+round.handNumber()+"局 · "+round.honba()+"本场 · 供托 "+round.riichiSticks()+" · 余牌 "+round.liveWall().size();
        if(sichuan!=null) {
            roundLabel="川麻 · "+TableSeating.wind(round.roundWind().ordinal())+round.handNumber()+"局 · 余牌 "+round.liveWall().size()+" · 已胡 "+sichuan.winners().size()+"人";
            if(!(phase instanceof MatchPhase.Dealing))roundLabel+=switch(sichuan.stage()){case EXCHANGE->" · 换三张";case VOID_SUIT->" · 定缺";case PLAY->driver.match().ruleSet().bloodBattle()?" · 血战到底":" · 首次胡牌结束";};
        }
        if(driver!=null&&driver.rules().competitiveProfile()!=com.themahjong.competition.CompetitiveRules.Profile.STANDARD)
            roundLabel=(driver.rules().competitiveProfile()==com.themahjong.competition.CompetitiveRules.Profile.JPML_A?"公式战":"M-League")+" · "+roundLabel;
        if(driver!=null&&driver.bloodBattleState()!=null)roundLabel+=" · 血战到底 · 已胡 "+driver.bloodBattleState().winners().size()+"人";
        if(driver!=null&&driver.exchangeInProgress()&&!(phase instanceof MatchPhase.Dealing))roundLabel+=" · 换三张";
        if(phase instanceof MatchPhase.Dealing)roundLabel+=" · 配牌中";
        if(phase instanceof MatchPhase.MatchEnded)roundLabel+=" · 对局结束";
        if(table.replay()!=null){
            var session=table.replay();
            roundLabel="牌谱回放 · "+(session.index()+1)+"/"+session.record().frames().size()+" · "+roundLabel;
            label+=(label.isEmpty()?"":" · ")+session.frame().label()+" · "+(session.playing()?"播放":"暂停")+" · "+session.speed()+"倍速";
        }
        int turn=phase instanceof MatchPhase.AwaitingDiscard d?d.seat():phase instanceof MatchPhase.AwaitingDraw d?d.seat():-1;
        if(driver!=null&&driver.exchangeInProgress())turn=-1;
        if(sichuan!=null&&sichuan.stage()!=com.themahjong.sichuan.SichuanState.Stage.PLAY)turn=-1;
        return new PublicTable(List.copyOf(out),round==null?0:round.liveWall().size(),round==null||sichuan!=null?0:Math.max(0,4-round.rinshanTiles().size()),dora,uras,sichuan==null?DoraGlint.tiles(round,phase,driver==null?com.themahjong.TheMahjongRuleSet.wrc():driver.rules()):driver.match().ruleSet().jokersEnabled()?Set.of(33):Set.of(),label,roundLabel,turn,round==null?0:round.riichiSticks(),stage,progress,driver==null?136:driver.match().tileSet().copiesPerTile().entrySet().stream().filter(e->!com.themahjong.variants.Jokers.isJoker(e.getKey())).mapToInt(e->e.getValue()).sum(),sichuan!=null,driver==null?52:driver.match().playerCount()*(driver.match().ruleSet().jokersEnabled()?12:13));
    }
}
