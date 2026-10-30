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
 * A handler reads the values its node declares with {@link Deps}; the planner schedules those evals before every
 * handler of the node, so they are fresh. Node state is read with {@link Context#node(Class)} and values with
 * {@link Context#value(Class)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Handle {
}
