package com.roundroll.flex;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler.
 * <p>
 * The method must be declared {@code void name(EventType event, Context context)} and runs once for every event
 * whose class is exactly {@code EventType}. Handlers see every event and are never coalesced or cut off.
 * <p>
 * {@link #deps()} declares the values the handler reads. The planner schedules those evals before the handler, so
 * they are fresh; node state is read with {@link Context#node(Class)} and values with {@link Context#value(Class)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Handle {
    /**
     * Node types whose values this handler reads.
     */
    Class<? extends Node>[] deps() default {};
}
