package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class WinCalculator {
    private static final int MANGAN_BASIC    = 2000;
    private static final int HANEMAN_BASIC   = 3000;
    private static final int BAIMAN_BASIC    = 4000;
    private static final int SANBAIMAN_BASIC = 6000;
    private static final int YAKUMAN_BASIC   = 8000;

    private WinCalculator() {}

    public static WinResult calculateBest(
            List<HandShape> decompositions,
            WinContext ctx,
            List<TheMahjongTile> allWinnerTiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int playerCount,
            int winnerSeat,
            int loserSeat,
            int dealerSeat,
            int honba,
            int riichiSticks,
            TheMahjongRuleSet rules) {
        return calculateBest(decompositions, ctx, allWinnerTiles, doraIndicators, uraDoraIndicators,
                playerCount, winnerSeat, loserSeat, dealerSeat, honba, riichiSticks, rules, 0);
    }

    public static WinResult calculateBest(
            List<HandShape> decompositions,
            WinContext ctx,
            List<TheMahjongTile> allWinnerTiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int playerCount,
            int winnerSeat,
            int loserSeat,
            int dealerSeat,
            int honba,
            int riichiSticks,
            TheMahjongRuleSet rules,
            int winnerKitaCount) {
        int doraCount = countDora(allWinnerTiles, doraIndicators, uraDoraIndicators, playerCount);
        if (rules.kitaCountsAsDora()) doraCount += winnerKitaCount;
        boolean handClosed = isHandClosed(decompositions);

        WinResult best = null;
        for (HandShape shape : decompositions) {
            List<Yakuman> yakumanList = Yakuman.check(shape, ctx, rules);
            List<NonYakuman> yakuList;
            int han;
            int fu = FuCalculator.calculate(shape, ctx, rules);

            if (!yakumanList.isEmpty()) {
                yakuList = List.of();

                if (yakumanList.size() == 1 && yakumanList.get(0) == Yakuman.RENHOU) {
                    han = 5;
                } else {
                    han = yakumanList.size() * 13;
                }
            } else {
                yakuList = NonYakuman.check(shape, ctx);
                if (yakuList.isEmpty()) continue;
                han = 0;
                for (NonYakuman y : yakuList) {
                    han += handClosed ? y.closedHan() : y.openHan().orElse(0);
                }
                han += doraCount;
            }

            int yakumanMultiplier = (yakumanList.isEmpty()
                    || (yakumanList.size() == 1 && yakumanList.get(0) == Yakuman.RENHOU))
                    ? 1
                    : yakumanList.size();

            OptionalInt paoSeat = findPaoSeat(shape, yakumanList, rules);
            List<Integer> deltas = computeDeltas(
                    han, fu, ctx.tsumo(), ctx.dealer(),
                    playerCount, winnerSeat, loserSeat, dealerSeat, honba, riichiSticks,
                    rules.kiriageMangan(), paoSeat, yakumanMultiplier, rules.tsumoLoss());
            WinResult candidate = new WinResult(yakuList, yakumanList, han, fu, doraCount, deltas);
            if (best == null || winnerGain(candidate, winnerSeat) > winnerGain(best, winnerSeat)) {
                best = candidate;
            }
        }

        if (best == null) {
            return new WinResult(List.of(), List.of(), 0, 0, doraCount, zeroDeltas(playerCount));
        }
        return best;
    }

    private static List<Integer> computeDeltas(
            int han, int fu, boolean isTsumo, boolean isDealer,
            int playerCount, int winnerSeat, int loserSeat, int dealerSeat,
            int honba, int riichiSticks, boolean kiriageMangan, OptionalInt paoSeat,
            int yakumanMultiplier, boolean tsumoLoss) {
        int[] deltas = new int[playerCount];
        int basic = basicPoints(han, fu, kiriageMangan) * yakumanMultiplier;

        int ronHonbaPerStick = (playerCount - 1) * 100;

        if (paoSeat.isPresent()) {
            int pao = paoSeat.getAsInt();
            if (isTsumo) {
                int total = 0;
                if (isDealer) {
                    total = (roundUp100(basic * 2) + honba * 100) * (playerCount - 1);
                } else {
                    int dealerPays    = roundUp100(basic * 2) + honba * 100;
                    int nonDealerPays = roundUp100(basic)     + honba * 100;
                    for (int i = 0; i < playerCount; i++) {
                        if (i == winnerSeat) continue;
                        total += (i == dealerSeat) ? dealerPays : nonDealerPays;
                    }
                }
                deltas[pao] -= total;
                deltas[winnerSeat] += total;
            } else {
                int total = roundUp100(basic * (isDealer ? 6 : 4));
                int paoShare = roundUp100(total / 2);
                int discarderShare = total - paoShare + honba * ronHonbaPerStick;
                if (pao == loserSeat) {
                    deltas[loserSeat] -= total + honba * ronHonbaPerStick;
                } else {
                    deltas[pao]       -= paoShare;
                    deltas[loserSeat] -= discarderShare;
                }
                deltas[winnerSeat] += total + honba * ronHonbaPerStick;
            }
        } else if (isTsumo) {
            int sanmaShareEach = 0;
            if (playerCount == 3 && !tsumoLoss) {
                int northWouldPay = isDealer ? (basic * 2) : basic;
                sanmaShareEach = roundUp100(northWouldPay / 2);
            }
            if (isDealer) {
                int each = roundUp100(basic * 2) + honba * 100 + sanmaShareEach;
                for (int i = 0; i < playerCount; i++) {
                    if (i != winnerSeat) {
                        deltas[i] -= each;
                        deltas[winnerSeat] += each;
                    }
                }
            } else {
                int dealerPays    = roundUp100(basic * 2) + honba * 100 + sanmaShareEach;
                int nonDealerPays = roundUp100(basic)     + honba * 100 + sanmaShareEach;
                for (int i = 0; i < playerCount; i++) {
                    if (i == winnerSeat) continue;
                    int pays = (i == dealerSeat) ? dealerPays : nonDealerPays;
                    deltas[i] -= pays;
                    deltas[winnerSeat] += pays;
                }
            }
        } else {
            int pays = roundUp100(basic * (isDealer ? 6 : 4)) + honba * ronHonbaPerStick;
            deltas[loserSeat] -= pays;
            deltas[winnerSeat] += pays;
        }

        deltas[winnerSeat] += riichiSticks * 1000;

        List<Integer> result = new ArrayList<>(playerCount);
        for (int d : deltas) result.add(d);
        return List.copyOf(result);
    }

    static int basicPoints(int han, int fu, boolean kiriageMangan) {
        if (han >= 13) return YAKUMAN_BASIC;
        if (han >= 11) return SANBAIMAN_BASIC;
        if (han >= 8)  return BAIMAN_BASIC;
        if (han >= 6)  return HANEMAN_BASIC;
        if (kiriageMangan && ((han == 4 && fu == 30) || (han == 3 && fu == 60))) return MANGAN_BASIC;
        return Math.min(fu * (1 << (han + 2)), MANGAN_BASIC);
    }

    private static int roundUp100(int x) {
        return ((x + 99) / 100) * 100;
    }

    static OptionalInt findPaoSeat(HandShape shape, List<Yakuman> yakumanList, TheMahjongRuleSet rules) {
        if (!(shape instanceof HandShape.Standard s)) return OptionalInt.empty();

        boolean hasDaisangen = yakumanList.contains(Yakuman.DAISANGEN);
        boolean hasDaisuushi = yakumanList.contains(Yakuman.DAISUUSHI);
        boolean hasSuukantsu = rules.paoOnSuukantsu() && yakumanList.contains(Yakuman.SUUKANTSU);

        if (!hasDaisangen && !hasDaisuushi && !hasSuukantsu) return OptionalInt.empty();

        OptionalInt result = OptionalInt.empty();

        if (hasDaisangen) result = lastHonorGroupPaoSeat(s.melds(), TheMahjongTile.Suit.DRAGON);
        if (hasDaisuushi) result = lastHonorGroupPaoSeat(s.melds(), TheMahjongTile.Suit.WIND);
        if (hasSuukantsu) result = lastKanPaoSeat(s.melds());

        return result;
    }

    private static OptionalInt lastHonorGroupPaoSeat(List<TheMahjongMeld> melds, TheMahjongTile.Suit suit) {
        int requiredRanks = (suit == TheMahjongTile.Suit.DRAGON) ? 3 : 4;
        java.util.Set<Integer> seenRanks = new java.util.HashSet<>();
        int lastSeat = -1;
        for (TheMahjongMeld m : melds) {
            if (m instanceof TheMahjongMeld.Chi) continue;
            if (m.tiles().isEmpty() || m.tiles().get(0).suit() != suit) continue;
            seenRanks.add(m.tiles().get(0).rank());
            lastSeat = meldPaoSeat(m);
        }
        if (seenRanks.size() < requiredRanks) return OptionalInt.empty();
        return lastSeat >= 0 ? OptionalInt.of(lastSeat) : OptionalInt.empty();
    }

    private static OptionalInt lastKanPaoSeat(List<TheMahjongMeld> melds) {
        int seat = -1;
        for (TheMahjongMeld m : melds) {
            if (m instanceof TheMahjongMeld.Daiminkan || m instanceof TheMahjongMeld.Kakan || m instanceof TheMahjongMeld.Ankan) {
                seat = meldPaoSeat(m);
            }
        }
        return seat >= 0 ? OptionalInt.of(seat) : OptionalInt.empty();
    }

    private static int meldPaoSeat(TheMahjongMeld m) {
        if (m instanceof TheMahjongMeld.Pon p)       return p.sourceSeat();
        if (m instanceof TheMahjongMeld.Daiminkan d) return d.sourceSeat();
        if (m instanceof TheMahjongMeld.Kakan k)     return k.upgradedFrom().sourceSeat();
        return -1;
    }

    private static int countDora(
            List<TheMahjongTile> tiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int playerCount) {
        int count = 0;
        for (TheMahjongTile indicator : doraIndicators) {
            TheMahjongTile dora = doraFromIndicator(indicator, playerCount);
            for (TheMahjongTile t : tiles) {
                if (t.matchesSuitRank(dora)) count++;
            }
        }
        for (TheMahjongTile t : tiles) {
            if (t.redDora()) count++;
        }
        for (TheMahjongTile indicator : uraDoraIndicators) {
            TheMahjongTile ura = doraFromIndicator(indicator, playerCount);
            for (TheMahjongTile t : tiles) {
                if (t.matchesSuitRank(ura)) count++;
            }
        }
        return count;
    }

    static TheMahjongTile doraFromIndicator(TheMahjongTile indicator) {
        return doraFromIndicator(indicator, 4);
    }

    static TheMahjongTile doraFromIndicator(TheMahjongTile indicator, int playerCount) {
        if (playerCount == 3 && indicator.suit() == TheMahjongTile.Suit.MANZU) {
            int wrapped = (indicator.rank() == 1) ? 9 : 1;
            return new TheMahjongTile(TheMahjongTile.Suit.MANZU, wrapped, false);
        }
        int next = (indicator.rank() % indicator.suit().maxRank()) + 1;
        return new TheMahjongTile(indicator.suit(), next, false);
    }

    private static boolean isHandClosed(List<HandShape> decompositions) {
        for (HandShape shape : decompositions) {
            if (shape instanceof HandShape.Standard s && !s.closed()) return false;
        }
        return true;
    }

    private static int winnerGain(WinResult r, int winnerSeat) {
        return r.pointDeltas().get(winnerSeat);
    }

    private static List<Integer> zeroDeltas(int playerCount) {
        List<Integer> z = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) z.add(0);
        return List.copyOf(z);
    }
}
