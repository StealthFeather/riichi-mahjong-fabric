// Pause riichi before its first draw; Sichuan retains its existing exchange/dingque state.
const fs=require('node:fs'),p=require('node:path');
module.exports=source=>{
 const replace=(from,to)=>{if(source.split(from).length!==2)throw Error('Exchange patch no longer matches: '+from);source=source.replace(from,to);};
 replace('    private boolean animationsEnabled;',
   '    private com.themahjong.variants.ExchangeThreeState exchangeState;\n    private boolean animationsEnabled;');
 const init='        initSichuanRound();';
 if(source.split(init).length!==3)throw Error('Exchange round initialization no longer matches');
 source=source.replaceAll(init,init+'\n        initExchangeRound();');
 replace('        this.phase = Objects.requireNonNull(snapshot.phase(), "snapshot.phase");',
   '        this.phase = Objects.requireNonNull(snapshot.phase(), "snapshot.phase");\n        if (phase instanceof MatchPhase.Dealing) initExchangeRound();');
 replace('    public List<PlayerAction> legalActions(int seat) {',
   '    public List<PlayerAction> legalActions(int seat) {\n        if (exchangeInProgress()) return List.of();');
 replace('        if (rules().sichuan()) return advanceSichuan(deltaSeconds, polledThisFrame);',
   '        if (exchangeInProgress()) return advanceExchange();\n        if (rules().sichuan()) return advanceSichuan(deltaSeconds, polledThisFrame);');
 for(const [method,action] of [['toggleExchangeTile(int seat, TheMahjongTile tile)','exchangeState.toggle(currentRound(), seat, tile)'],['clearExchangeSelection(int seat)','exchangeState.clear(seat)'],['confirmExchange(int seat)','exchangeState.confirm(currentRound(), seat)']]){
  replace('    public boolean '+method+' {','    public boolean '+method+' {\n        if (exchangeState != null) {\n            if (!canConfigureExchange() || !'+action+') return false;\n            if (exchangeState.ready()) match = matchWithRound(exchangeState.exchange(currentRound()));\n            else touchExchangeRound();\n            stateObserver.accept(this);\n            return true;\n        }');
 }
 // Finish the atomic swap before returning control to the table's transient animation.
 // Otherwise one advance() can exchange and draw before the renderer can capture the thirteen-tile hands.
 replace('            int previousWins = sichuanState == null ? 0 : sichuanState.wins().size();',
   '            boolean exchangeWasPending = exchangeInProgress() || sichuanState != null && sichuanState.stage() == com.themahjong.sichuan.SichuanState.Stage.EXCHANGE;\n            int previousWins = sichuanState == null ? 0 : sichuanState.wins().size();');
 replace('            if (sichuanState != null && sichuanState.wins().size() > previousWins) return;',
   '            if (exchangeWasPending && !exchangeInProgress() && (sichuanState == null || sichuanState.stage() != com.themahjong.sichuan.SichuanState.Stage.EXCHANGE)) return;\n            if (sichuanState != null && sichuanState.wins().size() > previousWins) return;');
 const methods=fs.readFileSync(p.join(__dirname,'src/rules/exchange-driver.inc'),'utf8');
 replace('    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {',methods+'\n    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {');
 return source;
};
