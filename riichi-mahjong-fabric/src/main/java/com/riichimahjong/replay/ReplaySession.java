package com.riichimahjong.replay;

import java.util.UUID;

/** A cursor over immutable recorded states; playback never polls the game or reruns bots. */
public final class ReplaySession {
    public enum Control { FIRST,PREVIOUS_ROUND,PREVIOUS,PLAY_PAUSE,NEXT,NEXT_ROUND,LAST,SPEED,PERSPECTIVE,REVEAL,ANALYSIS,EXIT }
    private static final double[] SPEEDS={.5,1,2,4};
    private final ReplayRecord record;
    private int index,perspective,speedIndex=1;
    private boolean playing,showAll,analysisEnabled;
    private double elapsed;
    private long revision;

    public ReplaySession(ReplayRecord record,UUID viewer,boolean analysisEnabled) {
        if(!record.completed())throw new IllegalArgumentException("Only completed matches can be replayed");
        this.record=record;this.analysisEnabled=analysisEnabled;
        perspective=record.participants().stream().filter(p->viewer.equals(p.uuid())).mapToInt(ReplayRecord.Participant::seat).findFirst().orElse(0);
    }
    public ReplayRecord record(){return record;}
    public int index(){return index;}
    public ReplayRecord.Frame frame(){return record.frames().get(index);}
    public boolean playing(){return playing;}
    public double speed(){return SPEEDS[speedIndex];}
    public int perspective(){return perspective;}
    public boolean showAll(){return showAll;}
    public boolean analysisEnabled(){return analysisEnabled;}
    public long revision(){return revision;}

    public void control(Control action) {
        int last=record.frames().size()-1;
        switch(action){
            case FIRST->{index=0;playing=false;}
            case PREVIOUS->{index=Math.max(0,index-1);playing=false;}
            case NEXT->{index=Math.min(last,index+1);playing=false;}
            case LAST->{index=last;playing=false;}
            case PREVIOUS_ROUND->{
                int round=frame().roundIndex(),start=index;
                while(start>0&&record.frames().get(start-1).roundIndex()==round)start--;
                // A second click at the beginning goes to the previous hand.
                if(start==index&&start>0){round=record.frames().get(--start).roundIndex();
                    while(start>0&&record.frames().get(start-1).roundIndex()==round)start--;}
                index=start;playing=false;
            }
            case NEXT_ROUND->{
                int round=frame().roundIndex();int next=index+1;
                while(next<=last&&record.frames().get(next).roundIndex()==round)next++;
                index=Math.min(last,next);playing=false;
            }
            case PLAY_PAUSE->{if(index==last)index=0;playing=!playing;}
            case SPEED->speedIndex=(speedIndex+1)%SPEEDS.length;
            case PERSPECTIVE->perspective=(perspective+1)%record.playerCount();
            case REVEAL->showAll=!showAll;
            case ANALYSIS->analysisEnabled=!analysisEnabled;
            case EXIT->playing=false;
        }
        elapsed=0;revision++;
    }
    /** Returns true only when the visible frame changed. */
    public boolean tick() {
        if(!playing)return false;
        elapsed+=speed()/20;
        if(elapsed<1)return false;
        elapsed-=1;
        int previous=index;index=Math.min(record.frames().size()-1,index+1);
        if(index==record.frames().size()-1)playing=false;
        if(index!=previous){revision++;return true;}
        return false;
    }
}
