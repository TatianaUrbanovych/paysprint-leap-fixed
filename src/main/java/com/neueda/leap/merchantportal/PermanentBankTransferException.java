package com.neueda.leap.merchantportal;

/**
 * Exception thrown when a bank transfer fails due to a permanent error.
 * Permanent errors (invalid account number, insufficient funds, blocked account, etc.)
 * should NOT be retried as they indicate a configuration or policy issue.
 */
public class PermanentBankTransferException extends BankTransferException {

    public PermanentBankTransferException(String message) {
        super(message);
    }

    public PermanentBankTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
