package com.riichimahjong.fabric;

import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.ai.RandomThinkingBot;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import java.util.UUID;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Lobby and match transitions must remove mannequins until a BOT controls an active seat. */
public final class LobbyAvatarRegression {
    public static void main(String[] args) {
        var open = MahjongTableBlockEntity.SeatInfo.open();
        var closed = MahjongTableBlockEntity.SeatInfo.closed();
        var reserved = MahjongTableBlockEntity.SeatInfo.claimed(UUID.randomUUID());
        var ai = new KobalabBot(BotDifficulty.NORMAL);
        var bot = new RandomThinkingBot();
        var human = new MahjongTableHumanPlayer();
        try {
            for (var seat : java.util.List.of(open, closed, reserved)) {
                check(!BotAvatars.shouldShow(seat, false, null), "lobby exposes a mannequin before the first match");
                check(!BotAvatars.shouldShow(seat, false, ai), "ended match leaves a mannequin in the lobby");
                check(!BotAvatars.shouldShow(seat, true, human), "a human-controlled seat retains its mannequin");
                check(!BotAvatars.shouldShow(seat, true, null), "seat outside the driver player count has a mannequin");
            }
            check(BotAvatars.shouldShow(open, true, ai), "active four-player AI is hidden");
            check(BotAvatars.shouldShow(open, true, bot), "active three-player/Sichuan BOT is hidden");
            check(BotAvatars.shouldShow(reserved, true, ai), "offline player's AI replacement is hidden");
            check(!BotAvatars.shouldShow(closed, true, ai), "disabled seat exposes a mannequin");
        } finally { ai.close(); }
        System.out.println("PASS: lobby has no mannequins; active enabled BOT seats appear and human takeover hides them");
    }
}
