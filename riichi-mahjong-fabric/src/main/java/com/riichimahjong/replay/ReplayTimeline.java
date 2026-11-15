package com.riichimahjong.replay;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Indexes recorded decisions and the exact river identity of their accepted discard. */
public final class ReplayTimeline {
    public enum Zone { NONE, HAND, DRAWN, RIVER, MELD }
    /** River index never changes when a discard is called; melds retain this source identity. */
    public record Discard(int decisionFrame,int discardFrame,int roundIndex,int seat,String tile,int riverIndex,boolean riichi) {}
    /** HAND/DRAWN index is in currentHand; RIVER is in discards; MELD is its owner's meld ordinal. */
    public record Location(Zone zone,int seat,int index,int meldTileIndex,String tile) {
        public static Location none(){return new Location(Zone.NONE,-1,-1,-1,"");}
        public boolean present(){return zone!=Zone.NONE;}
    }

    private final ReplayRecord record;
    private final CompoundTag[] rounds;
    private final int[][] decisions;
    private final List<List<Integer>> turns;
    private final Map<Integer,Discard> discards=new HashMap<>();

    public ReplayTimeline(ReplayRecord record) {
        this.record=Objects.requireNonNull(record);
        int size=record.frames().size();rounds=new CompoundTag[size];decisions=new int[4][size];
        var anchors=new ArrayList<List<Integer>>();for(int seat=0;seat<4;seat++)anchors.add(new ArrayList<>());
        int[] lastDecision={-1,-1,-1,-1},turnRound={-1,-1,-1,-1},turnRiver={-1,-1,-1,-1};
        int previousRound=-1;
        for(int i=0;i<size;i++){
            var frame=record.frames().get(i);int round=frame.roundIndex();
            rounds[i]=frame.driver().getCompoundOrEmpty("match").getCompoundOrEmpty("currentRound");
            if(round!=previousRound){Arrays.fill(lastDecision,-1);previousRound=round;}
            int acting=decisionSeat(i);
            if(acting>=0){
                lastDecision[acting]=i;
                int river=player(i,acting).getListOrEmpty("discards").size();
                // Kan/kita and replacement draws can create several decisions within one discard turn.
                if(turnRound[acting]!=round||turnRiver[acting]!=river){
                    anchors.get(acting).add(i);turnRound[acting]=round;turnRiver[acting]=river;
                }
            }
            for(int seat=0;seat<4;seat++)decisions[seat][i]=lastDecision[seat];
        }
        turns=anchors.stream().map(List::copyOf).toList();
        // Adjacent observer frames contain every accepted action. Never scan over an intervening
        // kan, kita, win or another turn and accidentally call its later discard this decision's choice.
        for(int i=size-2;i>=0;i--){
            int seat=decisionSeat(i);if(seat<0||!sameRound(i,i+1))continue;
            var before=player(i,seat);var after=player(i+1,seat);
            ListTag oldRiver=before.getListOrEmpty("discards"),newRiver=after.getListOrEmpty("discards");
            int riverIndex=oldRiver.size();
            if(newRiver.size()==riverIndex+1&&prefixEquals(oldRiver,newRiver)){
                var entry=newRiver.getCompoundOrEmpty(riverIndex);String tile=tileCode(entry.getCompoundOrEmpty("tile"));
                if(tile!=null&&handIndex(before,tile)>=0)
                    discards.put(i,new Discard(i,i+1,record.frames().get(i).roundIndex(),seat,tile,riverIndex,entry.getBooleanOr("riichiDeclared",false)));
            }else if(decisionSeat(i+1)==seat&&oldRiver.equals(newRiver)
                    &&before.getListOrEmpty("currentHand").equals(after.getListOrEmpty("currentHand"))
                    &&before.getListOrEmpty("melds").equals(after.getListOrEmpty("melds"))){
                // A repeated decision snapshot (e.g. committing riichi) is the same outgoing action.
                Discard next=discards.get(i+1);
                if(next!=null)discards.put(i,new Discard(i,next.discardFrame(),next.roundIndex(),seat,next.tile(),next.riverIndex(),next.riichi()));
            }
        }
    }

    /** The selected seat's most recent decision in this hand; never leaks across a hand boundary. */
    public int decisionFrame(int displayFrame,int seat){
        return validFrame(displayFrame)&&seat>=0&&seat<4?decisions[seat][displayFrame]:-1;
    }
    /** The accepted outgoing discard of this exact decision, including when it is still in the future. */
    public Optional<Discard> discardForDecision(int decisionFrame,int seat){
        Discard discard=discards.get(decisionFrame);
        return discard!=null&&discard.seat()==seat?Optional.of(discard):Optional.empty();
    }
    /** Only an already played discard of the selected seat's current decision. */
    public Optional<Discard> discardAt(int displayFrame,int seat){
        return discardForDecision(decisionFrame(displayFrame,seat),seat).filter(d->displayFrame>=d.discardFrame());
    }
    public List<Integer> turnFrames(int seat){return seat>=0&&seat<4?turns.get(seat):List.of();}
    /** Previous own turn, or the start of the record if no earlier own turn exists. */
    public int previousTurn(int displayFrame,int seat){
        List<Integer> anchors=turnFrames(seat);int at=Collections.binarySearch(anchors,displayFrame);
        int previous=at>=0?at-1:-at-2;return previous>=0?anchors.get(previous):0;
    }
    /** Next own turn, or the final recorded state if no later own turn exists. */
    public int nextTurn(int displayFrame,int seat){
        List<Integer> anchors=turnFrames(seat);int at=Collections.binarySearch(anchors,displayFrame);
        int next=at>=0?at+1:-at-1;return next<anchors.size()?anchors.get(next):Math.max(0,record.frames().size()-1);
    }

