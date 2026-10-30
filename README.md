# Flex Model

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An event-driven feature planner in Java, following Jane Street's
[Flex Model](https://blog.janestreet.com/flex-model-an-event-driven-feature-engineering-system/).

## The model

A `Node` owns a feature's mutable state and splits its computation in two:

| Member                                                | Meaning                                                                                                                 |
|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `@Deps(...)` on the node class                        | The values the node reads, shared by every handler and the eval. Declared once per node.                                |
| `@Handle void name(Event event, Context ctx)`         | Runs on **every** event of the method's parameter type, updating state. No coalescing, no cutoffs.                      |
| `@Eval double name(Context ctx)`                      | Pure computation of the node's value; runs **only when needed**. A stateless feature (like the mid) needs only an eval. |

The `Planner` builds, at construction, one ordered plan per event class: the event class selects the handlers, and
the values each node declares in `@Deps` fix the order, since a value must be computed before it is read. A handler
that reads a node also handled by the current event sees the handler's update first. Plans are deduplicated and
contain only what the event needs. Dependency cycles and unregistered dependencies are rejected at construction.

The plan for the post's running example — the average of `trade price - mid` over recent trades:

```text
on a quote:  book handle
on a trade:  mid eval ; recent_trade_vs_mid handle
when asked:  recent_trade_vs_mid eval
```

## Example

```java
record Quote(double bid, double ask) {
}

record Trade(double price) {
}

final class Book implements Node {
  double bid, ask;

  @Handle
  void onQuote(final Quote quote, final Context context) {
    bid = quote.bid();
    ask = quote.ask();
  }
}

@Deps(Book.class)
final class Mid implements Node {
  @Eval
  double eval(final Context context) {
    final var book = context.node(Book.class);
    return (book.bid + book.ask) / 2;
  }
}

@Deps(Mid.class)
final class RecentTradeVsMid implements Node {
  private final double[] diffs = new double[10];
  private int position;
  private int count;
  private double sum;

  @Handle
  void onTrade(final Trade trade, final Context context) {
    final double diff = trade.price() - context.value(Mid.class);
    if (count == diffs.length) {
      sum -= diffs[position];
    } else {
      ++count;
    }
    diffs[position] = diff;
    sum += diff;
    position = (position + 1) % diffs.length;
  }

  @Eval
  double eval(final Context context) {
    return count == 0 ? 0 : sum / count;
  }
}

final var planner = new Planner(new RecentTradeVsMid(), new Mid(), new Book());

planner.on(new Quote(100, 102));
planner.on(new Trade(103));
planner.on(new Trade(103));          // a second trade matters even at the same price
planner.on(new Quote(102, 104));
planner.on(new Trade(104));          // uses the mid as of this trade

final var value = planner.feature(RecentTradeVsMid.class);   // (2 + 2 + 1) / 3
```

## Guarantees

- **Order-independent**: node registration order does not affect values; plans are topological.
- **Deduplicated**: a shared value is evaluated once per event.
- **Eval after handle**: when a node both handles the current event and is read by it, its handler runs first.
- **Minimal**: only handlers of the event and the values they transitively read are scheduled.
- **Rejected at startup**: cycles, missing dependencies, duplicate node types, and invalid feature signatures are
  reported with the node and method involved.

## Performance

Planning, reflection, and method-handle binding all happen in the constructor. Dispatch walks a precomputed array
of bound method handles: no reflection and no allocation after warm-up (verified locally with a 1M-dispatch
allocation measurement). A `Planner` is not thread-safe; dispatch from a single thread.

## Behavior worth knowing

- Dependencies are **node-level**: one `@Deps` list per node, shared by every handler and the eval. There are no
  per-handler dependencies; a node whose responsibilities need different inputs should be split.
- Dependencies are **declared, not enforced**: reading `context.value(X)` without listing `X` in the node's `@Deps`
  observes the previous value.
- Events match handlers by **exact class**.
- Feature methods must be declared on the node class itself; inherited methods are not scanned.
- Node classes must be accessible to the planner (public when in another package); methods may be private.
- Feature values are `double`, as in the Flex model.

## Project layout

```text
flexmodel/
├── flex/       # the Flex model engine
└── cucumber/   # Cucumber integration tests for the engine
```

## Build

Java 26 is required; the Gradle wrapper provisions both Gradle and the JDK toolchain automatically.

```sh
./gradlew build            # compile and run all tests
./gradlew :flex:test       # engine unit tests
./gradlew :cucumber:test   # Cucumber integration tests
./gradlew clean            # remove build outputs
```

The Cucumber module runs the scenarios in
`cucumber/src/test/resources/features/flex.feature`, with step definitions and the example nodes in
`cucumber/src/test/java/com/roundroll/flex/cucumber`.
