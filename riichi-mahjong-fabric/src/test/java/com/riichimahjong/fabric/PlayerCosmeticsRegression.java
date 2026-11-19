package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.*;
import com.riichimahjong.replay.ReplayRecord;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.*;
import static com.riichimahjong.fabric.RulesRegression.check;

public final class PlayerCosmeticsRegression {
    private static final String PLAIN = "riichi_mahjong:none";

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            checkCosmetics();
        } finally {
            Util.shutdownExecutors();
        }
    }

    private static void checkCosmetics() throws Exception {
        var directory = Files.createTempDirectory("riichi-player-cosmetics");
        var registries = HolderLookup.Provider.create(Stream.empty());
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        try {
            try (var store = new SavedDataStorage(directory, DataFixers.getDataFixer(), registries)) {
                var data = store.computeIfAbsent(PlayerCosmetics.TYPE);
                data.set(first, new PlayerCosmetics.Selection(RiichiSkin.JADE, PLAIN));
                check(data.selection(second).equals(PlayerCosmetics.Selection.DEFAULT), "skin leaked to a second player");
                data.set(second, new PlayerCosmetics.Selection(RiichiSkin.GOLD, WinEffects.DEFAULT));
                store.saveAndJoin();
            }
            try (var store = new SavedDataStorage(directory, DataFixers.getDataFixer(), registries)) {
                var data = store.get(PlayerCosmetics.TYPE);
                check(data.selection(first).equals(new PlayerCosmetics.Selection(RiichiSkin.JADE, PLAIN)), "first player's settings lost on reload");
                check(data.selection(second).equals(new PlayerCosmetics.Selection(RiichiSkin.GOLD, WinEffects.DEFAULT)), "second player's settings lost on reload");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (var f : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
            }
        }
        var ctx = new WinEffectContext(PLAIN, new WinEffectContext.Source("river/0/2", 4),
                List.of(new WinEffectContext.Winner(1, 4), new WinEffectContext.Winner(3, 4)))
                .withChoices(seat -> seat == 1 ? PLAIN : WinEffects.DEFAULT);
        var session = new WinEffectSession(ctx);
        check(session.effect().consumesSource() && !session.effect(1).consumesSource(), "shared discard used the instant winner's effect");
        check(session.frame(1).handReveal() == 1 && session.frame(3).handReveal() == 0, "multi-ron merged individual hand animations");
        var queue = new DeferredWinQueue();
        queue.add(ctx);
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        queue.write(out);
        var restored = new DeferredWinQueue();
        restored.read(TagValueInput.create(ProblemReporter.DISCARDING, registries, out.buildResult()));
        check(restored.contexts().equals(queue.contexts()), "blood battle changed winner choices during reload");
        check(restored.next().effectId().equals(PLAIN) && restored.next().effectId().equals(WinEffects.DEFAULT), "blood battle used a shared effect");
        var legacy = new ReplayRecord.Frame(0, "", new CompoundTag());
        check(legacy.riichiSkin(1)==RiichiSkin.CLASSIC, "legacy replay lost the original stick");
        var tag = new CompoundTag();
        var skins = new CompoundTag(); skins.putString("1", "JADE"); tag.put("RiichiStickSkins", skins);
        var frame = new ReplayRecord.Frame(0, "", tag);
        check(frame.riichiSkin(1)==RiichiSkin.JADE && frame.riichiSkin(2)==RiichiSkin.CLASSIC, "wrong replay seat skin");
        System.out.println("PASS: individual skin and win choices survive disk reload, simultaneous ron, delayed blood-battle playback and replay skin lookup");
    }
}
