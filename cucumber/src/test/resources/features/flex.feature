Feature: Event-driven feature planning

  The planner turns events into feature state. Handlers run on every event of
  their type; evals run only when a feature value is needed. Each event runs a
  minimal, ordered, deduplicated plan over the registered nodes.

  Scenario: A quote updates only the book
    Given a planner with the example nodes
    When a quote with bid 100 and ask 102 arrives
    Then the executed steps are "H:Book"
    And the "mid" feature is 101

  Scenario: A trade runs a minimal, ordered, deduplicated plan
    Given a planner with the example nodes
    And a quote with bid 100 and ask 102 has arrived
    When a trade at price 101 with size 1 arrives
    Then the executed steps are "H:Vwap, E:Mid, H:RTVM, E:Vwap, H:MidVsVwap"

  Scenario: Every trade matters and reads a fresh mid
    Given a planner with the example nodes
    And a quote with bid 100 and ask 102 has arrived
    When a trade at price 101 with size 1 arrives
    Then the "recent trade vs mid" feature is 0
    When a trade at price 104 with size 3 arrives
    Then the "recent trade vs mid" feature is 1.5
    When a trade at price 104 with size 3 arrives
    Then the "recent trade vs mid" feature is 2
    When a quote with bid 102 and ask 104 arrives
    And a trade at price 110 with size 1 arrives
    Then the "recent trade vs mid" feature is 13 over 3
    When a trade at price 110 with size 1 arrives
    Then the "recent trade vs mid" feature is 17 over 3

  Scenario: Multi-dependency handlers see updated values
    Given a planner with the example nodes
    And a quote with bid 100 and ask 102 has arrived
    And a trade at price 101 with size 1 has arrived
    And a trade at price 104 with size 3 has arrived
    Then the "vwap" feature is 103.25
    And the "mid vs vwap" feature is -1.125
    When a trade at price 104 with size 3 arrives
    And a trade at price 110 with size 1 arrives
    Then the "vwap" feature is 104.375
    And the "mid vs vwap" feature is -153 over 56

  Scenario: Mid vs VWAP reads the VWAP as of each trade
    Given a planner with the example nodes
    And a quote with bid 100 and ask 102 has arrived
    And a trade at price 104 with size 2 has arrived
    And a trade at price 104 with size 2 has arrived
    Then the "mid vs vwap" feature is -3
    When a trade at price 101 with size 1 arrives
    And a trade at price 101 with size 1 arrives
    Then the "mid vs vwap" feature is -37 over 15

  Scenario: A timer reset runs the node's dependencies
    Given a planner with the example nodes
    And a quote with bid 100 and ask 102 has arrived
    And a trade at price 104 with size 3 has arrived
    When a timer at 500 milliseconds arrives
    Then the executed steps are "E:Mid, H:RTVM:reset, H:Clock"
    And the clock is 500
    And the "recent trade vs mid" feature is 0
    When a trade at price 106 with size 1 arrives
    Then the "recent trade vs mid" feature is 5

  Scenario: Unhandled events run no steps
    Given a planner with the example nodes
    When a heartbeat arrives
    Then the executed steps are ""

  Scenario: A query runs only the eval closure
    Given a planner with the example nodes
    And a quote with bid 100 and ask 102 has arrived
    And a trade at price 104 with size 3 has arrived
    And a trade at price 104 with size 3 has arrived
    When I query the "norm dist" feature
    Then the executed steps are "E:Mid, E:RTVM, E:Spread, E:NormDist"
    And the "norm dist" feature is 1.5
    When I query the "mid" feature
    Then the executed steps are "E:Mid"
    And the "mid" feature is 101
    And querying the "recent trade vs mid" feature twice returns the same value

  Scenario: Plans and values are independent of registration order
    Given a planner with the example nodes in reverse order
    And a quote with bid 100 and ask 102 has arrived
    And a trade at price 101 with size 1 has arrived
    And a trade at price 104 with size 3 has arrived
    And a trade at price 104 with size 3 has arrived
    And a quote with bid 102 and ask 104 has arrived
    And a trade at price 110 with size 1 has arrived
    Then the "recent trade vs mid" feature is 13 over 3
    And the "vwap" feature is 104.375
    And the "mid" feature is 103
