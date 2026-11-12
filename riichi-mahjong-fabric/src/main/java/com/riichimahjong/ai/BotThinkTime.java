package com.riichimahjong.ai;

import com.themahjong.driver.DecisionRequest;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Sample once per decision window; count game time without blocking the server or AI worker. */
public final class BotThinkTime {
    public static final double MIN_SECONDS=.5,MAX_SECONDS=1.5;
    private final DoubleSupplier random;
    private DecisionRequest current;
    private double elapsed,delay;

    public BotThinkTime(){this(()->ThreadLocalRandom.current().nextDouble());}
    public BotThinkTime(DoubleSupplier random){this.random=Objects.requireNonNull(random);}

    public boolean ready(DecisionRequest request,double deltaSeconds){
        if(!Objects.equals(current,request)){
            double sample=random.getAsDouble();
            if(!Double.isFinite(sample)||sample<0||sample>1)throw new IllegalArgumentException("Random sample outside [0,1]");
            current=request;elapsed=0;delay=MIN_SECONDS+(MAX_SECONDS-MIN_SECONDS)*sample;
        }
        elapsed+=Double.isFinite(deltaSeconds)?Math.max(0,deltaSeconds):0;
        return elapsed+1e-9>=delay;
    }
}
