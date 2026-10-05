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
    private static final String SYMBOL_FOO = "FOO";
    private static final String SYMBOL_BAR = "BAR";

    public static void main(String[] args) {
        AtomicLong idGen = new AtomicLong(0);
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));

        System.out.println("=== STEP 1: Seed book with a simple market maker ===");
        NaiveMarketMakerStrategy mm = new NaiveMarketMakerStrategy("MM1", idGen);
        mm.onTick(SYMBOL_FOO, 100, 2, 10, System.nanoTime())
            .forEach(order -> printResult("MM quote", engine.submit(order)));

        System.out.println(engine.snapshot(5));

        System.out.println("=== STEP 1A: FIFO within the same price level ===");
        MatchingEngine fifoEngine = new MatchingEngine(new RiskManager(100));
        Order fifoBuy1 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "FIFO_BUY_1", Side.BUY, OrderType.LIMIT, 100, 5, System.nanoTime());
        Order fifoBuy2 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "FIFO_BUY_2", Side.BUY, OrderType.LIMIT, 100, 5, System.nanoTime());
        printResult("FIFO_BUY_1 limit buy at 100", fifoEngine.submit(fifoBuy1));
        printResult("FIFO_BUY_2 limit buy at 100", fifoEngine.submit(fifoBuy2));

        Order fifoSell = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "FIFO_SELL_1", Side.SELL, OrderType.MARKET, 0, 6, System.nanoTime());
        printResult("FIFO_SELL_1 market sell", fifoEngine.submit(fifoSell));
        System.out.println(fifoEngine.snapshot(5));
        System.out.println("FIFO_BUY_1=" + fifoEngine.positionOf("FIFO_BUY_1", SYMBOL_FOO));
        System.out.println("FIFO_BUY_2=" + fifoEngine.positionOf("FIFO_BUY_2", SYMBOL_FOO));
        System.out.println("FIFO_SELL_1=" + fifoEngine.positionOf("FIFO_SELL_1", SYMBOL_FOO));

        System.out.println("=== STEP 1B: Cancel resting order and clean empty level ===");
        MatchingEngine cancelEngine = new MatchingEngine(new RiskManager(100));
        Order restingBuy = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "CANCEL_BUY", Side.BUY, OrderType.LIMIT, 105, 4, System.nanoTime());
        printResult("CANCEL_BUY limit buy", cancelEngine.submit(restingBuy));
        System.out.println("cancel success=" + cancelEngine.cancelOrder(SYMBOL_FOO, restingBuy.id()));
        System.out.println(cancelEngine.snapshot(SYMBOL_FOO, 5));

        System.out.println("=== STEP 1C: Partial fill across multiple resting levels ===");
        MatchingEngine multiLevelEngine = new MatchingEngine(new RiskManager(100));
        Order ask1 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "SELLER_1", Side.SELL, OrderType.LIMIT, 101, 4, System.nanoTime());
        Order ask2 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "SELLER_2", Side.SELL, OrderType.LIMIT, 102, 6, System.nanoTime());
        Order buyLarge = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "BUYER_1", Side.BUY, OrderType.MARKET, 0, 7, System.nanoTime());
        printResult("SELLER_1 sell limit 101", multiLevelEngine.submit(ask1));
        printResult("SELLER_2 sell limit 102", multiLevelEngine.submit(ask2));
        printResult("BUYER_1 market buy 7", multiLevelEngine.submit(buyLarge));
        System.out.println(multiLevelEngine.snapshot(SYMBOL_FOO, 5));

        System.out.println("=== STEP 1D: Modify a resting order ===");
        MatchingEngine modifyEngine = new MatchingEngine(new RiskManager(100));
        Order modifyOrder = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "MOD_BUY", Side.BUY, OrderType.LIMIT, 104, 4, System.nanoTime());
        printResult("MOD_BUY limit buy", modifyEngine.submit(modifyOrder));
        System.out.println("modify result=" + modifyEngine.modifyOrder(SYMBOL_FOO, modifyOrder.id(), 108, 3)
            .map(order -> "updated " + order.id() + " -> price=" + order.price() + ", qty=" + order.remainingQty())
            .orElse("not found"));
        System.out.println(modifyEngine.snapshot(SYMBOL_FOO, 5));

        System.out.println("=== STEP 1E: Different symbols do not interfere ===");
        MatchingEngine multiSymbolEngine = new MatchingEngine(new RiskManager(100));
        Order fooBuy = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "MULTI_BUY", Side.BUY, OrderType.LIMIT, 100, 5, System.nanoTime());
        Order barBuy = new Order(idGen.incrementAndGet(), SYMBOL_BAR, "MULTI_BUY", Side.BUY, OrderType.LIMIT, 200, 5, System.nanoTime());
        printResult("FOO buy limit", multiSymbolEngine.submit(fooBuy));
        printResult("BAR buy limit", multiSymbolEngine.submit(barBuy));
        System.out.println(multiSymbolEngine.snapshot(5));
        System.out.println("MULTI_BUY FOO=" + multiSymbolEngine.positionOf("MULTI_BUY", SYMBOL_FOO));
        System.out.println("MULTI_BUY BAR=" + multiSymbolEngine.positionOf("MULTI_BUY", SYMBOL_BAR));

        System.out.println("=== STEP 1F: Invalid order and wrong-owner cancel checks ===");
        MatchingEngine invalidEngine = new MatchingEngine(new RiskManager(100));
        Order invalidLimit = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "BAD_OWNER", Side.BUY, OrderType.LIMIT, 110, 3, System.nanoTime());
        printResult("BAD_OWNER buy limit", invalidEngine.submit(invalidLimit));
        System.out.println("wrong owner cancel=" + invalidEngine.cancelOrder(SYMBOL_FOO, "OTHER_OWNER", invalidLimit.id()));
        System.out.println("invalid symbol cancel=" + invalidEngine.cancelOrder("ZOO", invalidLimit.id()));
        System.out.println("missing order cancel=" + invalidEngine.cancelOrder(SYMBOL_FOO, "BAD_OWNER", idGen.incrementAndGet()));

        Order blankSymbolOrder = new Order(idGen.incrementAndGet(), "   ", "BAD_OWNER", Side.BUY, OrderType.LIMIT, 110, 3, System.nanoTime());
        printResult("blank symbol order", invalidEngine.submit(blankSymbolOrder));
        printResult("null order", invalidEngine.submit(null));

        System.out.println("=== STEP 1G: Price-time priority (FIFO strict ordering) ===");
        MatchingEngine priceTimePriorityEngine = new MatchingEngine(new RiskManager(100));
        long baseSeq = System.nanoTime();
        Order ptp1 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "PTP_BUY_1", Side.BUY, OrderType.LIMIT, 105, 5, baseSeq, baseSeq);
        Order ptp2 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "PTP_BUY_2", Side.BUY, OrderType.LIMIT, 105, 5, baseSeq + 100, baseSeq + 100);
        Order ptp3 = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "PTP_BUY_3", Side.BUY, OrderType.LIMIT, 105, 5, baseSeq + 200, baseSeq + 200);
        printResult("PTP_BUY_1 resting", priceTimePriorityEngine.submit(ptp1));
        printResult("PTP_BUY_2 resting", priceTimePriorityEngine.submit(ptp2));
        printResult("PTP_BUY_3 resting", priceTimePriorityEngine.submit(ptp3));

        Order ptpSell = new Order(idGen.incrementAndGet(), SYMBOL_FOO, "PTP_SELL", Side.SELL, OrderType.MARKET, 0, 10, System.nanoTime());
        printResult("PTP_SELL market sell 10", priceTimePriorityEngine.submit(ptpSell));
        System.out.println("PTP_BUY_1=" + priceTimePriorityEngine.positionOf("PTP_BUY_1", SYMBOL_FOO));
        System.out.println("PTP_BUY_2=" + priceTimePriorityEngine.positionOf("PTP_BUY_2", SYMBOL_FOO));
        System.out.println("PTP_BUY_3=" + priceTimePriorityEngine.positionOf("PTP_BUY_3", SYMBOL_FOO));

        System.out.println("=== STEP 2: Aggressive buy crosses ask ===");
        Order aggressiveBuy = new Order(
            idGen.incrementAndGet(), SYMBOL_FOO, "TAKER_A", Side.BUY, OrderType.MARKET, 0, 6, System.nanoTime()
        );
        printResult("TAKER_A market buy", engine.submit(aggressiveBuy));
        System.out.println(engine.snapshot(SYMBOL_FOO, 5));

        System.out.println("=== STEP 3: Aggressive sell crosses bid ===");
        Order aggressiveSell = new Order(
            idGen.incrementAndGet(), SYMBOL_FOO, "TAKER_B", Side.SELL, OrderType.MARKET, 0, 4, System.nanoTime()
        );
        printResult("TAKER_B market sell", engine.submit(aggressiveSell));
        System.out.println(engine.snapshot(SYMBOL_FOO, 5));

        System.out.println("=== STEP 4: Risk reject example ===");
        Order tooLargeBuy = new Order(
            idGen.incrementAndGet(), SYMBOL_FOO, "TAKER_A", Side.BUY, OrderType.MARKET, 0, 200, System.nanoTime()
        );
        printResult("TAKER_A oversized order", engine.submit(tooLargeBuy));

        System.out.println("=== FINAL POSITIONS ===");
        System.out.println("MM1=" + engine.positionOf("MM1"));
        System.out.println("TAKER_A=" + engine.positionOf("TAKER_A"));
        System.out.println("TAKER_B=" + engine.positionOf("TAKER_B"));
        System.out.println("MULTI_BUY FOO=" + multiSymbolEngine.positionOf("MULTI_BUY", SYMBOL_FOO));
        System.out.println("MULTI_BUY BAR=" + multiSymbolEngine.positionOf("MULTI_BUY", SYMBOL_BAR));
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
