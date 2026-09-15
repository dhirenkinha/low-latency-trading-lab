package com.lowlatencylab.sim.engine;

import com.lowlatencylab.sim.model.Trade;

import java.util.List;

public final class SubmissionResult {
    private final boolean accepted;
    private final String reason;
    private final List<Trade> trades;
    private final boolean restingOrderAdded;

    public SubmissionResult(boolean accepted, String reason, List<Trade> trades, boolean restingOrderAdded) {
        this.accepted = accepted;
        this.reason = reason;
        this.trades = trades;
        this.restingOrderAdded = restingOrderAdded;
    }

    public boolean accepted() {
        return accepted;
    }

    public String reason() {
        return reason;
    }

    public List<Trade> trades() {
        return trades;
    }

    public boolean restingOrderAdded() {
        return restingOrderAdded;
    }

    public static SubmissionResult rejected(String reason) {
        return new SubmissionResult(false, reason, List.of(), false);
    }

    public static SubmissionResult accepted(List<Trade> trades, boolean restingOrderAdded) {
        return new SubmissionResult(true, "OK", trades, restingOrderAdded);
    }
}
