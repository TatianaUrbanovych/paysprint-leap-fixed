package com.neueda.leap.merchantportal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class MerchantController {

    @Autowired
    private PayoutRepository payoutRepository;

    // FIX (A01): caller passes their merchant ID in the header; return 404 whether
    // the payout doesn't exist or belongs to a different merchant (don't leak existence)
    @GetMapping("/api/payouts/{payoutId}")
    public PayoutRequest getPayout(@PathVariable Long payoutId,
                                   @RequestHeader("X-Merchant-Id") Long callerMerchantId) {
        PayoutRequest payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        if (!payout.getMerchantId().equals(callerMerchantId)) {
            throw new RuntimeException("Payout not found");
        }

        return payout;
    }
}
