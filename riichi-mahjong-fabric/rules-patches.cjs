// Keep upstream/ byte-for-byte; apply small, checked fixes to the compiled driver.
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
