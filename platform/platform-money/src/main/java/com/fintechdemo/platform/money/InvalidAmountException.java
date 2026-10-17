
package com.fintechdemo.platform.money;

public final class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(String message, Throwable cause) {
        super(message, cause)
    }
}