    /** Actual chosen tile before play, then its exact river slot or claimed meld tile afterwards. */
    public Location locationAt(int displayFrame,int seat){
        if(!validFrame(displayFrame))return Location.none();
        var outgoing=discardForDecision(decisionFrame(displayFrame,seat),seat);
        if(outgoing.isEmpty())return Location.none();Discard discard=outgoing.get();
        if(displayFrame<discard.discardFrame()){
            var own=player(displayFrame,seat);ListTag hand=own.getListOrEmpty("currentHand");
            var active=rounds[displayFrame].getCompoundOrEmpty("activeTile");
            if(rounds[displayFrame].getIntOr("currentTurnSeat",-1)==seat
                    &&active.getStringOr("type","").equals("DRAWN")
                    &&discard.tile().equals(tileCode(active.getCompoundOrEmpty("tile")))){
                // A draw is appended to currentHand. Equal copies are interchangeable in the rules,
                // so prefer the visibly separated drawn copy when that is the accepted tile kind.
                for(int i=hand.size()-1;i>=0;i--)if(discard.tile().equals(tileCode(hand.getCompoundOrEmpty(i))))
                    return new Location(Zone.DRAWN,seat,i,-1,discard.tile());
            }
            int index=handIndex(own,discard.tile());
            return index<0?Location.none():new Location(Zone.HAND,seat,index,-1,discard.tile());
        }
        ListTag ownRiver=player(displayFrame,seat).getListOrEmpty("discards");
        if(discard.riverIndex()>=ownRiver.size()||!discard.tile().equals(tileCode(ownRiver.getCompoundOrEmpty(discard.riverIndex()).getCompoundOrEmpty("tile"))))return Location.none();
        ListTag players=rounds[displayFrame].getListOrEmpty("players");
        for(int owner=0;owner<players.size();owner++){
            ListTag melds=players.getCompoundOrEmpty(owner).getListOrEmpty("melds");
            for(int index=0;index<melds.size();index++){
                var meld=melds.getCompoundOrEmpty(index);
                var base=meld.getStringOr("type","").equals("KAKAN")?meld.getCompoundOrEmpty("upgradedFrom"):meld;
                if(base.getIntOr("sourceSeat",-1)!=seat||base.getIntOr("sourceDiscardIndex",-1)!=discard.riverIndex())continue;
                int claimed=base.getIntOr("claimedTileIndex",-1);ListTag tiles=base.getListOrEmpty("tiles");
                if(claimed<0||claimed>=tiles.size()||!discard.tile().equals(tileCode(tiles.getCompoundOrEmpty(claimed))))return Location.none();
                return new Location(Zone.MELD,owner,index,claimed,discard.tile());
            }
        }
        return new Location(Zone.RIVER,seat,discard.riverIndex(),-1,discard.tile());
    }

    private boolean validFrame(int index){return index>=0&&index<rounds.length;}
    private boolean sameRound(int first,int second){return record.frames().get(first).roundIndex()==record.frames().get(second).roundIndex();}
    private CompoundTag player(int frame,int seat){return rounds[frame].getListOrEmpty("players").getCompoundOrEmpty(seat);}
    private int decisionSeat(int frame){
        var phase=record.frames().get(frame).driver().getCompoundOrEmpty("phase");
        if(!phase.getStringOr("type","").equals("AWAITING_DISCARD"))return -1;
        int seat=phase.getIntOr("seat",-1);
        return seat>=0&&seat<4&&seat<rounds[frame].getListOrEmpty("players").size()?seat:-1;
    }
    private static boolean prefixEquals(ListTag prefix,ListTag full){
        for(int i=0;i<prefix.size();i++)if(!prefix.get(i).equals(full.get(i)))return false;return true;
    }
    private static int handIndex(CompoundTag player,String tile){
        ListTag hand=player.getListOrEmpty("currentHand");
        for(int i=0;i<hand.size();i++)if(tile.equals(tileCode(hand.getCompoundOrEmpty(i))))return i;return -1;
    }
    public static String tileCode(CompoundTag tag){
        String suit=tag.getStringOr("suit","");int rank=tag.getIntOr("rank",0);boolean red=tag.getBooleanOr("redDora",false);
        return switch(suit){
            case "MANZU"->rank>=1&&rank<=9?"m"+(red&&rank==5?0:rank):null;
            case "PINZU"->rank>=1&&rank<=9?"p"+(red&&rank==5?0:rank):null;
            case "SOUZU"->rank>=1&&rank<=9?"s"+(red&&rank==5?0:rank):null;
            case "WIND"->rank>=1&&rank<=4?"z"+rank:null;
            case "DRAGON"->rank>=1&&rank<=3?"z"+(rank+4):null;
            default->null;
        };
    }
}
