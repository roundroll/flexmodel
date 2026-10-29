package com.roundroll.flex.cucumber;

import com.roundroll.flex.Node;
import com.roundroll.flex.Planner;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public final class FlexSteps {
    private static final Map<String, Class<? extends Node>> FEATURE_TYPES = Map.of(
        "mid", ExampleNodes.Mid.class,
        "spread", ExampleNodes.Spread.class,
        "vwap", ExampleNodes.Vwap.class,
        "recent trade vs mid", ExampleNodes.RecentTradeVsMid.class,
        "mid vs vwap", ExampleNodes.MidVsVwap.class,
        "norm dist", ExampleNodes.NormDist.class);

    private Planner planner;

    @Given("a planner with the example nodes")
    public void aPlannerWithTheExampleNodes() {
        planner = ExampleNodes.planner(false);
        ExecutionLog.clear();
    }

    @Given("a planner with the example nodes in reverse order")
    public void aPlannerWithTheExampleNodesInReverseOrder() {
        planner = ExampleNodes.planner(true);
        ExecutionLog.clear();
    }

    @Given("a quote with bid {double} and ask {double} has arrived")
    public void aQuoteHasArrived(final double bid, final double ask) {
        dispatchAndClear(new ExampleNodes.Quote(bid, ask));
    }

    @When("a quote with bid {double} and ask {double} arrives")
    public void aQuoteArrives(final double bid, final double ask) {
        dispatch(new ExampleNodes.Quote(bid, ask));
    }

    @Given("a trade at price {double} with size {double} has arrived")
    public void aTradeHasArrived(final double price, final double size) {
        dispatchAndClear(new ExampleNodes.Trade(price, size));
    }

    @When("a trade at price {double} with size {double} arrives")
    public void aTradeArrives(final double price, final double size) {
        dispatch(new ExampleNodes.Trade(price, size));
    }

    @When("a timer at {int} milliseconds arrives")
    public void aTimerArrives(final int ms) {
        dispatch(new ExampleNodes.TimerEvent(ms));
    }

    @When("a heartbeat arrives")
    public void aHeartbeatArrives() {
        dispatch(new ExampleNodes.Heartbeat());
    }

    @When("I query the {string} feature")
    public void iQueryTheFeature(final String name) {
        ExecutionLog.clear();
        feature(name);
    }

    @Then("the executed steps are {string}")
    public void theExecutedStepsAre(final String expected) {
        final List<String> steps = expected.isBlank()
            ? List.of()
            : Arrays.asList(expected.split(",\\s*"));
        assertThat(ExecutionLog.take()).containsExactlyElementsOf(steps);
    }

    @Then("the {string} feature is {double}")
    public void theFeatureIs(final String name, final double expected) {
        assertThat(feature(name)).isCloseTo(expected, within(1E-12));
    }

    @Then("the {string} feature is {int} over {int}")
    public void theFeatureIsTheFraction(final String name, final int numerator, final int denominator) {
        assertThat(feature(name)).isCloseTo((double) numerator / denominator, within(1E-12));
    }

    @Then("the clock is {int}")
    public void theClockIs(final int ms) {
        assertThat(planner.node(ExampleNodes.Clock.class).ms).isEqualTo(ms);
    }

    @Then("querying the {string} feature twice returns the same value")
    public void queryingTwiceReturnsTheSameValue(final String name) {
        assertThat(feature(name)).isEqualTo(feature(name));
    }

    private double feature(final String name) {
        final var type = FEATURE_TYPES.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Unknown feature: " + name);
        }
        return planner.feature(type);
    }

    private void dispatchAndClear(final Object event) {
        dispatch(event);
        ExecutionLog.clear();
    }

    private void dispatch(final Object event) {
        ExecutionLog.clear();
        planner.on(event);
    }
}
