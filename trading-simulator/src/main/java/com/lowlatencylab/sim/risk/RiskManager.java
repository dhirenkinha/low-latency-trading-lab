package com.lowlatencylab.sim.risk;

public final class RiskManager {
    private final int maxAbsolutePosition;

    public RiskManager(int maxAbsolutePosition) {
        if (maxAbsolutePosition <= 0) {
            throw new IllegalArgumentException("maxAbsolutePosition must be > 0");
        }
        this.maxAbsolutePosition = maxAbsolutePosition;
    }

    public boolean canAccept(int currentPosition, int requestedQtyDelta) {
        int projected = currentPosition + requestedQtyDelta;
        return Math.abs(projected) <= maxAbsolutePosition;
    }

    public int maxAbsolutePosition() {
        return maxAbsolutePosition;
    }
}
