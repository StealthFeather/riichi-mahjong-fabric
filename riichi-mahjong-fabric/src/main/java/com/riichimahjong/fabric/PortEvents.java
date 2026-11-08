package com.riichimahjong.fabric;
import java.util.*;
import java.util.function.Consumer;
public final class PortEvents {
    public static final Bus BUS=new Bus();
    public static final class Bus {
        private final Map<Class<?>,List<Consumer<Object>>> listeners=new HashMap<>();
        @SuppressWarnings("unchecked") public <T> void addListener(Class<T> type,Consumer<T> handler){listeners.computeIfAbsent(type,k->new ArrayList<>()).add((Consumer<Object>)handler);}
        public void post(Object event){for(var handler:listeners.getOrDefault(event.getClass(),List.of()))handler.accept(event);}
    }
}
