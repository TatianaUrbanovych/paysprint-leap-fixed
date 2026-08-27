package com.neueda.leap.merchantportal;

import java.util.List;

public class BatchPayoutJob {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchPayoutJob.class);

    private BankTransferClient bankTransferClient;
    private PayoutRepository payoutRepository;

    public BatchPayoutJob(BankTransferClient bankTransferClient, PayoutRepository payoutRepository) {
        this.bankTransferClient = bankTransferClient;
        this.payoutRepository = payoutRepository;
    }

    // VULNERABILITY (A10): if the bank transfer fails partway through the
    // nightly batch, the payout is still marked APPROVED->PAID and the loop
    // moves on to the next merchant. There is no distinction between "this
    // payout was never attempted" and "this payout failed after the money
    // may have already left," so re-running the batch after a failure risks
    // paying some merchants twice, and silently skipping others.
    public void runNightlyBatch(List<PayoutRequest> approvedPayouts) {
        for (PayoutRequest payout : approvedPayouts) {
            try {
                bankTransferClient.transfer(payout.getMerchantId(), payout.getAmount());
                payout.setApprovalStatus("PAID");
            } catch (BankTransferException e) {
                log.warn("Transfer failed for payout {}, marking paid anyway: {}",
                        payout.getId(), e.getMessage());
                payout.setApprovalStatus("PAID");
            }
            payoutRepository.save(payout);
        }
    }
}
