package com.lowlatencylab.sim.engine;

import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.OrderStatus;
import com.lowlatencylab.sim.model.OrderType;
import com.lowlatencylab.sim.model.Side;
import com.lowlatencylab.sim.risk.RiskManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineTest {
    private static final String SYMBOL = "FOO";

    // A non-crossing limit order should wait in the book.
    @Test
    void limitOrderRestsWhenItDoesNotCross() {
        MatchingEngine engine = engineWithLimit(100);
        Order order = order(1, "BUYER", Side.BUY, OrderType.LIMIT, 100, 5);

        SubmissionResult result = engine.submit(order);

        assertTrue(result.accepted());
        assertTrue(result.trades().isEmpty());
        assertTrue(result.restingOrderAdded());
        assertEquals(OrderStatus.RESTING, order.status());
        assertTrue(engine.snapshot(SYMBOL, 5).contains("price=100 qty=5 orders=1"));
    }

    // A market order trades at the best available price and updates positions.
    @Test
    void marketOrderMatchesBestPriceAndUpdatesPositions() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        engine.submit(order(1, "SELLER", Side.SELL, OrderType.LIMIT, 101, 4));

        SubmissionResult result = engine.submit(order(2, "BUYER", Side.BUY, OrderType.MARKET, 0, 4));

        assertTrue(result.accepted());
        assertEquals(1, result.trades().size());
        assertEquals(101, result.trades().get(0).price());
        assertEquals(4, result.trades().get(0).quantity());
        assertEquals(4, engine.positionOf("BUYER", SYMBOL));
        assertEquals(-4, engine.positionOf("SELLER", SYMBOL));
        assertTrue(engine.bestAsk(SYMBOL).isEmpty());
    }

    // Matching should prefer the best price, then the oldest order at that price.
    @Test
    void matchingUsesPricePriorityThenFifo() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        engine.submit(order(1, "SELLER_102", Side.SELL, OrderType.LIMIT, 102, 5));
        engine.submit(order(2, "SELLER_101_A", Side.SELL, OrderType.LIMIT, 101, 2));
        engine.submit(order(3, "SELLER_101_B", Side.SELL, OrderType.LIMIT, 101, 2));

        SubmissionResult result = engine.submit(order(4, "BUYER", Side.BUY, OrderType.MARKET, 0, 3));

        assertEquals(2, result.trades().size());
        assertEquals("SELLER_101_A", result.trades().get(0).sellOwner());
        assertEquals("SELLER_101_B", result.trades().get(1).sellOwner());
        assertEquals(2, result.trades().get(0).quantity());
        assertEquals(1, result.trades().get(1).quantity());
        assertEquals(1, engine.findOrderById(SYMBOL, 3).orElseThrow().remainingQty());
        assertEquals(5, engine.findOrderById(SYMBOL, 1).orElseThrow().remainingQty());
    }

    // A partially filled limit order should rest with its remaining quantity.
    @Test
    void partialLimitFillRestsRemainingQuantity() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        engine.submit(order(1, "SELLER", Side.SELL, OrderType.LIMIT, 101, 4));

        Order incoming = order(2, "BUYER", Side.BUY, OrderType.LIMIT, 101, 7);
        SubmissionResult result = engine.submit(incoming);

        assertEquals(1, result.trades().size());
        assertEquals(3, incoming.remainingQty());
        assertEquals(OrderStatus.RESTING, incoming.status());
        assertTrue(result.restingOrderAdded());
        assertTrue(engine.snapshot(SYMBOL, 5).contains("price=101 qty=3 orders=1"));
    }

    // Only the owner may cancel an order, and empty price levels should disappear.
    @Test
    void cancellationRequiresCorrectOwnerAndRemovesEmptyLevel() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        Order order = order(1, "OWNER", Side.BUY, OrderType.LIMIT, 100, 5);
        engine.submit(order);

        assertFalse(engine.cancelOrder(SYMBOL, "OTHER_OWNER", order.id()));
        assertTrue(engine.cancelOrder(SYMBOL, "OWNER", order.id()));
        assertEquals(OrderStatus.CANCELLED, order.status());
        assertTrue(engine.bestBid(SYMBOL).isEmpty());
    }

    // Modifying an order removes it from the old level and adds it at the new price.
    @Test
    void modificationReinsertsOrderAtNewPrice() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        Order order = order(1, "OWNER", Side.BUY, OrderType.LIMIT, 100, 5);
        engine.submit(order);

        assertTrue(engine.modifyOrder(SYMBOL, "OWNER", order.id(), 105, 3).isPresent());

        assertEquals(105, order.price());
        assertEquals(3, order.remainingQty());
        assertEquals(OrderStatus.RESTING, order.status());
        assertTrue(engine.snapshot(SYMBOL, 5).contains("price=105 qty=3 orders=1"));
        assertFalse(engine.snapshot(SYMBOL, 5).contains("price=100"));
    }

    // A modified order loses priority, even when its price stays unchanged.
    @Test
    void modificationLosesTimePriority() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        Order first = order(1, "FIRST", Side.BUY, OrderType.LIMIT, 100, 5);
        engine.submit(first);
        engine.submit(order(2, "SECOND", Side.BUY, OrderType.LIMIT, 100, 5));

        assertTrue(engine.modifyOrder(SYMBOL, "FIRST", first.id(), 100, 5).isPresent());
        SubmissionResult result = engine.submit(order(3, "SELLER", Side.SELL, OrderType.MARKET, 0, 1));

        assertEquals("SECOND", result.trades().get(0).buyOwner());
    }

    // Modifications may reduce remaining quantity but cannot increase it.
    @Test
    void modificationCannotIncreaseRemainingQuantity() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        Order order = order(1, "OWNER", Side.BUY, OrderType.LIMIT, 100, 5);
        engine.submit(order);

        assertTrue(engine.modifyOrder(SYMBOL, "OWNER", order.id(), 105, 3).isPresent());
        assertFalse(engine.modifyOrder(SYMBOL, "OWNER", order.id(), 105, 4).isPresent());
        assertEquals(3, order.remainingQty());
    }

    // A partial fill may be reduced or cancelled, but already traded quantity cannot return.
    @Test
    void partiallyFilledOrderCanOnlyModifyRemainingQuantity() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        Order restingSell = order(1, "SELLER", Side.SELL, OrderType.LIMIT, 101, 10);
        engine.submit(restingSell);
        engine.submit(order(2, "BUYER", Side.BUY, OrderType.MARKET, 0, 4));

        assertEquals(OrderStatus.PARTIALLY_FILLED, restingSell.status());
        assertEquals(6, restingSell.remainingQty());
        assertTrue(engine.modifyOrder(SYMBOL, "SELLER", restingSell.id(), 102, 3).isPresent());
        assertEquals(3, restingSell.remainingQty());
        assertFalse(engine.modifyOrder(SYMBOL, "SELLER", restingSell.id(), 102, 7).isPresent());
    }

    // Orders that are fully filled are closed and cannot be modified.
    @Test
    void filledOrderCannotBeModified() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        Order restingSell = order(1, "SELLER", Side.SELL, OrderType.LIMIT, 101, 2);
        engine.submit(restingSell);
        engine.submit(order(2, "BUYER", Side.BUY, OrderType.MARKET, 0, 2));

        assertEquals(OrderStatus.FILLED, restingSell.status());
        assertFalse(engine.modifyOrder(SYMBOL, "SELLER", restingSell.id(), 102, 1).isPresent());
    }

    // Orders for different symbols must use separate books and positions.
    @Test
    void symbolsUseIndependentBooksAndPositions() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));
        engine.submit(order(1, "OWNER", "FOO", Side.BUY, OrderType.LIMIT, 100, 5));
        engine.submit(order(2, "OWNER", "BAR", Side.SELL, OrderType.LIMIT, 200, 5));

        assertEquals(0, engine.positionOf("OWNER", "FOO"));
        assertEquals(0, engine.positionOf("OWNER", "BAR"));
        assertEquals(100, engine.bestBid("FOO").orElseThrow());
        assertEquals(200, engine.bestAsk("BAR").orElseThrow());
        assertTrue(engine.bestAsk("FOO").isEmpty());
    }

    // A submission is rejected when it would exceed the owner's position limit.
    @Test
    void riskRejectsOrderThatWouldBreachPositionLimit() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(5));
        engine.submit(order(1, "SELLER", Side.SELL, OrderType.LIMIT, 100, 4));
        engine.submit(order(2, "BUYER", Side.BUY, OrderType.MARKET, 0, 4));

        SubmissionResult result = engine.submit(order(3, "BUYER", Side.BUY, OrderType.MARKET, 0, 2));

        assertFalse(result.accepted());
        assertTrue(result.reason().contains("projected position"));
        assertEquals(4, engine.positionOf("BUYER", SYMBOL));
    }

    // Null and malformed orders should be rejected before matching.
    @Test
    void invalidSubmissionsAreRejected() {
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));

        assertFalse(engine.submit(null).accepted());
        assertFalse(engine.submit(order(1, "OWNER", "   ", Side.BUY, OrderType.LIMIT, 100, 1)).accepted());
    }

    private static MatchingEngine engineWithLimit(long limit) {
        return new MatchingEngine(new RiskManager((int) limit));
    }

    private static Order order(long id, String owner, Side side, OrderType type, long price, int quantity) {
        return order(id, owner, SYMBOL, side, type, price, quantity);
    }

    private static Order order(long id, String owner, String symbol, Side side, OrderType type, long price, int quantity) {
        return new Order(id, symbol, owner, side, type, price, quantity, id);
    }
}
