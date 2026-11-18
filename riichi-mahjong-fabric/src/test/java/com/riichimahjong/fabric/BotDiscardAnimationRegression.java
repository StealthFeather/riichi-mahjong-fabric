package com.riichimahjong.fabric;

import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.BotDiscardAnimation;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.ai.RandomThinkingBot;
import com.themahjong.driver.*;
import java.util.List;
import java.util.Optional;
import static com.riichimahjong.fabric.RulesRegression.*;

/** The look must be visible before a discard can be accepted, including forced riichi discards. */
public final class BotDiscardAnimationRegression {
    private static DecisionRequest request(PlayerAction... actions) {
        var original = AiControllerRegression.request();
        return new DecisionRequest(0, new MatchPhase.AwaitingDiscard(0), List.of(actions),
                original.round(), original.rules());
    }

    private static void lockAndTiming() {
        var first = new PlayerAction.Discard(tile(4, 2));
        var other = new PlayerAction.Discard(tile(4, 1));
        var request = request(first, other);
        var animation = new BotDiscardAnimation();
        check(animation.select(request, first, 10, true).isEmpty(), "first frame skipped visible look");
        check(animation.tileFor(request.round(), request.phase()).orElseThrow().equals(first.tile()), "look target missing");
        check(animation.select(request, other, .249, true).isEmpty(), "discard completed before look deadline");
        check(animation.tileFor(request.round(), request.phase()).orElseThrow().equals(first.tile()), "polling changed chosen tile");
        check(animation.select(request, other, .001, false).isEmpty(), "look bypassed normal thinking delay");
        check(animation.select(request, other, 0, true).orElseThrow().equals(first), "selected tile differs from look target");
        check(animation.tileFor(request.round(), request.phase()).isEmpty(), "accepted choice still exposed as preview");
        check(animation.select(request, first, 10, true).isEmpty(), "same decision submitted twice");
    }

    private static void invalidDeltaAndChoice() {
        var action = new PlayerAction.DiscardWithRiichi(tile(4, 2));
        var request = request(action);
        var animation = new BotDiscardAnimation();
        check(animation.select(request, new PlayerAction.Discard(tile(4, 1)), 10, true).isEmpty(), "illegal action accepted");
        check(animation.tileFor(request.round(), request.phase()).isEmpty(), "illegal action was previewed");
        check(animation.select(request, action, Double.NaN, true).isEmpty(), "first riichi frame accepted");
        for (double delta : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -10})
            check(animation.select(request, action, delta, true).isEmpty(), "invalid delta bypassed look duration");
        check(animation.select(request, null, .249, true).isEmpty(), "invalid delta added elapsed time");
        check(animation.select(request, null, .001, true).orElseThrow().equals(action), "riichi look did not complete");
    }

    private static void cancellation() {
        var action = new PlayerAction.Discard(tile(4, 2));
        var original = request(action);
        var animation = new BotDiscardAnimation();
        animation.select(original, action, .05, false);
        var replacement = request(action);
        check(animation.tileFor(replacement.round(), replacement.phase()).isEmpty(), "old round target leaked into replacement");
        check(animation.tileFor(original.round(), new MatchPhase.AwaitingDiscard(1)).isEmpty(), "old seat target survived turn change");
        var draw = new DecisionRequest(0, new MatchPhase.AwaitingDraw(0, false), List.of(PlayerAction.Draw.INSTANCE),
                original.round(), original.rules());
        check(animation.select(draw, null, 10, true).isEmpty(), "missing new candidate used stale action");
        check(animation.tileFor(original.round(), original.phase()).isEmpty(), "new request did not clear old preview");
        check(animation.select(draw, PlayerAction.Draw.INSTANCE, 0, true).orElseThrow() instanceof PlayerAction.Draw,
                "non-discard action gained animation delay");
        animation.select(replacement, action, .05, false);
        animation.clear();
        check(animation.tileFor(replacement.round(), replacement.phase()).isEmpty(), "close/reset retained preview");
    }

    private static BotDiscardAnimation animation(MahjongPlayerInterface bot) {
        return bot instanceof KobalabBot ai ? ai.discardAnimation() : ((RandomThinkingBot)bot).discardAnimation();
    }

    private static void controllers() {
        for (MahjongPlayerInterface bot : List.of(new KobalabBot(BotDifficulty.EASY),
                new KobalabBot(BotDifficulty.NORMAL), new KobalabBot(BotDifficulty.HARD), new RandomThinkingBot())) {
            try {
                for (PlayerAction action : List.of(new PlayerAction.Discard(tile(4, 2)), new PlayerAction.DiscardWithRiichi(tile(4, 2)))) {
                    var request = request(action);
                    int lookFrames = 0;
                    Optional<PlayerAction> accepted = Optional.empty();
                    for (int tick = 0; tick < 30 && accepted.isEmpty(); tick++) {
                        accepted = bot.chooseAction(request, .05);
                        if (animation(bot).tileFor(request.round(), request.phase()).isPresent()) lookFrames++;
                    }
                    check(lookFrames >= 5, "controller did not expose at least 0.25 seconds of look frames");
                    check(accepted.orElseThrow().equals(action), "controller changed forced discard or exceeded 1.5 seconds");
                    check(bot.chooseAction(request, 10).isEmpty(), "controller repeated accepted discard");
                }
                var request = request(new PlayerAction.Discard(tile(4, 1)));
                check(bot.chooseAction(request, .05).isEmpty(), "new controller decision skipped thinking");
                if (bot instanceof KobalabBot ai) {
                    check(ai.discardAnimation().tileFor(request.round(), request.phase()).isPresent(), "AI close fixture lacks preview");
                    ai.close();
                    check(ai.discardAnimation().tileFor(request.round(), request.phase()).isEmpty(), "closed AI retained preview");
                }
            } finally {
                if (bot instanceof KobalabBot ai) ai.close();
            }
        }
    }

    public static void main(String[] args) {
        lockAndTiming(); invalidDeltaAndChoice(); cancellation(); controllers();
        System.out.println("PASS: AI discard and riichi previews precede acceptance, lock the selected tile, respect thinking time, cancel stale requests and clear on close");
    }
}
