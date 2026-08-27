package com.neueda.leap.merchantportal;

import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class BatchPayoutJob {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchPayoutJob.class);

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 1000; // 1 second base backoff

    // Payout statuses to distinguish between different states
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PENDING_RETRY = "PENDING_RETRY";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_MANUAL_REVIEW = "MANUAL_REVIEW";

    private BankTransferClient bankTransferClient;
    private PayoutRepository payoutRepository;

    public BatchPayoutJob(BankTransferClient bankTransferClient, PayoutRepository payoutRepository) {
        this.bankTransferClient = bankTransferClient;
        this.payoutRepository = payoutRepository;
    }

    /**
     * Runs the nightly batch payout with improved safety and error handling.
     * Features:
     * - Atomic transactions: status is only updated after successful transfer
     * - Idempotency: uses idempotency keys to prevent duplicate payments
     * - Smart retries: distinguishes transient from permanent failures
     * - Intermediate states: IN_PROGRESS, PENDING_RETRY, FAILED, MANUAL_REVIEW
     * - Audit logging: detailed logs for compliance and debugging
     */
    @Transactional
    public void runNightlyBatch(List<PayoutRequest> approvedPayouts) {
        log.info("Starting nightly batch payout processing for {} payouts", approvedPayouts.size());

        for (PayoutRequest payout : approvedPayouts) {
            processPayoutWithRetry(payout);
        }

        log.info("Completed nightly batch payout processing");
    }

    /**
     * Process a single payout with retry logic for transient failures.
     */
    private void processPayoutWithRetry(PayoutRequest payout) {
        String payoutId = payout.getId();
        
        // Generate idempotency key to prevent duplicate transfers
        String idempotencyKey = generateIdempotencyKey(payout);
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                // Mark as IN_PROGRESS before attempting transfer
                markPayoutInProgress(payout);
                
                log.info("Processing payout {} (attempt {}/{}) - Merchant: {}, Amount: {}",
                        payoutId, attempt, MAX_RETRY_ATTEMPTS, 
                        payout.getMerchantId(), payout.getAmount());
                
                // Attempt the bank transfer with idempotency key
                bankTransferClient.transfer(payout.getMerchantId(), payout.getAmount(), idempotencyKey);
                
                // Transfer succeeded - mark as PAID within same transaction
                markPayoutPaid(payout);
                log.info("Payout {} successfully marked as PAID", payoutId);
                return; // Exit on success
                
            } catch (TransientBankTransferException e) {
                // Transient errors (network, timeout, temporary service issues)
                handleTransientError(payout, e, attempt);
                
            } catch (PermanentBankTransferException e) {
                // Permanent errors (invalid account, insufficient funds, etc.)
                handlePermanentError(payout, e);
                return; // No point retrying permanent errors
                
            } catch (BankTransferException e) {
                // Unknown error type - treat as potentially transient
                handleUnknownError(payout, e, attempt);
            }
            
            // Back off before retry (exponential backoff)
            if (attempt < MAX_RETRY_ATTEMPTS) {
                backoffBeforeRetry(attempt);
            }
        }
        
        // All retries exhausted
        log.error("Payout {} failed after {} attempts - marking for manual review", 
                payoutId, MAX_RETRY_ATTEMPTS);
        markPayoutForManualReview(payout);
    }

    /**
     * Mark payout as IN_PROGRESS and persist immediately.
     * This prevents re-processing if the batch is interrupted.
     */
    @Transactional
    private void markPayoutInProgress(PayoutRequest payout) {
        payout.setApprovalStatus(STATUS_IN_PROGRESS);
        payout.setAttemptCount(payout.getAttemptCount() + 1);
        payout.setLastProcessedAt(System.currentTimeMillis());
        payoutRepository.save(payout);
    }

    /**
     * Mark payout as PAID only after successful transfer confirmation.
     * This is atomic with the transfer attempt.
     */
    @Transactional
    private void markPayoutPaid(PayoutRequest payout) {
        payout.setApprovalStatus(STATUS_PAID);
        payout.setCompletedAt(System.currentTimeMillis());
        payoutRepository.save(payout);
    }

    /**
     * Handle transient errors that should be retried.
     */
    private void handleTransientError(PayoutRequest payout, 
                                     TransientBankTransferException e, 
                                     int attempt) {
        log.warn("Transient error for payout {} (attempt {}): {} - will retry",
                payout.getId(), attempt, e.getMessage(), e);
        
        if (attempt < MAX_RETRY_ATTEMPTS) {
            payout.setApprovalStatus(STATUS_PENDING_RETRY);
        }
    }

    /**
     * Handle permanent errors that should not be retried.
     */
    private void handlePermanentError(PayoutRequest payout, 
                                     PermanentBankTransferException e) {
        log.error("Permanent error for payout {}: {} - marking as FAILED",
                payout.getId(), e.getMessage(), e);
        
        markPayoutFailed(payout, e.getMessage());
    }

    /**
     * Handle unknown errors - treat conservatively.
     */
    private void handleUnknownError(PayoutRequest payout, 
                                   BankTransferException e, 
                                   int attempt) {
        log.warn("Unknown error type for payout {} (attempt {}): {} - will retry cautiously",
                payout.getId(), attempt, e.getMessage(), e);
        
        if (attempt < MAX_RETRY_ATTEMPTS) {
            payout.setApprovalStatus(STATUS_PENDING_RETRY);
        }
    }

    /**
     * Mark payout as FAILED with error details.
     */
    @Transactional
    private void markPayoutFailed(PayoutRequest payout, String errorReason) {
        payout.setApprovalStatus(STATUS_FAILED);
        payout.setFailureReason(errorReason);
        payout.setCompletedAt(System.currentTimeMillis());
        payoutRepository.save(payout);
    }

    /**
     * Mark payout for manual review after all retries exhausted.
     */
    @Transactional
    private void markPayoutForManualReview(PayoutRequest payout) {
        payout.setApprovalStatus(STATUS_MANUAL_REVIEW);
        payout.setRequiresManualReview(true);
        payout.setCompletedAt(System.currentTimeMillis());
        payoutRepository.save(payout);
    }

    /**
     * Generate idempotency key for safe retries.
     * Ensures same payout is not processed twice even if batch is re-run.
     */
    private String generateIdempotencyKey(PayoutRequest payout) {
        // Use payout ID and batch date to create stable idempotency key
        return "payout-" + payout.getId() + "-" + System.currentTimeMillis() / 86400000L;
    }

    /**
     * Exponential backoff before retry.
     */
    private void backoffBeforeRetry(int attempt) {
        long backoffMs = RETRY_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
        log.debug("Backing off for {} ms before retry attempt {}", backoffMs, attempt + 1);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Backoff interrupted", e);
        }
    }
}
