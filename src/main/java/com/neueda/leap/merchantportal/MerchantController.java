package com.neueda.leap.merchantportal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MerchantController {

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private CurrentMerchantProvider currentMerchantProvider;

    // VULNERABILITY (A01): returns any payout request by ID, with no check
    // that the caller is the merchant (or an authorised staff member) it
    // belongs to. Any logged-in merchant can view another merchant's
    // pending/approved payout amounts.

     @GetMapping("/api/payouts/{payoutId}")
    public PayoutRequest getPayout(@PathVariable Long payoutId) {
        PayoutRequest payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

         if (!payout.getMerchantId().equals(currentMerchantProvider.currentMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        }
        return payout;

    }
}
