package com.neueda.leap.merchantportal;

/**
 * Exception thrown when a bank transfer fails due to a transient error.
 * Transient errors (network timeout, temporary service unavailability, etc.)
 * should be retried.
 */
public class TransientBankTransferException extends BankTransferException {

    public TransientBankTransferException(String message) {
        super(message);
    }

    public TransientBankTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
