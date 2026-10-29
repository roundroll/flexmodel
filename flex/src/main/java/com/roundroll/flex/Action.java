package com.roundroll.flex;

@FunctionalInterface
interface Action {
    void run(Object event);
}
