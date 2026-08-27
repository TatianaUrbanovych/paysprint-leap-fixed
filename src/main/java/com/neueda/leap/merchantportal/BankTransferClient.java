package com.neueda.leap.merchantportal;

public interface BankTransferClient {
    /**
     * Transfer funds to a merchant (legacy method for backward compatibility).
     */
    void transfer(Long merchantId, double amount) throws BankTransferException;

    /**
     * Transfer funds to a merchant with idempotency key support.
     * The idempotency key ensures that the same transfer is not processed twice,
     * allowing safe retries without risking duplicate payments.
     *
     * @param merchantId the merchant ID
     * @param amount the amount to transfer
     * @param idempotencyKey a unique key for this transfer (prevents duplicates on retry)
     * @throws BankTransferException if the transfer fails
     */
    void transfer(Long merchantId, double amount, String idempotencyKey) throws BankTransferException;
}
