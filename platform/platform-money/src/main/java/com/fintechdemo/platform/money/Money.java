package com.fintechdemo.platform.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact amount of money: a whole number of minor units (kobo for NGN) plus its currency.
 * No float or double anywhere. Arithmetic fails loudly on overflow or mixed currencies.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    /** Converts a major-unit amount from an API edge (e.g. 10000.50 naira). Never rounds silently. */
    public static Money ofMajor(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        try {
            long minorUnits = amount.movePointRight(currency.minorUnitDigits()).longValueExact();
            return new Money(minorUnits, currency);
        } catch (ArithmeticException notExact) {
            throw new InvalidAmountException(
                    amount + " is not an exact " + currency + " amount (max "
                            + currency.minorUnitDigits() + " decimal places)", notExact);
        }
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minorUnits, currency.minorUnitDigits());
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    /** Applies a rate (fee percentage, tax rate). The caller must choose how to round. */
    public Money times(BigDecimal factor, RoundingMode rounding) {
        Objects.requireNonNull(factor, "factor");
        Objects.requireNonNull(rounding, "rounding");
        BigDecimal exact = BigDecimal.valueOf(minorUnits).multiply(factor);
        return new Money(exact.setScale(0, rounding).longValueExact(), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return minorUnits > other.minorUnits;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return minorUnits < other.minorUnits;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    @Override
    public String toString() {
        return currency + " " + toMajor().toPlainString();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (other.currency != currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
