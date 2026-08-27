package com.neueda.leap.merchantportal;

public class BankTransferException extends RuntimeException {
    public BankTransferException(String message) {
        super(message);
    }

    public BankTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
