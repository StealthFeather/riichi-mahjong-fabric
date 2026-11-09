// Keep upstream/ byte-for-byte; apply small, checked fixes to compiled rule sources.
module.exports=function patchDriver(source){
 const replace=(from,to)=>{
  if(source.split(from).length!==2)throw Error('Upstream driver patch no longer matches: '+from);
  source=source.replace(from,to);
 };
 replace('if (!c.pendingSeats().contains(seat)) return List.of();',
         'if (!c.pendingSeats().contains(seat) || claimDecisions.containsKey(seat)) return List.of();');
 replace('        commitPendingRiichi();\n\n        for (int offset',
         '        commitPendingRiichi();\n        round = currentRound(); // Preserve the accepted deposit when a declaration discard is called.\n\n        for (int offset');
 replace('        claimDecisions.remove(seat);',
         '        // Keep an already accepted claim when its human controller leaves.');
 return source;
};

module.exports.round=function patchRound(source){
 const from=`        List<TheMahjongPlayer> newPlayers = players;
        if (rules.nagashiManganAllowed()) {`;
 const to=`        List<TheMahjongPlayer> newPlayers = players;
        boolean nagashiSettled = false;
        if (rules.nagashiManganAllowed()) {`;
 if(source.split(from).length!==2)throw Error('Upstream round patch no longer matches: exhaustive draw entry');
 source=source.replace(from,to);
 const nagashi=`                newPlayers = applyNagashiManganDeltas(winners);
            }
        }
        return new TheMahjongRound(`;
 const settlement=`                newPlayers = applyNagashiManganDeltas(winners);
                nagashiSettled = true;
            }
        }
        // Exhaustive draws settle directly into player points, as nagashi does.
        // Abortive draws have their own path and must never pay noten penalties.
        if (!nagashiSettled) {
            newPlayers = com.themahjong.compat.DrawSettlement.applyNotenPayments(players);
        }
        return new TheMahjongRound(`;
 if(source.split(nagashi).length!==2)throw Error('Upstream round patch no longer matches: nagashi settlement');
 return source.replace(nagashi,settlement);
};
