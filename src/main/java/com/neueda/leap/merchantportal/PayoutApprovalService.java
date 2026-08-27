package com.neueda.leap.merchantportal;

public class PayoutApprovalService {

    private PayoutRepository payoutRepository;

    public PayoutApprovalService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    // FIXED (A06): segregation of duties enforced below - requester cannot approve their own payout.
    public void approve(Long payoutId, Long approvingUserId) {
        PayoutRequest payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        // enforce segregation of duties: requester cannot approve their own payout
        if (payout.getRequestedByUserId().equals(approvingUserId)) {
            throw new SecurityException("Approver cannot be the same user who requested the payout");
        }

        payout.setApprovalStatus("APPROVED");
        payout.setApprovedByUserId(approvingUserId);
        payoutRepository.save(payout);
    }
}
