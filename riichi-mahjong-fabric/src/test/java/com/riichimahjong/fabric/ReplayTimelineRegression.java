package com.riichimahjong.fabric;

import com.riichimahjong.replay.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Turn seeking and exact physical locations, including calls, kan and red-five identity. */
public final class ReplayTimelineRegression {
    private static final UUID VIEWER=UUID.fromString("02151f19-91f8-4d24-b5b1-251c9d5d56d3");
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static CompoundTag tile(String code){
        var tag=new CompoundTag();int rank=code.charAt(1)-'0';
        String suit=switch(code.charAt(0)){case 'm'->"MANZU";case 'p'->"PINZU";case 's'->"SOUZU";default->rank<=4?"WIND":"DRAGON";};
        tag.putString("suit",suit);tag.putInt("rank",rank==0?5:code.charAt(0)=='z'&&rank>4?rank-4:rank);tag.putBoolean("redDora",rank==0);return tag;
    }
    private static ListTag tiles(String... codes){var list=new ListTag();for(String code:codes)list.add(tile(code));return list;}
    private static CompoundTag meld(String type,int source,int river,int claimed,String... codes){
        var tag=new CompoundTag();tag.putString("type",type);tag.put("tiles",tiles(codes));
        if(source>=0){tag.putInt("sourceSeat",source);tag.putInt("sourceDiscardIndex",river);tag.putInt("claimedTileIndex",claimed);}return tag;
    }
    private static CompoundTag kakan(CompoundTag pon,String added){var tag=new CompoundTag();tag.putString("type","KAKAN");tag.put("upgradedFrom",pon.copy());tag.put("addedTile",tile(added));return tag;}
    private static final class State {
        final CompoundTag[] players=new CompoundTag[4];
        final List<ReplayRecord.Frame> frames=new ArrayList<>();
        CompoundTag active=new CompoundTag();int turn;
        State(){for(int seat=0;seat<4;seat++){players[seat]=new CompoundTag();hand(seat,"z1");players[seat].put("discards",new ListTag());players[seat].put("melds",new ListTag());}active.putString("type","NONE");}
        void hand(int seat,String...codes){players[seat].put("currentHand",tiles(codes));}
        void discard(int seat,String code,boolean riichi){var tag=new CompoundTag();tag.put("tile",tile(code));tag.putBoolean("riichiDeclared",riichi);players[seat].getListOrEmpty("discards").add(tag);}
        void melds(int seat,CompoundTag...melds){var list=new ListTag();for(var meld:melds)list.add(meld.copy());players[seat].put("melds",list);}
        void active(String type,String code){active=new CompoundTag();active.putString("type",type);if(code!=null)active.put("tile",tile(code));}
        void frame(int round,String phase,int seat){
            if(seat>=0)turn=seat;
            var people=new ListTag();for(var player:players)people.add(player.copy());
            var current=new CompoundTag();current.put("players",people);current.put("activeTile",active.copy());current.putInt("currentTurnSeat",turn);
            var match=new CompoundTag();match.putInt("playerCount",4);match.put("currentRound",current);
            var action=new CompoundTag();action.putString("type",phase);if(seat>=0)action.putInt("seat",seat);
            var driver=new CompoundTag();driver.put("match",match);driver.put("phase",action);
            frames.add(new ReplayRecord.Frame(round,"动作 "+frames.size(),driver));
        }
        ReplayRecord record(){return new ReplayRecord(UUID.randomUUID(),0,1,"MAHJONG_SOUL_4P",List.of(new ReplayRecord.Participant(0,VIEWER,"测试玩家",25000)),frames);}
    }
    private static ReplayRecord fixture(){
        var s=new State();
        s.hand(0,"m1","m5");s.frame(0,"AWAITING_DRAW",0); // 0
        s.hand(0,"m1","m5","m0");s.active("DRAWN","m0");s.frame(0,"AWAITING_DISCARD",0); // 1
        s.hand(0,"m1","m5");s.discard(0,"m0",false);s.active("HELD_DISCARD","m0");s.frame(0,"AWAITING_CLAIMS",0); // 2
        var pon=meld("PON",0,0,2,"m5","m5","m0");
        var prior=meld("ANKAN",-1,-1,-1,"p1","p1","p1","p1");
        s.melds(1,prior,pon);s.hand(1,"p1","p2");s.active("NONE",null);s.frame(0,"AWAITING_DISCARD",1); // 3
        s.hand(1,"p2");s.discard(1,"p1",false);s.active("HELD_DISCARD","p1");s.frame(0,"AWAITING_CLAIMS",1); // 4
        s.frame(0,"AWAITING_DRAW",2); // 5
        s.melds(1,prior,kakan(pon,"m5"));s.hand(2,"s1","z1");s.active("DRAWN","s1");s.frame(0,"AWAITING_DISCARD",2); // 6
        s.hand(2,"z1");s.discard(2,"s1",false);s.active("HELD_DISCARD","s1");s.frame(0,"AWAITING_CLAIMS",2); // 7
        s.active("NONE",null);s.frame(0,"AWAITING_DRAW",0); // 8
        s.hand(0,"m1","m5","m1");s.active("DRAWN","m1");s.frame(0,"AWAITING_DISCARD",0); // 9
        s.hand(0,"m1","m1");s.discard(0,"m5",true);s.active("HELD_DISCARD","m5");s.frame(0,"AWAITING_CLAIMS",0); // 10
        s.melds(2,meld("CHI",0,1,1,"m4","m5","m6"));s.active("NONE",null);s.frame(0,"AWAITING_DRAW",0); // 11
        s.hand(0,"m1","m1","z4","m7","m7","m7","m7");s.active("DRAWN","z4");s.frame(0,"AWAITING_DISCARD",0); // 12: chooses kan, not a discard.
        s.hand(0,"m1","m1","z4");s.melds(0,meld("ANKAN",-1,-1,-1,"m7","m7","m7","m7"));s.active("NONE",null);s.frame(0,"AWAITING_DRAW",0); // 13
        s.hand(0,"m1","m1","z4","p9");s.active("DRAWN","p9");s.frame(0,"AWAITING_DISCARD",0); // 14: same turn, replacement draw.
        s.hand(0,"m1","m1","p9");s.discard(0,"z4",false);s.active("HELD_DISCARD","z4");s.frame(0,"AWAITING_CLAIMS",0); // 15
        s.active("NONE",null);s.frame(0,"AWAITING_DRAW",0); // 16
        s.hand(0,"m1","m1","p9","z5");s.active("DRAWN","z5");s.frame(0,"AWAITING_DISCARD",0); // 17: tsumo, no outgoing discard.
        s.frame(0,"ROUND_ENDED",-1); // 18
        for(var player:s.players){player.put("discards",new ListTag());player.put("melds",new ListTag());}
        s.active("NONE",null);s.frame(1,"AWAITING_DRAW",1); // 19
        s.hand(1,"p7","z1");s.active("DRAWN","p7");s.frame(1,"AWAITING_DISCARD",1); // 20
        s.hand(1,"z1");s.discard(1,"p7",false);s.active("HELD_DISCARD","p7");s.frame(1,"AWAITING_CLAIMS",1); // 21
        s.hand(0,"p1","p2");s.active("DRAWN","p2");s.frame(1,"AWAITING_DISCARD",0); // 22
        s.hand(0,"p1");s.discard(0,"p2",false);s.active("HELD_DISCARD","p2");s.frame(1,"AWAITING_CLAIMS",0); // 23
        s.frame(1,"MATCH_ENDED",-1); // 24
        return s.record();
    }
    private static void timeline(ReplayRecord record){
        var index=new ReplayTimeline(record);
        check(index.turnFrames(0).equals(List.of(1,9,12,17,22)),"Own turns must skip other players and combine kan replacement draws");
        check(index.turnFrames(1).equals(List.of(3,20)),"Claiming creates a turn without drawing");
        check(index.nextTurn(1,0)==9&&index.previousTurn(9,0)==1,"Adjacent own turn navigation");
        check(index.previousTurn(8,0)==1&&index.nextTurn(8,0)==9,"Navigation between own turns");
        check(index.nextTurn(12,0)==17&&index.previousTurn(14,0)==12,"Kan continuation is within one discard turn");
        check(index.nextTurn(17,0)==22&&index.previousTurn(22,0)==17,"Turn seek crosses a hand boundary safely");
        check(index.previousTurn(0,0)==0&&index.nextTurn(24,0)==24,"Seek must clamp both boundaries");
        check(index.nextTurn(1,3)==24&&index.previousTurn(24,3)==0,"No recorded turn still has safe boundaries");
        check(index.decisionFrame(8,0)==1&&index.decisionFrame(8,1)==3,"Selected perspective decides the associated original decision");
        check(index.decisionFrame(19,0)==-1&&!index.locationAt(19,0).present(),"Hand change must clear the previous hand's decision and marker");
        check(index.decisionFrame(-1,0)==-1&&index.decisionFrame(100,0)==-1,"Out of range frames are safe");
        var first=index.discardForDecision(1,0).orElseThrow();
        check(first.discardFrame()==2&&first.riverIndex()==0&&first.tile().equals("m0"),"Actual red-five discard is indexed from the correct decision");
        check(index.discardAt(1,0).isEmpty()&&index.discardAt(2,0).isPresent(),"Future outgoing choice and already played discard are separate");
        var before=index.locationAt(1,0);
        check(before.zone()==ReplayTimeline.Zone.DRAWN&&before.index()==2&&before.tile().equals("m0"),"Before play, highlight the separated actual red draw");
        var after=index.locationAt(2,0);
        check(after.zone()==ReplayTimeline.Zone.RIVER&&after.seat()==0&&after.index()==0,"After play, highlight its exact river index");
        var called=index.locationAt(3,0);
        check(called.zone()==ReplayTimeline.Zone.MELD&&called.seat()==1&&called.index()==1&&called.meldTileIndex()==2,"Claimed tile must move to the exact caller meld and claimed index");
        var upgraded=index.locationAt(6,0);
        check(upgraded.zone()==ReplayTimeline.Zone.MELD&&upgraded.seat()==1&&upgraded.index()==1&&upgraded.meldTileIndex()==2,"Added kan keeps the original called-tile identity");
        var hand=index.locationAt(9,0);
        check(hand.zone()==ReplayTimeline.Zone.HAND&&hand.index()==1&&hand.tile().equals("m5"),"Non-drawn black five must not mark the old red five or unrelated draw");
        check(index.discardForDecision(9,0).orElseThrow().riichi(),"Riichi declaration belongs to the indexed outgoing discard");
        var second=index.locationAt(10,0);
        check(second.zone()==ReplayTimeline.Zone.RIVER&&second.index()==1,"Next discard must not mark a previously called similar tile");
        var chi=index.locationAt(11,0);
        check(chi.zone()==ReplayTimeline.Zone.MELD&&chi.seat()==2&&chi.index()==0&&chi.meldTileIndex()==1,"Chi follows source river identity, not first same-kind meld");
        check(index.discardForDecision(12,0).isEmpty()&&!index.locationAt(12,0).present()&&!index.locationAt(13,0).present(),"Kan decision must not borrow the later replacement-draw discard");
        check(index.discardForDecision(14,0).orElseThrow().tile().equals("z4")&&index.locationAt(14,0).zone()==ReplayTimeline.Zone.HAND,"Replacement-draw decision has its own exact outgoing choice");
        check(index.discardForDecision(17,0).isEmpty()&&!index.locationAt(18,0).present(),"Winning decision must not borrow a next-hand discard");
        check(index.discardForDecision(1,1).isEmpty(),"Other seat cannot consume an original decision");
    }
    private static void controls(ReplayRecord record){
        var session=new ReplaySession(record,VIEWER,false);
        session.control(ReplaySession.Control.NEXT_TURN);check(session.index()==1&&!session.playing(),"Next turn starts at viewer's first decision");
        session.control(ReplaySession.Control.NEXT_TURN);check(session.index()==9,"Next turn skips all other seats");
        session.control(ReplaySession.Control.NEXT_TURN);check(session.index()==12,"Next turn enters the start of a kan turn");
        session.control(ReplaySession.Control.NEXT);session.control(ReplaySession.Control.NEXT);
        session.control(ReplaySession.Control.NEXT_TURN);check(session.index()==17,"Turn seek skips replacement-draw continuation");
        session.control(ReplaySession.Control.PERSPECTIVE);session.control(ReplaySession.Control.NEXT_TURN);
        check(session.perspective()==1&&session.index()==20,"Changing perspective changes whose next turn is selected");
        session.control(ReplaySession.Control.PREVIOUS_TURN);check(session.index()==3,"Previous turn uses selected perspective across hands");
        session.control(ReplaySession.Control.PREVIOUS_TURN);check(session.index()==0,"Previous turn does not underflow");
        session.control(ReplaySession.Control.PLAY_PAUSE);check(session.playing(),"Fixture starts auto playback");
        session.control(ReplaySession.Control.NEXT_TURN);check(session.index()==3&&!session.playing(),"Turn seek pauses automatic playback");
    }
    private static void repeatedDecision(){
        var s=new State();s.hand(0,"m5","p1","m5");s.active("DRAWN","m5");s.frame(0,"AWAITING_DISCARD",0);
        s.frame(0,"AWAITING_DISCARD",0);s.hand(0,"p1","m5");s.discard(0,"m5",true);s.active("HELD_DISCARD","m5");s.frame(0,"AWAITING_CLAIMS",0);s.frame(0,"MATCH_ENDED",-1);
        var index=new ReplayTimeline(s.record());
        check(index.turnFrames(0).equals(List.of(0)),"Repeated riichi decision must not become an extra turn");
        check(index.discardForDecision(0,0).orElseThrow().discardFrame()==2&&index.discardForDecision(1,0).orElseThrow().discardFrame()==2,"Repeated unchanged snapshots retain the accepted outgoing choice");
        check(index.locationAt(0,0).zone()==ReplayTimeline.Zone.DRAWN&&index.locationAt(0,0).index()==2,"Duplicate tile kinds prefer the visible separated drawn copy");
    }
    private static void incompleteOrAmbiguousHistory(){
        var missing=new State();missing.hand(0,"m1");missing.active("DRAWN","m1");missing.frame(0,"AWAITING_DISCARD",0);
        missing.hand(0,"m1");missing.discard(0,"p1",false);missing.active("HELD_DISCARD","p1");missing.frame(0,"AWAITING_CLAIMS",0);missing.frame(0,"MATCH_ENDED",-1);
        check(new ReplayTimeline(missing.record()).discardForDecision(0,0).isEmpty(),"Missing history must not attribute a tile absent from the decision hand");
        var skipped=new State();skipped.hand(0,"m1","m2");skipped.active("DRAWN","m2");skipped.frame(0,"AWAITING_DISCARD",0);
        skipped.discard(0,"m1",false);skipped.discard(0,"m2",false);skipped.frame(0,"AWAITING_CLAIMS",0);skipped.frame(0,"MATCH_ENDED",-1);
        check(new ReplayTimeline(skipped.record()).discardForDecision(0,0).isEmpty(),"A skipped second turn must not be presented as an exact accepted action");
        var record=fixture();var frames=new ArrayList<>(record.frames());var broken=frames.get(11).driver().copy();
        var claimed=broken.getCompoundOrEmpty("match").getCompoundOrEmpty("currentRound").getListOrEmpty("players").getCompoundOrEmpty(2).getListOrEmpty("melds").getCompoundOrEmpty(0);
        claimed.put("tiles",tiles("m4","m0","m6"));
        frames.set(11,new ReplayRecord.Frame(0,"损坏的来源牌",broken));
        var inconsistent=new ReplayRecord(record.id(),record.startedAt(),record.endedAt(),record.preset(),record.participants(),frames);
        check(!new ReplayTimeline(inconsistent).locationAt(11,0).present(),"Invalid source-tile metadata must not mark a different red copy or a hidden river slot");
    }
    public static void main(String[] args){var record=fixture();timeline(record);controls(record);repeatedDecision();incompleteOrAmbiguousHistory();System.out.println("PASS: "+checks+" replay timeline checks, perspective turn seeking, exact future/river/called-meld discards and round boundaries");}
}
