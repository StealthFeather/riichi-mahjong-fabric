package com.riichimahjong.fabric;

import java.util.Optional;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.replay.ReplayAnalysis;
import com.riichimahjong.replay.ReplayTimeline;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;

final class ReplayOverlayLayout {
    private ReplayOverlayLayout(){}
    static ChatFormatting color(ReplayAnalysis.Decision decision){
        if(decision.chosenTile()==null)return ChatFormatting.RED;
        var chosen=decision.candidates().stream().filter(c->sameKind(c.tile(),decision.chosenTile())).findFirst();
        double maximum=decision.candidates().stream().mapToDouble(ReplayAnalysis.Candidate::policyProbability).max().orElse(0);
        if(chosen.isPresent()&&chosen.get().policyProbability()>=maximum-1e-12)return ChatFormatting.GREEN;
        return decision.chosenRank()>=2&&decision.chosenRank()<=3?ChatFormatting.YELLOW:ChatFormatting.RED;
    }
    static float scoreScale(String text){return (float)(TableLayout.TILE_WIDTH/(.025*(TableButtonLayout.textWidth(text)+1)));}
    static String tileKind(String tile){
        return tile==null?null:ReplayAnalysis.tileKind(tile);
    }
    static boolean sameKind(String first,String second){return java.util.Objects.equals(tileKind(first),tileKind(second));}
    static boolean showProbability(ReplayAnalysis.Candidate candidate){return candidate.rank()<=3&&candidate.probability()>=1;}
    static boolean showPlayedProbability(ReplayAnalysis.Decision decision){
        return decision.chosenTile()!=null&&decision.chosenRank()>=1&&decision.chosenRank()<=3&&decision.chosenProbability()>=1;
    }

    /** Follow the actual historical choice from its hand to its exact river or called meld identity. */
    static Optional<Vec3> playedTile(MahjongTableBlockEntity table,ReplayTimeline.Location location){
        var driver=table.driver();if(driver==null||!location.present()||driver.match().currentRound().isEmpty())return Optional.empty();
        var round=driver.currentRound();int seat=location.seat();
        if(seat<0||seat>=round.players().size())return Optional.empty();
        var pos=table.getBlockPos();var dir=PortViews.outward(seat,table.getBlockState());
        if(location.zone()==ReplayTimeline.Zone.HAND||location.zone()==ReplayTimeline.Zone.DRAWN){
            if(!PublicTable.revealedSeats(table).contains(seat))return Optional.empty();
            var hand=table.handDisplayOrder(seat);var slots=table.handDisplaySlots(seat);var gaps=table.handDisplayGapStarts(seat);
            int index=-1;
            for(int i=0;i<hand.size();i++)if(com.riichimahjong.ai.AiSnapshot.tile(hand.get(i)).equals(location.tile())){
                index=i;if(location.zone()!=ReplayTimeline.Zone.DRAWN)break;
            }
            return index<0?Optional.empty():Optional.of(TableLayout.handAnchor(pos,dir,TableLayout.handAlong(slots.get(index),gaps),true));
        }
        if(location.zone()==ReplayTimeline.Zone.RIVER){
            var claimed=PublicTable.claimed(round.players(),seat);if(claimed.contains(location.index()))return Optional.empty();
            int visible=location.index()-(int)claimed.stream().filter(i->i<location.index()).count();
            return Optional.of(TableLayout.anchor(pos,dir,(visible%6-2.5)*.073,.59,.26+(visible/6)*.09));
        }
        if(location.zone()==ReplayTimeline.Zone.MELD){
            if(location.index()<0||location.index()>=round.players().get(seat).melds().size())return Optional.empty();
            var arranged=MeldLayout.arrange(round.players().get(seat).melds().get(location.index()),seat,round.players().size());
            int called=-1;
            for(int i=0;i<arranged.size();i++)if(arranged.get(i).sideways()&&!arranged.get(i).stacked()){called=i;break;}
            int index=called;
            var melds=PublicTable.capture(table).seats().get(seat).melds();
            return MeldGeometry.arrange(melds).stream().filter(p->p.meld()==location.index()&&p.index()==index)
                    .findFirst().map(p->TableLayout.anchor(pos,dir,p.along(),p.height(),p.distance()));
        }
        return Optional.empty();
    }
}
