package com.riichimahjong.fabric;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Exercise public dora visibility through actual rule transitions and vanilla item components. */
public final class DoraTilesRegression {
    private static final Set<Integer> RED = Set.of(34,35,36);
    private static final List<TheMahjongTile> DORA = List.of(
            tile(4,3),tile(2,4),tile(0,4),tile(3,4),tile(1,8));
    private static final List<TheMahjongTile> URA = List.of(
            tile(1,6),tile(2,8),tile(0,8),tile(4,1),tile(3,1));

    private static List<TheMahjongPlayer> players(int count) {
        var result = new ArrayList<TheMahjongPlayer>();
        for (int seat=0; seat<count; seat++) result.add(player(waiting(1+seat%2)));
        return result;
    }

    private static TheMahjongRound round(List<TheMahjongPlayer> players,
            List<TheMahjongTile> indicators, List<TheMahjongTile> ura, int revealed) {
        return new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,
                TheMahjongRound.State.TURN,0,-1,TheMahjongRound.ActiveTile.drawn(tile(4,1)),
                Collections.nCopies(20,tile(4,2)),Collections.nCopies(4,tile(4,2)),
                indicators,ura,revealed,players,List.of());
    }

    private static TheMahjongDriver restored(TheMahjongRound round) {
        int count = round.players().size();
        var rules = count==3 ? TheMahjongRuleSet.mahjongSoulSanma() : TheMahjongRuleSet.mahjongSoul();
        var match = new TheMahjongMatch(count,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(false),rules,List.of(),round);
        return new TheMahjongDriver(match,
                round.players().stream().map(p -> (MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),
                new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }

    private static TheMahjongPlayer riichi(TheMahjongPlayer player, TheMahjongPlayer.RiichiState state) {
        return new TheMahjongPlayer(player.points(),state,false,player.currentHand(),player.melds(),
                player.discards(),player.temporaryFuritenTiles());
    }

    private static Set<Integer> doraCodes(TheMahjongDriver driver) {
        return DoraTiles.tiles(driver.currentRound(),driver.currentPhase());
    }

    private static void indicator(TheMahjongTile indicator, TheMahjongTile expected, int count) {
        var indicators = new ArrayList<>(DORA);
        indicators.set(0,indicator);
        var round = round(players(count),indicators,URA,1);
        var wanted = new HashSet<>(RED);
        wanted.add(MahjongTileItems.codeForTile(expected));
        check(DoraTiles.tiles(round,new MatchPhase.AwaitingDiscard(0)).equals(wanted),
                count+"p indicator "+indicator+" must highlight "+expected+" only, besides red fives");
    }

    private static void cyclesAndHiddenIndicators() {
        for (int count : new int[]{3,4}) {
            for (int suit=1; suit<=2; suit++) {
                indicator(tile(suit,9),tile(suit,1),count);
                indicator(tile(suit,4),tile(suit,5),count);
            }
            indicator(tile(0,9),tile(0,1),count);
            indicator(tile(0,1),tile(0,count==3 ? 9 : 2),count);
            for (int rank=1; rank<=4; rank++) indicator(tile(3,rank),tile(3,rank==4 ? 1 : rank+1),count);
            for (int rank=1; rank<=3; rank++) indicator(tile(4,rank),tile(4,rank==3 ? 1 : rank+1),count);
            indicator(new TheMahjongTile(TheMahjongTile.Suit.PINZU,5,true),tile(1,6),count);

            var round = round(players(count),DORA,URA,1);
            var live = DoraTiles.tiles(round,new MatchPhase.AwaitingDiscard(0));
            check(live.equals(Set.of(31,34,35,36)),"unrevealed normal/ura indicators leaked into live highlights");
            check(!live.contains(-1),"concealed tile backs can never inherit a dora identity");
            check(DoraTiles.tiles(round,new MatchPhase.Dealing(MatchPhase.Stage.WALL_BUILDING,.5)).equals(RED),
                    "wall-building highlights exposed the first unrevealed indicator");
            check(!DoraTiles.uraVisible(round,new MatchPhase.AwaitingDiscard(0)),"live play exposed ura indicators");
        }
        check(DoraTiles.tiles(null,new MatchPhase.NotStarted()).isEmpty(),"empty table has dora identities");
        check(!DoraTiles.uraVisible(null,new MatchPhase.RoundEnded(List.of())),"empty table exposes ura indicators");
    }

    private static void acceptedKanReveals() {
        var hands = players(4);
        hands.set(0,player(List.of(tile(0,1),tile(0,1),tile(0,1),tile(0,1),tile(0,2),tile(0,3),tile(0,4),
                tile(1,2),tile(1,3),tile(1,4),tile(2,5),tile(2,6),tile(3,1),tile(4,2))));
        var driver = restored(round(hands,DORA,URA,1));
        var before = doraCodes(driver);
        check(!before.contains(22),"kan fixture already exposes the second indicator's sou-five");
        var kan = driver.legalActions(0).stream().filter(a -> a instanceof PlayerAction.Ankan).findFirst().orElseThrow();
        act(driver,0,kan);
        check(driver.currentRound().revealedDoraCount()==2,"accepted concealed kan did not reveal its indicator");
        check(doraCodes(driver).containsAll(Set.of(22,31,34,35,36)),"concealed kan failed to retain old dora and add sou-five");
        check(!doraCodes(driver).contains(4),"concealed kan revealed the third indicator early");
        check(!before.contains(22),"a later indicator reveal mutated an earlier dora snapshot");

        // Mahjong Soul reveals an open/added kan's indicator only after its next discard.
        hands = players(4);
        var hand = new ArrayList<TheMahjongTile>();
        for (int rank=1; rank<=9; rank++) hand.add(tile(0,rank));
        hand.add(tile(3,1));hand.add(tile(4,1));
        var pon = new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,3,0);
        hands.set(0,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                hand,List.of(pon),List.of(),List.of()));
        driver = restored(round(hands,DORA,URA,1));
        var kakan = driver.legalActions(0).stream().filter(a -> a instanceof PlayerAction.Kakan).findFirst().orElseThrow();
        act(driver,0,kakan);
        passClaims(driver);
        check(driver.currentRound().pendingKanDoraReveals()==1 && !doraCodes(driver).contains(22),
                "added-kan declaration leaked the delayed indicator before its discard");
        act(driver,0,PlayerAction.Draw.INSTANCE);
        check(!doraCodes(driver).contains(22),"rinshan draw prematurely exposed a delayed kan indicator");
        act(driver,0,new PlayerAction.Discard(tile(4,2)));
        check(driver.currentRound().revealedDoraCount()==2 && doraCodes(driver).contains(22),
                "the post-kan discard did not refresh the matching tiles' appearance");
        check(!doraCodes(driver).contains(15),"kan reveal leaked the corresponding ura indicator during live play");
    }

    private static void winAndDrawVisibility() {
        for (int count : new int[]{3,4}) {
            for (var state : List.of(TheMahjongPlayer.RiichiState.NONE,
                    TheMahjongPlayer.RiichiState.RIICHI,TheMahjongPlayer.RiichiState.DOUBLE_RIICHI)) {
                var hands = players(count);
                var winner = new ArrayList<>(waiting(1));winner.add(tile(4,1));
                hands.set(0,riichi(player(winner),state));
                // A losing player's riichi must not reveal ura for a non-riichi winner.
                hands.set(1,riichi(hands.get(1),TheMahjongPlayer.RiichiState.RIICHI));
                var driver = restored(round(hands,DORA,URA,1));
                var drawn = driver.currentRound().exhaustiveDraw(count==3
                        ? TheMahjongRuleSet.mahjongSoulSanma() : TheMahjongRuleSet.mahjongSoul());
                check(!DoraTiles.uraVisible(drawn,new MatchPhase.RoundEnded(List.of())),"draw exposed ura for a riichi player");
                check(!DoraTiles.tiles(drawn,new MatchPhase.RoundEnded(List.of())).contains(15),
                        "draw highlighting disclosed the hidden ura identity");
                check(!doraCodes(driver).contains(15),"riichi hand was highlighted from an unrevealed ura indicator");
                var tsumo = driver.legalActions(0).stream().filter(a -> a instanceof PlayerAction.DeclareTsumo)
                        .findFirst().orElseThrow();
                act(driver,0,tsumo);
                check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"tsumo fixture did not resolve");
                boolean expected = state!=TheMahjongPlayer.RiichiState.NONE;
                check(DoraTiles.uraVisible(driver.currentRound(),driver.currentPhase())==expected,
                        "ura reveal must depend on the winner's riichi, including double riichi");
                check(doraCodes(driver).contains(15)==expected,"settlement ura highlighting disagrees with the winning player's riichi");
                check(!doraCodes(driver).contains(26),"settlement revealed an ura indicator beyond the public indicator count");
                check(!DoraTiles.tiles(driver.currentRound(),new MatchPhase.BetweenRounds()).contains(15),
                        "ura highlighting survived outside its result phase");
            }
        }

        var hands = players(4);
        var source = new ArrayList<>(waiting(0));source.add(tile(4,1));
        hands.set(0,player(source));
        hands.set(2,riichi(hands.get(2),TheMahjongPlayer.RiichiState.RIICHI));
        var driver = restored(round(hands,DORA,URA,1));
        act(driver,0,new PlayerAction.Discard(tile(4,1)));
        for (int seat : new int[]{1,2}) {
            var ron = driver.legalActions(seat).stream().filter(a -> a instanceof PlayerAction.DeclareRon)
                    .findFirst().orElseThrow();
            act(driver,seat,ron);
        }
        passClaims(driver);
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended && ended.winResults().size()==2,
                "double-ron fixture did not retain both winners");
        check(DoraTiles.uraVisible(driver.currentRound(),driver.currentPhase()) && doraCodes(driver).contains(15),
                "a second riichi winner in double ron did not reveal ura highlighting");
    }

    private static void terminalResultVisibility() {
        for(boolean declared:List.of(false,true)) {
            var hands=players(4);var winner=new ArrayList<>(waiting(1));winner.add(tile(4,1));
            hands.set(0,riichi(player(winner),declared?TheMahjongPlayer.RiichiState.RIICHI:TheMahjongPlayer.RiichiState.NONE));
            // A losing declaration still must not reveal ura for a non-riichi winner.
            hands.set(1,riichi(hands.get(1),TheMahjongPlayer.RiichiState.RIICHI));
            var driver=restored(round(hands,DORA,URA,1));
            var win=driver.legalActions(0).stream().filter(a->a instanceof PlayerAction.DeclareTsumo).findFirst().orElseThrow();
            act(driver,0,win);
            var display=new RoundResultDisplay();display.observe(driver.currentPhase());
            var before=DoraTiles.tiles(driver.currentRound(),driver.currentPhase());
            var terminal=new MatchPhase.MatchEnded();display.observe(terminal);
            check(display.phase(terminal) instanceof MatchPhase.RoundEnded,"terminal display lost the final hand result");
            check(DoraTiles.uraVisible(driver.currentRound(),display.phase(terminal))==declared,
                    "terminal result changed the publicly revealed ura boundary");
            check(DoraTiles.tiles(driver.currentRound(),display.phase(terminal)).equals(before),
                    "terminal result removed a final-hand dora highlight");
            var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);display.write(out);
            var reloaded=new RoundResultDisplay();
            reloaded.read(TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),out.buildResult()));
            check(DoraTiles.tiles(driver.currentRound(),reloaded.phase(terminal)).equals(before),
                    "world reload removed final-hand dora highlight");
            reloaded.observe(new MatchPhase.AwaitingDraw(0,false));
            check(reloaded.phase(terminal) instanceof MatchPhase.MatchEnded,
                    "a previous hand's result leaked into a new match");
        }
    }

    private static byte[] displayPacket(int code, boolean sichuan, boolean dora, String expectedPath, boolean foil) {
        var stack = PortViews.tileStack(code, sichuan, dora);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            EntityDataSerializers.ITEM_STACK.codec().encode(buffer, stack);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            var decoded = EntityDataSerializers.ITEM_STACK.codec().decode(buffer);
            check(Identifier.fromNamespaceAndPath("riichi_mahjong", expectedPath).equals(decoded.get(DataComponents.ITEM_MODEL)),
                    "wrong item-display model for " + expectedPath);
            check(decoded.hasFoil() == foil, "wrong enchantment appearance for " + expectedPath);
            if (!foil) check(!decoded.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE),
                    "unexpected glint override in the packet for " + expectedPath);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void tileDisplayPackets() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());

        displayPacket(33, false, true, "bridge/dora/tile_dragon_red", false);
        displayPacket(33, false, false, "bridge/tile_dragon_red", false);
        byte[] back = displayPacket(-1, false, false, "bridge/tile_back", false);
        for (boolean sichuan : List.of(false, true)) {
            check(Arrays.equals(back, displayPacket(-1, sichuan, true, "bridge/tile_back", false)),
                    "a concealed tile leaked bonus or rule information in its packet");
        }
        displayPacket(33, true, false, "bridge/tile_joker", true);
        displayPacket(37, false, true, "bridge/tile_joker", false);
    }

    public static void main(String[] args) {
        cyclesAndHiddenIndicators();
        acceptedKanReveals();
        winAndDrawVisibility();
        terminalResultVisibility();
        tileDisplayPackets();
        System.out.println("PASS: 3p/4p dora cycles, red fives, concealed/delayed kan reveals and hidden indicator boundaries");
        System.out.println("PASS: riichi/double-riichi and double-ron ura reveal, non-riichi wins and draws preserve hidden ura");
        System.out.println("PASS: terminal result and reload retain public ura; encoded dora use gold models and all concealed backs are byte-identical");
    }
}
