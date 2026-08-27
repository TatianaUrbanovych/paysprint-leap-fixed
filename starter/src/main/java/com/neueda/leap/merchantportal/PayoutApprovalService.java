package com.neueda.leap.merchantportal;

public class PayoutApprovalService {

    private PayoutRepository payoutRepository;

    public PayoutApprovalService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    // VULNERABILITY (A06): the design has no concept of segregation of
    // duties, whoever requested a payout is also allowed to approve it
    // themselves. This is a design flaw: no amount of careful coding of
    // *this* method fixes it, the approval workflow itself needs a rule
    // that the approver cannot be the requester.
    public void approve(Long payoutId, Long approvingUserId) {
        PayoutRequest payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        payout.setApprovalStatus("APPROVED");
        payout.setApprovedByUserId(approvingUserId);
        payoutRepository.save(payout);
    }
}
