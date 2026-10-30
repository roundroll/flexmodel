package com.roundroll.flex.cucumber;

import com.roundroll.flex.Context;
import com.roundroll.flex.Deps;
import com.roundroll.flex.Eval;
import com.roundroll.flex.Handle;
import com.roundroll.flex.Node;
import com.roundroll.flex.Planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The running example from the Flex model post, as planners the scenarios can drive.
 */
public final class ExampleNodes {
    private ExampleNodes() {
    }

    public record Quote(double bid, double ask) {
    }

    public record Trade(double price, double size) {
    }

    public record TimerEvent(int ms) {
    }

    public record Heartbeat() {
    }

    static Planner planner(final boolean reversed) {
        final List<Node> nodes = new ArrayList<>(List.of(
            new Book(), new Mid(), new Spread(), new Vwap(), new RecentTradeVsMid(),
            new MidVsVwap(), new NormDist(), new Clock()));
        if (reversed) {
            Collections.reverse(nodes);
        }
        return new Planner(nodes.toArray(Node[]::new));
    }

    public static final class Book implements Node {
        double bid;
        double ask;

        @Handle
        private void onQuote(final Quote event, final Context context) {
            bid = event.bid();
            ask = event.ask();
            ExecutionLog.add("H:Book");
        }
    }

    @Deps(Book.class)
    public static final class Mid implements Node {
        @Eval
        private double eval(final Context context) {
            final var book = context.node(Book.class);
            ExecutionLog.add("E:Mid");
            return (book.bid + book.ask) / 2;
        }
    }

    @Deps(Book.class)
    public static final class Spread implements Node {
        @Eval
        private double eval(final Context context) {
            final var book = context.node(Book.class);
            ExecutionLog.add("E:Spread");
            return book.ask - book.bid;
        }
    }

    public static final class Vwap implements Node {
        private static final int N = 4;
        private final double[] prices = new double[N];
        private final double[] sizes = new double[N];
        private double sumPrices;
        private double sumSizes;
        private int position;
        private int count;

        @Handle
        private void onTrade(final Trade trade, final Context context) {
            if (count == N) {
                sumPrices -= prices[position];
                sumSizes -= sizes[position];
            } else {
                ++count;
            }
            prices[position] = trade.price() * trade.size();
            sizes[position] = trade.size();
            sumPrices += prices[position];
            sumSizes += sizes[position];
            position = (position + 1) % N;
            ExecutionLog.add("H:Vwap");
        }

        @Eval
        private double eval(final Context context) {
            ExecutionLog.add("E:Vwap");
            return sumSizes != 0 ? sumPrices / sumSizes : 0;
        }
    }

    @Deps(Mid.class)
    public static final class RecentTradeVsMid implements Node {
        private static final int N = 3;
        private final double[] diffs = new double[N];
        private double sum;
        private int position;
        private int count;

        @Handle
        private void onTrade(final Trade trade, final Context context) {
            final var diff = trade.price() - context.value(Mid.class);
            if (count == N) {
                sum -= diffs[position];
            } else {
                ++count;
            }
            diffs[position] = diff;
            sum += diff;
            position = (position + 1) % N;
            ExecutionLog.add("H:RTVM");
        }

        @Handle
        private void onTimer(final TimerEvent event, final Context context) {
            position = 0;
            count = 0;
            sum = 0;
            ExecutionLog.add("H:RTVM:reset");
        }

        @Eval
        private double eval(final Context context) {
            ExecutionLog.add("E:RTVM");
            return count != 0 ? sum / count : 0;
        }
    }

    @Deps({Mid.class, Vwap.class})
    public static final class MidVsVwap implements Node {
        private static final int N = 3;
        private final double[] diffs = new double[N];
        private double sum;
        private int position;
        private int count;

        @Handle
        private void onTrade(final Trade trade, final Context context) {
            final var diff = context.value(Mid.class) - context.value(Vwap.class);
            if (count == N) {
                sum -= diffs[position];
            } else {
                ++count;
            }
            diffs[position] = diff;
            sum += diff;
            position = (position + 1) % N;
            ExecutionLog.add("H:MidVsVwap");
        }

        @Eval
        private double eval(final Context context) {
            ExecutionLog.add("E:MidVsVwap");
            return count != 0 ? sum / count : 0;
        }
    }

    @Deps({RecentTradeVsMid.class, Spread.class})
    public static final class NormDist implements Node {
        @Eval
        private double eval(final Context context) {
            final var spread = context.value(Spread.class);
            ExecutionLog.add("E:NormDist");
            return spread != 0 ? context.value(RecentTradeVsMid.class) / spread : 0;
        }
    }

    public static final class Clock implements Node {
        int ms;

        @Handle
        private void onTimer(final TimerEvent event, final Context context) {
            ms = event.ms();
            ExecutionLog.add("H:Clock");
        }
    }
}
