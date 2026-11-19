package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.*;
import java.util.List;
import static com.riichimahjong.fabric.RulesRegression.check;
import static com.riichimahjong.fabric.wineffect.WinEffectCue.Anchor.*;
import static com.riichimahjong.fabric.wineffect.WinEffectFrame.Action.*;
import static com.riichimahjong.fabric.wineffect.WinEffectFrame.Actor.*;

public final class RonWinEffectRegression {
    private static WinEffectSession session(String id, String slot) {
        var winners = slot.startsWith("hand/") ? List.of(new WinEffectContext.Winner(1, 34))
                : List.of(new WinEffectContext.Winner(1, 34), new WinEffectContext.Winner(3, 34));
        return new WinEffectSession(new WinEffectContext("riichi_mahjong:" + id,
                new WinEffectContext.Source(slot, 34), winners));
    }

    private static WinEffectFrame frame(WinEffectSession session, int tick) {
        return session.effect().frame(session.context(), tick);
    }

    private static WinEffectFrame.ActorPose actor(WinEffectFrame frame, WinEffectFrame.Actor kind,
            WinEffectCue.Anchor anchor, WinEffectFrame.Action action) {
        return frame.actors().stream().filter(a -> a.kind() == kind && a.anchor() == anchor && a.action() == action)
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + kind + " " + action + " at " + anchor));
    }

    private static void handsFirst(String id) {
        for (String source : List.of("river/0/8", "robbed/0")) {
            var session = session(id, source);
            check(session.effect().id().equals(session.context().effectId()), "Ron lost its selected effect");
            check(WinEffects.all().contains(session.effect()), "Ron effect missing from table settings");
            check(session.effect().consumesSource(), "Ron keeps a duplicate in the river");
            boolean tilting = false, moved = false;
            double previousReveal = 0;
            do {
                var f = session.frame();
                check(f.handReveal() >= previousReveal, "Hand stood back up during " + id);
                check(!(f.source().visible() && f.winningTile().visible()), "Duplicate visible winning tile");
                if (f.handReveal() < 1) {
                    tilting |= f.handReveal() > 0;
                    check(f.actors().isEmpty() && f.source().equals(WinEffectFrame.TilePose.STANDING)
                            && !f.winningTile().visible(), "Discard or actors moved before the hand was flat");
                }
                if (f.winningTile().visible()) {
                    check(f.winningTile().flip() == 1 && f.handReveal() == 1, "Arriving tile is not face up");
                    moved = true;
                }
                previousReveal = f.handReveal();
            } while (!session.advance());
            var end = session.frame();
            check(tilting && moved && end.actors().isEmpty() && !end.source().visible()
                    && end.winningTile().visible() && end.winningTile().y() == 0
                    && end.winningTile().scale() == 1 && end.travel() == 1, "Incomplete scene or final pose");
        }
        check(session(id, "hand/1/13").effect().id().equals(WinEffects.DEFAULT), "Ron style changed a self draw");
    }

    private static void enderman() {
        var s = session("enderman", "river/0/8");
        actor(frame(s,14), ENDERMAN, SOURCE, IDLE);
        var pickup = frame(s,24);
        actor(pickup, ENDERMAN, SOURCE, CARRY);
        var before = frame(s,31);
        var after = frame(s,32);
        actor(before, ENDERMAN, SOURCE, CARRY);
        actor(after, ENDERMAN, WINNER, CARRY);
        check(pickup.source().y() > 0 && before.source().y() > pickup.source().y(), "Tile did not lift");
        check(!after.source().visible() && after.winningTile().visible() && after.travel() == 1
                && Math.abs(after.winningTile().y() - before.source().y()) < 1e-7, "Teleport broke the carry");
        var placed = frame(s,43);
        actor(placed, ENDERMAN, WINNER, IDLE);
        check(placed.winningTile().y() == 0, "Tile remained suspended after release");
    }

    private static void evoker() {
        var s = session("evoker_fangs", "river/0/8");
        var cast = frame(s,18);
        actor(cast, EVOKER, WINNER, CAST);
        check(cast.source().visible() && cast.actors().size() == 1, "Fangs appeared before the cast");
        var bite = frame(s,36);
        actor(bite, EVOKER, WINNER, CAST);
        actor(bite, FANGS, SOURCE, IDLE);
        check(bite.source().visible() && bite.source().y() < 0 && bite.source().scale() < 1, "Fangs did not swallow");
        check(!frame(s,42).source().visible(), "Tile remains after the bite");
        var emerge = frame(s,48);
        check(emerge.actors().isEmpty() && emerge.winningTile().visible() && emerge.winningTile().y() < 0,
                "Tile must emerge below the empty hand slot after the evoker vanishes");
        check(frame(s,56).winningTile().y() > emerge.winningTile().y() && frame(s,62).winningTile().y() == 0,
                "Tile did not rise to the felt");
    }

    private static int soundTick(WinEffectSession session, String id) {
        return session.effect().cues().stream().filter(cue -> cue.type().equals("sound")
                && cue.resource().equals("minecraft:" + id)).mapToInt(WinEffectCue::tick).findFirst().orElseThrow();
    }

    private static void warden() {
        var s = session("warden", "river/0/8");
        int charge = soundTick(s, "entity.warden.sonic_charge");
        int release = soundTick(s, "entity.warden.sonic_boom");
        // Minecraft 26.3 SonicBoom.start() delays the attack 34 ticks after SONIC_CHARGE.
        check(release - charge == 34, "Sonic blast does not match the native Warden wind-up");
        var buried = actor(frame(s,14), WARDEN, SOURCE, EMERGE);
        var emerged = actor(frame(s,charge-1), WARDEN, SOURCE, EMERGE);
        check(buried.y() < 0 && emerged.y() == 0, "Warden did not climb out at the discard");
        for (int tick = charge; tick < release; tick++) {
            var windingUp = frame(s,tick);
            actor(windingUp, WARDEN, SOURCE, SONIC);
            check(windingUp.actors().stream().noneMatch(a -> a.kind() == SONIC_WAVE)
                    && windingUp.source().equals(WinEffectFrame.TilePose.STANDING) && !windingUp.winningTile().visible(),
                    "The blast or discard moved before the Warden released its attack");
        }
        var blast = frame(s,release);
        actor(blast, WARDEN, SOURCE, SONIC);
        actor(blast, SONIC_WAVE, SOURCE, SONIC);
        int landed = soundTick(s, "block.bamboo_wood.place");
        var landing = frame(s,landed);
        check(landing.winningTile().visible() && landing.winningTile().y() == 0 && landing.travel() == 1,
                "Blasted tile did not settle at the hand slot");
        check(!blast.source().visible() && blast.winningTile().visible()
                && blast.travel() > 0 && blast.travel() < 1 && blast.winningTile().y() > 0,
                "Sonic release, beam and first tile displacement must share the impact frame");
        double lastTravel = blast.travel();
        for (int tick = release + 1; tick <= landed; tick++) {
            var flight = frame(s,tick);
            check(flight.winningTile().visible() && flight.travel() > lastTravel,
                    "Blasted tile paused or reversed before reaching the hand");
            lastTravel = flight.travel();
        }
    }

    public static void main(String[] args) {
        for (String id : List.of("enderman", "evoker_fangs", "warden")) handsFirst(id);
        enderman();
        evoker();
        warden();
        System.out.println("PASS: all three ron scenes reveal first, stage the actors at the discard and winner, and deliver one face-up tile");
    }
}
