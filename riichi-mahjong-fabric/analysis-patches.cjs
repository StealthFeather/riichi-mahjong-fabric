// Observe accepted legal decisions before execution without wrapping or replacing seat controllers.
module.exports=function patchAnalysisDriver(source){
 const replace=(from,to)=>{
  if(source.split(from).length!==2)throw Error('Analysis driver patch no longer matches: '+from);
  source=source.replace(from,to);
 };
 replace('    private final Map<Integer, PlayerAction> submitted = new HashMap<>();',
         `    private final Map<Integer, PlayerAction> submitted = new HashMap<>();

    private java.util.function.BiConsumer<DecisionRequest, PlayerAction> actionObserver = (request, action) -> {};

    /** Called once for each legal non-null decision, with the state immediately before its execution. */
    public void setActionObserver(java.util.function.BiConsumer<DecisionRequest, PlayerAction> observer) {
        actionObserver = Objects.requireNonNull(observer, "observer");
    }`);
 replace(`    private PlayerAction pollSeat(int seat, double deltaSeconds, Set<Integer> polledThisFrame) {
        PlayerAction queued = submitted.remove(seat);
        if (queued != null) return queued;
        double dt = polledThisFrame.add(seat) ? deltaSeconds : 0.0;
        DecisionRequest req = new DecisionRequest(
                seat, phase, legalActions(seat), currentRound(), rules());
        return players.get(seat).chooseAction(req, dt).orElse(null);
    }`,
         `    private PlayerAction pollSeat(int seat, double deltaSeconds, Set<Integer> polledThisFrame) {
        PlayerAction queued = submitted.remove(seat);
        DecisionRequest req = new DecisionRequest(
                seat, phase, legalActions(seat), currentRound(), rules());
        PlayerAction chosen = queued;
        if (chosen == null) {
            double dt = polledThisFrame.add(seat) ? deltaSeconds : 0.0;
            chosen = players.get(seat).chooseAction(req, dt).orElse(null);
        }
        if (chosen != null && req.legalActions().contains(chosen)) actionObserver.accept(req, chosen);
        return chosen;
    }`);
 return source;
};
