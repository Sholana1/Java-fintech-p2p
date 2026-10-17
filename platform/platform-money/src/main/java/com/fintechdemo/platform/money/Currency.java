package com.fintechdemo.platform.money;

public enum Currency {
    NGN(2),
    USD(2);


    private final int minorUnitDigits;

    Currency(int minorUnitDigits) {
        this.minorUnitDigits = minorUnitDigits;
    }

    public int minorUnitDigits() {
        return minorUnitDigits;
    }
}