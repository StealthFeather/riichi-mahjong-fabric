// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable.record;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.StartMatchResult;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongFixedDeal;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTileSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public abstract class MahjongTableRecordSimpleEastHandItem extends MahjongTableRecordItem {
    protected MahjongTableRecordSimpleEastHandItem(Properties properties) {
        super(properties);
    }

    protected abstract List<TheMahjongTile> handTiles();

    protected abstract List<TheMahjongTile> wallTiles();

    protected TheMahjongTile doraIndicator() {
        return null;
    }

    protected List<TheMahjongTile> rinshanTiles() {
        return List.of();
    }

    protected List<TheMahjongTile> secondHandTiles() {
        return List.of();
    }

    protected boolean breakFirstRoundUninterrupted() {
        return true;
    }

    protected int startingHandNumber() {
        return 1;
    }

    protected TheMahjongTile.Wind roundWind() {
        return TheMahjongTile.Wind.EAST;
    }

    protected int honba() {
        return 0;
    }

    protected TheMahjongRuleSet ruleSet() {
        return TheMahjongRuleSet.wrc();
    }

    protected TheMahjongTileSet tileSet() {
        return TheMahjongTileSet.standardRiichi(false);
    }

    protected RuleSetPreset preset() {
        return RuleSetPreset.WRC_4P;
    }

    @Override
    protected boolean isPredefinedFixture() {
        return true;
    }

    protected static TheMahjongTile m(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, false);
    }

    protected static TheMahjongTile p(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false);
    }

    protected static TheMahjongTile s(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false);
    }

    protected static TheMahjongTile w(TheMahjongTile.Wind wind) {
        return new TheMahjongTile(TheMahjongTile.Suit.WIND, wind.tileRank(), false);
    }

    protected static TheMahjongTile d(TheMahjongTile.Dragon dragon) {
        return new TheMahjongTile(TheMahjongTile.Suit.DRAGON, dragon.tileRank(), false);
    }

    @Override
    public boolean applyToTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        TheMahjongMatch match;
        try {
            match = buildMatch();
        } catch (RuntimeException ex) {
            player.sendSystemMessage(
                    Component.literal("Predefined record failed to build: " + ex.getMessage()), true);
            return false;
        }
        StartMatchResult result = table.tryApplyPredefined(
                 0xC6E4D31L, preset(), match, player.getUUID());
        if (result == StartMatchResult.NOT_IDLE) {
            player.sendSystemMessage(
                    Component.literal("Cannot load record: end the current match first."), true);
            return false;
        }
        return true;
    }

    @Override
    public boolean recordFromTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        player.sendSystemMessage(
                Component.literal("Predefined records cannot be re-recorded."), true);
        return false;
    }

    private TheMahjongMatch buildMatch() {
        List<TheMahjongTile> hand = handTiles();
        if (hand.size() != 14) {
            throw new IllegalStateException(
                    "handTiles() must return exactly 14 tiles (13 concealed + 1 drawn), got " + hand.size());
        }
        List<TheMahjongTile> concealed = List.copyOf(hand.subList(0, 13));
        TheMahjongTile drawn = hand.get(13);

        List<TheMahjongTile> wallProvided = wallTiles();
        List<TheMahjongTile> top = new ArrayList<>(1 + wallProvided.size());
        top.add(drawn);
        top.addAll(wallProvided);

        TheMahjongFixedDeal.Builder b = TheMahjongFixedDeal.builder()
                .playerCount(preset().playerCount())
                .ruleSet(ruleSet())
                .tileSet(tileSet())
                .roundWind(roundWind())
                .handNumber(startingHandNumber())
                .honba(honba())
                .handForSeat(0, concealed)
                .topOfWall(top)
                .drawForDealer(true);

        List<TheMahjongTile> second = secondHandTiles();
        if (!second.isEmpty()) {
            if (second.size() != 13) {
                throw new IllegalStateException(
                        "secondHandTiles(), if set, must be 13 tiles; got " + second.size());
            }
            b.handForSeat(preset().playerCount() - 1, second);
        }

        TheMahjongTile dora = doraIndicator();
        if (dora != null) {
            b.doraIndicators(List.of(dora));
        }

        List<TheMahjongTile> rinshan = rinshanTiles();
        if (!rinshan.isEmpty()) {
            b.rinshanTiles(rinshan);
        }

        TheMahjongMatch match = b.buildMatch();

        if (breakFirstRoundUninterrupted()) {
            match = withFakedPriorTurns(match,  3);
        }
        return match;
    }

    private static TheMahjongMatch withFakedPriorTurns(TheMahjongMatch match, int count) {
        TheMahjongRound r = match.currentRound().orElse(null);
        if (r == null || count <= 0) return match;
        if (r.liveWall().size() < count) return match;
        List<TheMahjongTile> taken = r.liveWall().subList(0, count);
        List<TheMahjongTile> newLiveWall = new ArrayList<>(r.liveWall().subList(count, r.liveWall().size()));

        TheMahjongPlayer dealer = r.players().get(0);
        List<TheMahjongDiscard> newDiscards = new ArrayList<>(dealer.discards());
        for (TheMahjongTile t : taken) newDiscards.add(new TheMahjongDiscard(t, false));
        TheMahjongPlayer newDealer = new TheMahjongPlayer(
                dealer.points(), dealer.riichiState(), dealer.ippatsuEligible(),
                dealer.currentHand(), dealer.melds(), newDiscards,
                dealer.temporaryFuritenTiles(), dealer.riichiPermanentFuriten(),
                dealer.kitaCount());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        newPlayers.set(0, newDealer);

        TheMahjongRound newRound = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(),
                r.dealerSeat(), r.state(),
                r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(),
                newLiveWall, r.rinshanTiles(),
                r.doraIndicators(), r.uraDoraIndicators(),
                r.revealedDoraCount(),
                newPlayers, r.pendingDeltas());
        return new TheMahjongMatch(
                match.playerCount(), match.startingPoints(), match.targetPoints(),
                match.roundCount(), match.state(), match.tileSet(), match.ruleSet(),
                match.completedRounds(), newRound);
    }
}
