// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.cuterenderer.InteractKey;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.themahjong.driver.MahjongHumanPlayer;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MahjongTableHumanPlayer extends MahjongHumanPlayer {
    private UUID occupant;

    private boolean drawnTileDeliveryAttempted;

    private boolean drawnTileDelivered;

    private TheMahjongTile pendingConsume;

    private boolean riichiPending;

    private transient double riichiAutoDiscardElapsed;

    private transient SkipConfirmation skipConfirmation;
    private transient WinOpportunity skippedTsumo;

    private record WinOpportunity(TheMahjongDriver driver, TheMahjongRound round,
                                  MatchPhase phase, int seat, UUID occupant,
                                  List<PlayerAction> wins) {
        boolean matches(WinOpportunity other) {
            return other != null && driver == other.driver && round == other.round
                    && phase == other.phase && seat == other.seat
                    && java.util.Objects.equals(occupant, other.occupant) && wins.equals(other.wins);
        }
    }

    private record SkipConfirmation(UUID token, WinOpportunity opportunity, boolean tsumo) {}

    public sealed interface TableButton {
        record SubmitAction(PlayerAction action) implements TableButton {}
        record RiichiToggle(boolean active) implements TableButton {}
        record SkipTsumo() implements TableButton {}
        record ConfirmSkip(UUID token) implements TableButton {}
        record CancelSkip(UUID token) implements TableButton {}
    }

    public Optional<UUID> occupant() {
        return Optional.ofNullable(occupant);
    }

    public void setOccupant(UUID uuid) {
        if (!java.util.Objects.equals(occupant, uuid)) clearWinSkipState();
        this.occupant = uuid;
    }

    public void clearOccupant() {
        clearWinSkipState();
        this.occupant = null;
    }

    public void validateBeforeAdvance(ServerLevel level) {
        if (!hasQueued()) return;
        if (!drawnTileDelivered || pendingConsume == null || occupant == null) return;
        ServerPlayer sp = level.getPlayerByUUID(occupant) instanceof ServerPlayer s ? s : null;
        Item item = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(pendingConsume));
        if (sp == null || item == null || !inventoryContains(sp.getInventory(), item)) {
            clearQueued();
            if (sp != null) hint(sp, "riichi_mahjong.hint.player.return_drawn");
        }
    }

    public boolean tickAfterAdvance(ServerLevel level, MahjongTableBlockEntity table,
                                    int seatIndex, MatchPhase phase) {
        // Drawing to a real held item would broadcast the tile through equipment packets.
        // The vanilla-client bridge displays it only in this player's private virtual hand.
        drawnTileDeliveryAttempted=false;drawnTileDelivered=false;pendingConsume=null;
        boolean ownTurn=phase instanceof MatchPhase.AwaitingDiscard d&&d.seat()==seatIndex;
        var driver=table.driver();
        boolean changed=driver!=null&&refreshWinSkipState(driver,seatIndex,driver.legalActions(seatIndex));
        if(!ownTurn&&riichiPending){riichiPending=false;changed=true;}
        if(ownTurn)maybeAutoQueueRiichiDiscard(level,table,seatIndex);
        else riichiAutoDiscardElapsed=0;
        return changed;
    }

    private static final double RIICHI_AUTO_DISCARD_SECONDS = 0.3;
    private static final double SECONDS_PER_TICK = 1.0 / 20.0;

    private void maybeAutoQueueRiichiDiscard(ServerLevel level,
                                             MahjongTableBlockEntity table, int seatIndex) {
        if (hasQueued()) { riichiAutoDiscardElapsed = 0.0; return; }
        TheMahjongDriver driver = table.driver();
        if (driver == null) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        TheMahjongRound round = roundOpt.get();
        if (seatIndex < 0 || seatIndex >= round.players().size()) return;
        if (!round.players().get(seatIndex).riichi()) {
            riichiAutoDiscardElapsed = 0.0;
            return;
        }
        if (!(round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn)) {
            riichiAutoDiscardElapsed = 0.0;
            return;
        }
        java.util.List<PlayerAction> legal = driver.legalActions(seatIndex);
        if (legal.isEmpty()) return;
        for (PlayerAction a : legal) {
            // A confirmed skipped tsumo no longer blocks the usual riichi
            // auto-discard. An unconfirmed or cancelled win still pauses it.
            if (a instanceof PlayerAction.DeclareTsumo && skippedTsumo != null) continue;
            if (!(a instanceof PlayerAction.Discard)) return;
        }
        riichiAutoDiscardElapsed += SECONDS_PER_TICK;
        if (riichiAutoDiscardElapsed < RIICHI_AUTO_DISCARD_SECONDS) return;
        if (occupant == null) return;
        if (!(level.getPlayerByUUID(occupant) instanceof ServerPlayer sp)) return;

        tryDiscard(sp, drawn.tile(), driver, seatIndex);
    }

    private static TheMahjongTile currentDrawnTile(MahjongTableBlockEntity table) {
        if (table.driver() == null) return null;
        var roundOpt = table.driver().match().currentRound();
        if (roundOpt.isEmpty()) return null;
        if (roundOpt.get().activeTile() instanceof TheMahjongRound.ActiveTile.Drawn d) {
            return d.tile();
        }
        return null;
    }

    private boolean changedSince(boolean priorAttempted, boolean priorDelivered,
                                  @org.jspecify.annotations.Nullable TheMahjongTile priorPending,
                                  boolean priorRiichi) {
        return priorAttempted != drawnTileDeliveryAttempted
                || priorDelivered != drawnTileDelivered
                || priorRiichi != riichiPending
                || !java.util.Objects.equals(priorPending, pendingConsume);
    }

    public boolean drawnTileDelivered() { return drawnTileDelivered; }

    public void writeNbt(ValueOutput out) {
        out.putBoolean("DeliveryAttempted", drawnTileDeliveryAttempted);
        out.putBoolean("Delivered", drawnTileDelivered);
        out.putBoolean("RiichiPending", riichiPending);
        if (pendingConsume != null) {
            out.putString("PendingConsumeSuit", pendingConsume.suit().name());
            out.putInt("PendingConsumeRank", pendingConsume.rank());
            out.putBoolean("PendingConsumeRedDora", pendingConsume.redDora());
        }
    }

    public void readNbt(ValueInput in) {
        // Confirmation belongs to a live opportunity, never to persisted seat state.
        clearWinSkipState();
        drawnTileDeliveryAttempted = in.getBooleanOr("DeliveryAttempted", false);
        drawnTileDelivered = in.getBooleanOr("Delivered", false);
        riichiPending = in.getBooleanOr("RiichiPending", false);
        pendingConsume = in.getString("PendingConsumeSuit")
                .map(name -> {
                    try {
                        return new TheMahjongTile(
                                TheMahjongTile.Suit.valueOf(name),
                                in.getIntOr("PendingConsumeRank", 0),
                                in.getBooleanOr("PendingConsumeRedDora", false));
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static boolean tryGiveToPlayer(ServerPlayer player, ItemStack stack,
                                           Item targetItem, boolean mainHand) {
        Inventory inv = player.getInventory();
        if (mainHand) {
            int slot = inv.getSelectedSlot();
            if (!inv.getItem(slot).isEmpty()) return false;
            inv.setItem(slot, stack);
            return true;
        }
        if (!hasRoomForOneItem(inv, targetItem)) return false;
        return inv.add(stack) && stack.isEmpty();
    }

    private static boolean hasRoomForOneItem(Inventory inv, Item targetItem) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack here = inv.getItem(slot);
            if (here.isEmpty()) return true;
            if (here.getItem() == targetItem
                    && here.getCount() < here.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public void onCuteClick(InteractKey key, TheMahjongDriver driver, int seatIndex,
                            ServerPlayer sender) {
        if (!(key instanceof InteractKey.SeatSlot ss)) return;
        if (ss.seat() != seatIndex) return;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return;
        if (ss.area() == InteractKey.SeatSlot.AREA_HAND) {
            List<TheMahjongTile> hand = round.handDisplayOrder(seatIndex);
            int idx = ss.index();
            if (idx < 0 || idx >= hand.size()) return;
            tryDiscard(sender, hand.get(idx), driver, seatIndex);
        } else if (ss.area() == InteractKey.SeatSlot.AREA_BUTTON) {
            List<TableButton> buttons = tableButtons(driver, seatIndex, riichiPending);
            int idx = ss.index();
            if (idx < 0 || idx >= buttons.size()) return;
            selectTableButton(buttons.get(idx), driver, seatIndex, sender);
        }
    }

    /** Select a concrete, current button; token-bearing confirmation buttons expire safely. */
    public boolean selectTableButton(TableButton button, TheMahjongDriver driver,
                                     int seatIndex, ServerPlayer sender) {
        if (hasQueued() || !tableButtons(driver, seatIndex, riichiPending).contains(button)) return false;
        if (button instanceof TableButton.ConfirmSkip confirm) {
            var pending=skipConfirmation;
            if(pending==null||!pending.token().equals(confirm.token()))return false;
            skipConfirmation=null;
            if(pending.tsumo()){
                // Tsumo is offered in the discard phase, which has no legal Pass.
                // Hide this opportunity and leave every ordinary discard available.
                skippedTsumo=pending.opportunity();
                return true;
            }
            return tryQueueButtonAction(sender,PlayerAction.Pass.INSTANCE,driver,seatIndex);
        }
        if (button instanceof TableButton.CancelSkip cancel) {
            if(skipConfirmation==null||!skipConfirmation.token().equals(cancel.token()))return false;
            skipConfirmation=null;
            return true;
        }
        if (button instanceof TableButton.SkipTsumo) {
            return requestWinSkip(driver,seatIndex,true);
        }
        if (button instanceof TableButton.RiichiToggle) {
            riichiPending=!riichiPending;
            return true;
        }
        var action=((TableButton.SubmitAction)button).action();
        if(action instanceof PlayerAction.Pass && hasClaimWin(driver.legalActions(seatIndex)))
            return requestWinSkip(driver,seatIndex,false);
        return tryQueueButtonAction(sender,action,driver,seatIndex);
    }

    private boolean tryQueueButtonAction(ServerPlayer sender, PlayerAction action,
                                         TheMahjongDriver driver, int seatIndex) {
        if(hasQueued()||!driver.legalActions(seatIndex).contains(action))return false;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return false;
        boolean ourDiscardTurn =
                driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad
                        && ad.seat() == seatIndex;
        if (ourDiscardTurn
                && drawnTileDelivered
                && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn) {
            Item drawnItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(drawn.tile()));
            if (drawnItem != null && !inventoryContains(sender.getInventory(), drawnItem)) {
                hint(sender, "riichi_mahjong.hint.player.retrieve_drawn_first");
                return false;
            }
            pendingConsume = drawn.tile();
        }
        clearWinSkipState();
        queue(action);
        return true;
    }

    public boolean riichiPending() { return riichiPending; }

    private static boolean hasClaimWin(List<PlayerAction> legal) {
        return legal.stream().anyMatch(a->a instanceof PlayerAction.DeclareRon
                ||a instanceof PlayerAction.DeclareChankan);
    }

    private WinOpportunity winOpportunity(TheMahjongDriver driver,int seat,List<PlayerAction> legal) {
        if(seat<0||seat>=driver.match().playerCount()||driver.playerAt(seat)!=this)return null;
        var round=driver.match().currentRound().orElse(null);
        var wins=legal.stream().filter(a->a instanceof PlayerAction.DeclareRon
                ||a instanceof PlayerAction.DeclareTsumo||a instanceof PlayerAction.DeclareChankan).toList();
        return round==null||wins.isEmpty()?null:new WinOpportunity(driver,round,driver.currentPhase(),seat,occupant,wins);
    }

    private boolean refreshWinSkipState(TheMahjongDriver driver,int seat,List<PlayerAction> legal) {
        var current=winOpportunity(driver,seat,legal);
        boolean changed=false;
        if(skipConfirmation!=null&&!skipConfirmation.opportunity().matches(current)){skipConfirmation=null;changed=true;}
        if(skippedTsumo!=null&&!skippedTsumo.matches(current)){skippedTsumo=null;changed=true;}
        return changed;
    }

    private void clearWinSkipState() {
        skipConfirmation=null;
        skippedTsumo=null;
    }

    private boolean requestWinSkip(TheMahjongDriver driver,int seat,boolean tsumo) {
        var legal=driver.legalActions(seat);
        refreshWinSkipState(driver,seat,legal);
        var opportunity=winOpportunity(driver,seat,legal);
        boolean available=tsumo?legal.stream().anyMatch(a->a instanceof PlayerAction.DeclareTsumo):
                legal.contains(PlayerAction.Pass.INSTANCE)&&hasClaimWin(legal);
        if(opportunity==null||!available)return false;
        if(skipConfirmation==null)skipConfirmation=new SkipConfirmation(UUID.randomUUID(),opportunity,tsumo);
        return true;
    }

    public static List<TableButton> tableButtons(TheMahjongDriver driver, int seat, boolean riichiPending) {
        List<PlayerAction> legal = driver.legalActions(seat);
        var human=driver.playerAt(seat) instanceof MahjongTableHumanPlayer h?h:null;
        boolean hideTsumo=false;
        if(human!=null){
            human.refreshWinSkipState(driver,seat,legal);
            if(human.hasQueued())return List.of();
            if(human.skipConfirmation!=null){
                var token=human.skipConfirmation.token();
                return List.of(new TableButton.ConfirmSkip(token),new TableButton.CancelSkip(token));
            }
            hideTsumo=human.skippedTsumo!=null;
        }
        boolean riichiAvailable = false;
        for (PlayerAction a : legal) {
            if (a instanceof PlayerAction.DiscardWithRiichi) { riichiAvailable = true; break; }
        }
        List<TableButton> out = new ArrayList<>();
        if (riichiAvailable) {
            out.add(new TableButton.RiichiToggle(riichiPending));
        }
        for (PlayerAction a : buttonEligibleActions(legal)) {
            if(hideTsumo&&a instanceof PlayerAction.DeclareTsumo)continue;
            out.add(new TableButton.SubmitAction(a));
        }
        if(!hideTsumo&&legal.stream().anyMatch(a->a instanceof PlayerAction.DeclareTsumo))out.add(new TableButton.SkipTsumo());
        return out;
    }

    public static List<PlayerAction> buttonEligibleActions(List<PlayerAction> legal) {
        List<PlayerAction> out = new ArrayList<>(legal.size());
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareRon) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareTsumo) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareChankan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Chi) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Pon) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Daiminkan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Ankan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Kakan) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.DeclareKita) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.KyuushuAbort) out.add(a);
        for (PlayerAction a : legal) if (a instanceof PlayerAction.Pass) out.add(a);
        return out;
    }

    public void onTableRightClick(TheMahjongDriver driver, MatchPhase phase, int seatIndex,
                                  ServerPlayer sender) {
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (phase instanceof MatchPhase.AwaitingDiscard ad && ad.seat() == seatIndex) {
            if (round != null
                    && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn) {
                tryDiscard(sender, drawn.tile(), driver, seatIndex);
            } else {
                hint(sender, "riichi_mahjong.hint.player.choose_discard");
            }
        } else if (phase instanceof MatchPhase.AwaitingClaims) {
            // A table/block fallback may open the prompt, but only its explicit
            // token-bearing confirmation button may ever accept a skipped win.
            selectTableButton(new TableButton.SubmitAction(PlayerAction.Pass.INSTANCE),driver,seatIndex,sender);
        } else if (phase instanceof MatchPhase.AwaitingDiscard
                || phase instanceof MatchPhase.AwaitingDraw) {
            hint(sender, "riichi_mahjong.hint.player.wait_turn");
        }
    }

    private void tryDiscard(ServerPlayer sender, TheMahjongTile tile,
                            TheMahjongDriver driver, int seatIndex) {
        if(hasQueued())return;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) {
            hint(sender, "riichi_mahjong.hint.player.wait_next_round");
            return;
        }

        if (!(driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad)
                || ad.seat() != seatIndex) {
            hint(sender, "riichi_mahjong.hint.player.wait_turn");
            return;
        }

        PlayerAction action = null;
        for (PlayerAction a : driver.legalActions(seatIndex)) {
            if (riichiPending) {
                if (a instanceof PlayerAction.DiscardWithRiichi r2 && r2.tile().equals(tile)) {
                    action = a;
                    break;
                }
            } else {
                if (a instanceof PlayerAction.Discard d2 && d2.tile().equals(tile)) {
                    action = a;
                    break;
                }
            }
        }
        if (action == null) {
            hint(sender, riichiPending
                    ? "riichi_mahjong.hint.player.not_tenpai_for_riichi"
                    : "riichi_mahjong.hint.player.illegal_discard");
            return;
        }
        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn d && drawnTileDelivered) {
            Item drawnItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(d.tile()));
            if (drawnItem == null) return;
            if (!inventoryContains(sender.getInventory(), drawnItem)) {
                hint(sender, "riichi_mahjong.hint.player.retrieve_drawn_to_discard");
                return;
            }
            pendingConsume = d.tile();
        }
        clearWinSkipState();
        queue(action);
    }

    private static void hint(ServerPlayer p, net.minecraft.network.chat.Component msg) {
        p.sendSystemMessage(msg, true);
    }

    private static void hint(ServerPlayer p, String key) {
        hint(p, net.minecraft.network.chat.Component.translatable(key));
    }

    private static boolean inventoryContains(Inventory inv, Item targetItem) {
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack here = inv.getItem(slot);
            if (!here.isEmpty() && here.getItem() == targetItem) return true;
        }
        return false;
    }

    private void consumeFromOccupant(ServerLevel level, TheMahjongTile tile) {
        if (occupant == null) return;
        if (!(level.getPlayerByUUID(occupant) instanceof ServerPlayer sp)) return;
        Item targetItem = MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(tile));
        if (targetItem == null) return;
        Inventory inv = sp.getInventory();
        ItemStack selected = inv.getItem(inv.getSelectedSlot());
        if (!selected.isEmpty() && selected.getItem() == targetItem) {
            selected.shrink(1);
            if (selected.isEmpty()) inv.setItem(inv.getSelectedSlot(), ItemStack.EMPTY);
            return;
        }
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack here = inv.getItem(slot);
            if (here.isEmpty() || here.getItem() != targetItem) continue;
            here.shrink(1);
            if (here.isEmpty()) inv.setItem(slot, ItemStack.EMPTY);
            return;
        }
    }
}
