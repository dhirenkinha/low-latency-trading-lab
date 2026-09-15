package com.lowlatencylab.sim;

import com.lowlatencylab.sim.engine.MatchingEngine;
import com.lowlatencylab.sim.engine.SubmissionResult;
import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.OrderType;
import com.lowlatencylab.sim.model.Side;
import com.lowlatencylab.sim.risk.RiskManager;
import com.lowlatencylab.sim.strategy.NaiveMarketMakerStrategy;

import java.util.concurrent.atomic.AtomicLong;

public final class Main {
    private static final String SYMBOL = "FOO";

    public static void main(String[] args) {
        AtomicLong idGen = new AtomicLong(0);
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));

        System.out.println("=== STEP 1: Seed book with a simple market maker ===");
        NaiveMarketMakerStrategy mm = new NaiveMarketMakerStrategy("MM1", idGen);
        mm.onTick(SYMBOL, 100, 2, 10, System.nanoTime())
            .forEach(order -> printResult("MM quote", engine.submit(order)));

        System.out.println(engine.snapshot(5));

        System.out.println("=== STEP 1A: FIFO within the same price level ===");
        MatchingEngine fifoEngine = new MatchingEngine(new RiskManager(100));
        Order fifoBuy1 = new Order(idGen.incrementAndGet(), SYMBOL, "FIFO_BUY_1", Side.BUY, OrderType.LIMIT, 100, 5, System.nanoTime());
        Order fifoBuy2 = new Order(idGen.incrementAndGet(), SYMBOL, "FIFO_BUY_2", Side.BUY, OrderType.LIMIT, 100, 5, System.nanoTime());
        printResult("FIFO_BUY_1 limit buy at 100", fifoEngine.submit(fifoBuy1));
        printResult("FIFO_BUY_2 limit buy at 100", fifoEngine.submit(fifoBuy2));

        Order fifoSell = new Order(idGen.incrementAndGet(), SYMBOL, "FIFO_SELL_1", Side.SELL, OrderType.MARKET, 0, 6, System.nanoTime());
        printResult("FIFO_SELL_1 market sell", fifoEngine.submit(fifoSell));
        System.out.println(fifoEngine.snapshot(5));
        System.out.println("FIFO_BUY_1=" + fifoEngine.positionOf("FIFO_BUY_1"));
        System.out.println("FIFO_BUY_2=" + fifoEngine.positionOf("FIFO_BUY_2"));
        System.out.println("FIFO_SELL_1=" + fifoEngine.positionOf("FIFO_SELL_1"));

        System.out.println("=== STEP 1B: Cancel resting order and clean empty level ===");
        MatchingEngine cancelEngine = new MatchingEngine(new RiskManager(100));
        Order restingBuy = new Order(idGen.incrementAndGet(), SYMBOL, "CANCEL_BUY", Side.BUY, OrderType.LIMIT, 105, 4, System.nanoTime());
        printResult("CANCEL_BUY limit buy", cancelEngine.submit(restingBuy));
        System.out.println("cancel success=" + cancelEngine.cancelOrder(restingBuy.id()));
        System.out.println(cancelEngine.snapshot(5));

        System.out.println("=== STEP 1C: Partial fill across multiple resting levels ===");
        MatchingEngine multiLevelEngine = new MatchingEngine(new RiskManager(100));
        Order ask1 = new Order(idGen.incrementAndGet(), SYMBOL, "SELLER_1", Side.SELL, OrderType.LIMIT, 101, 4, System.nanoTime());
        Order ask2 = new Order(idGen.incrementAndGet(), SYMBOL, "SELLER_2", Side.SELL, OrderType.LIMIT, 102, 6, System.nanoTime());
        Order buyLarge = new Order(idGen.incrementAndGet(), SYMBOL, "BUYER_1", Side.BUY, OrderType.MARKET, 0, 7, System.nanoTime());
        printResult("SELLER_1 sell limit 101", multiLevelEngine.submit(ask1));
        printResult("SELLER_2 sell limit 102", multiLevelEngine.submit(ask2));
        printResult("BUYER_1 market buy 7", multiLevelEngine.submit(buyLarge));
        System.out.println(multiLevelEngine.snapshot(5));

        System.out.println("=== STEP 1D: Modify a resting order ===");
        MatchingEngine modifyEngine = new MatchingEngine(new RiskManager(100));
        Order modifyOrder = new Order(idGen.incrementAndGet(), SYMBOL, "MOD_BUY", Side.BUY, OrderType.LIMIT, 104, 4, System.nanoTime());
        printResult("MOD_BUY limit buy", modifyEngine.submit(modifyOrder));
        System.out.println("modify result=" + modifyEngine.modifyOrder(modifyOrder.id(), 108, 3)
            .map(order -> "updated " + order.id() + " -> price=" + order.price() + ", qty=" + order.remainingQty())
            .orElse("not found"));
        System.out.println(modifyEngine.snapshot(5));

        System.out.println("=== STEP 2: Aggressive buy crosses ask ===");
        Order aggressiveBuy = new Order(
            idGen.incrementAndGet(), SYMBOL, "TAKER_A", Side.BUY, OrderType.MARKET, 0, 6, System.nanoTime()
        );
        printResult("TAKER_A market buy", engine.submit(aggressiveBuy));
        System.out.println(engine.snapshot(5));

        System.out.println("=== STEP 3: Aggressive sell crosses bid ===");
        Order aggressiveSell = new Order(
            idGen.incrementAndGet(), SYMBOL, "TAKER_B", Side.SELL, OrderType.MARKET, 0, 4, System.nanoTime()
        );
        printResult("TAKER_B market sell", engine.submit(aggressiveSell));
        System.out.println(engine.snapshot(5));

        System.out.println("=== STEP 4: Risk reject example ===");
        Order tooLargeBuy = new Order(
            idGen.incrementAndGet(), SYMBOL, "TAKER_A", Side.BUY, OrderType.MARKET, 0, 200, System.nanoTime()
        );
        printResult("TAKER_A oversized order", engine.submit(tooLargeBuy));

        System.out.println("=== FINAL POSITIONS ===");
        System.out.println("MM1=" + engine.positionOf("MM1"));
        System.out.println("TAKER_A=" + engine.positionOf("TAKER_A"));
        System.out.println("TAKER_B=" + engine.positionOf("TAKER_B"));
    }

    private static void printResult(String label, SubmissionResult result) {
        System.out.println("--- " + label + " ---");
        if (!result.accepted()) {
            System.out.println("REJECTED: " + result.reason());
            return;
        }

        if (result.trades().isEmpty()) {
            System.out.println("No trade. restingOrderAdded=" + result.restingOrderAdded());
            return;
        }

        result.trades().forEach(trade -> System.out.println(
            "TRADE symbol=" + trade.symbol() +
                " price=" + trade.price() +
                " qty=" + trade.quantity() +
                " buyOwner=" + trade.buyOwner() +
                " sellOwner=" + trade.sellOwner()
        ));
    }
}
