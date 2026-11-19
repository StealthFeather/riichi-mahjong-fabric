package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.*;
import java.util.ArrayList;
import java.util.List;

/** Self-draw choreography, multi-winner sessions and registration contracts. */
public final class WinEffectRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual-expected)<1e-7,message+": "+actual);
    }
    private static void rejected(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static WinEffectContext context(String id) {
        // The public tile code includes red-five identity; both ron winners share one source.
        return new WinEffectContext(id,new WinEffectContext.Source("river/0/5",34),
                List.of(new WinEffectContext.Winner(1,34),new WinEffectContext.Winner(2,34)));
    }
    private static void finite(WinEffectFrame.TilePose pose) {
        check(Double.isFinite(pose.x())&&Double.isFinite(pose.y())&&Double.isFinite(pose.z())
                &&Double.isFinite(pose.flip())&&Float.isFinite(pose.scale()),"non-finite tile pose");
        check(pose.scale()>=0&&pose.flip()>=0&&pose.flip()<=1,"invalid tile pose range");
    }
    private static void selfDrawTimeline() {
        var context=new WinEffectContext(WinEffects.DEFAULT,new WinEffectContext.Source("hand/1/13",34),
                List.of(new WinEffectContext.Winner(1,34)));
        var effect=new WinEffectSession(context).effect();
        var initial=effect.frame(context,0);
        check(initial.source().visible()&&!initial.winningTile().visible(),"initial source missing or duplicate destination");
        near(initial.source().y(),0,"source must start on the table");
        near(initial.source().scale(),1,"source must start at normal scale");
        near(initial.handReveal(),0,"hand already pushed down at start");
        check(effect.consumesSource(),"fang effect must consume the source visual");
        var before=effect.frame(context,-10);
        near(before.source().y(),0,"negative pre-roll sinks the source");
        near(before.handReveal(),0,"negative pre-roll reveals the hand");

        double previousSink=0,previousScale=1,previousRise=-.6,previousReveal=0;
        for(int tick=0;tick<=effect.durationTicks()+10;tick++) {
            var frame=effect.frame(context,tick);
            finite(frame.source());finite(frame.winningTile());
            check(frame.source().y()<=previousSink+1e-7,"source moved up while being swallowed");
            check(frame.source().scale()<=previousScale+1e-7,"source grew during swallowing");
            check(frame.winningTile().y()>=previousRise-1e-7,"emerging tile moved back below the table");
            check(frame.handReveal()>=previousReveal-1e-7,"hand stood back up during reveal");
            if(tick<18)check(frame.source().visible(),"source disappeared before the bite completed");
            else check(!frame.source().visible(),"source remains after being eaten");
            if(tick<24)check(!frame.winningTile().visible(),"destination appeared before the source was eaten");
            else check(frame.winningTile().visible(),"destination vanished during emergence");
            if(tick<48)near(frame.handReveal(),0,"hand reveal overlaps the tile emergence");
            previousSink=frame.source().y();previousScale=frame.source().scale();
            previousRise=frame.winningTile().y();previousReveal=frame.handReveal();
        }
        check(effect.frame(context,12).source().y()<0,"fang bite never pulls the source below the table");
        check(effect.frame(context,24).winningTile().y()<-.4,"winning tile does not start below the table");
        var emerged=effect.frame(context,44);
        near(emerged.winningTile().y(),0,"winning tile did not reach its hand slot");
        near(emerged.winningTile().flip(),0,"winning tile was not upright after emergence");
        var pushing=effect.frame(context,54);
        check(pushing.handReveal()>0&&pushing.handReveal()<1,"hand reveal does not animate");
        near(pushing.winningTile().flip(),pushing.handReveal(),"winning tile and whole hand fall at different times");
        var finished=effect.frame(context,60);
        near(finished.handReveal(),1,"whole hand has not fallen at the end");
        near(finished.winningTile().flip(),1,"winning tile has not fallen at the end");
        check(effect.cues().stream().anyMatch(c->c.type().equals("entity")
                &&c.resource().equals("minecraft:evoker_fangs")&&c.anchor()==WinEffectCue.Anchor.SOURCE),"missing source fangs cue");
        check(effect.cues().stream().allMatch(c->c.tick()>=0&&c.tick()<effect.durationTicks()),"cue outside playback");
    }
    private static void contextAndSession() {
        var winners=new ArrayList<>(List.of(new WinEffectContext.Winner(1,34),new WinEffectContext.Winner(2,34)));
        var context=new WinEffectContext(WinEffects.DEFAULT,new WinEffectContext.Source("river/0/5",34),winners);
        winners.clear();
        check(context.winners().size()==2&&context.includes(1)&&context.includes(2)&&!context.includes(0),"mutable winner list or wrong winners");
        check(context.source().tileCode()==34&&context.winners().stream().allMatch(w->w.tileCode()==34),"multi-ron changed red-five identity");
        try { context.winners().clear();throw new AssertionError("context exposes mutable winners"); }
        catch(UnsupportedOperationException expected) {}
        rejected(()->new WinEffectContext(WinEffects.DEFAULT,context.source(),List.of()),"empty winner event accepted");
        var session=new WinEffectSession(context);
        check(session.context()==context&&session.elapsedTicks()==0,"session lost its captured context or initial frame");
        int duration=session.effect().durationTicks();
        for(int tick=1;tick<duration;tick++) {
            check(!session.advance(),"playback ended before its duration");
            check(session.elapsedTicks()==tick,"playback skipped a frame");
        }
        check(session.advance()&&session.elapsedTicks()==duration,"playback never completed");
        near(session.frame().handReveal(),1,"completed playback did not settle to its final hand");
        var fallback=new WinEffectSession(context("missing:effect"));
        check(fallback.effect().id().equals(WinEffects.DEFAULT),"unknown saved ID has no default fallback");
    }
    private static WinEffect custom(String id,int duration,List<WinEffectCue> cues) {
        return new WinEffect() {
            public String id() { return id; }
            public String displayName() { return "Regression custom effect"; }
            public int durationTicks() { return duration; }
            public List<WinEffectCue> cues() { return cues; }
            public WinEffectFrame frame(WinEffectContext context,int tick) { return WinEffectFrame.FINAL; }
        };
    }
    private static void replacement() {
        var cue=new WinEffectCue(1,"sound","minecraft:block.note_block.bell",WinEffectCue.Anchor.WINNER,.5f,1);
        var custom=custom("regression:replacement",2,List.of(cue));
        WinEffects.register(custom);
        var session=new WinEffectSession(context(custom.id()));
        check(session.effect()==custom&&WinEffects.all().contains(custom),"registered effect cannot be selected");
        check(session.effect().cues().equals(List.of(cue)),"replacement lost its cues");
        check(!session.advance()&&session.advance(),"replacement duration ignored");
        rejected(()->WinEffects.register(custom),"duplicate ID silently replaced a registered effect");
        rejected(()->WinEffects.register(custom("bad id",2,List.of())),"invalid effect ID accepted");
        rejected(()->WinEffects.register(custom("regression:zero",0,List.of())),"zero-duration effect accepted");
        rejected(()->WinEffects.register(custom("regression:long",201,List.of())),"unbounded playback accepted");
        rejected(()->WinEffects.register(custom("regression:late_cue",1,List.of(cue))),"cue past playback accepted");
        var plain=WinEffects.resolve("riichi_mahjong:none");
        check(!plain.consumesSource(),"direct reveal consumes the river tile");
        near(plain.frame(context(plain.id()),0).handReveal(),1,"direct reveal still waits for animation");
    }
    public static void main(String[] args) {
        selfDrawTimeline();contextAndSession();replacement();
        System.out.println("PASS: self-draw fangs consume the source before emergence and hand reveal; win effects preserve multi-ron identity and support bounded replaceable playback with unknown-ID fallback");
    }
}
