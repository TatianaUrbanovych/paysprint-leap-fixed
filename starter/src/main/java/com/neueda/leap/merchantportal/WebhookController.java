package com.neueda.leap.merchantportal;

import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {

    // VULNERABILITY (A08): this endpoint receives payment-status updates
    // from an external payment provider and applies them directly, with no
    // verification that the request actually came from that provider (no
    // HMAC signature check, no shared secret, nothing). Anyone who can reach
    // this URL can mark any payout as "settled".
    @PostMapping("/api/webhooks/payment-status")
    public void handlePaymentStatusWebhook(@RequestBody PaymentStatusEvent event) {
        payoutStatusUpdater.markSettled(event.getPayoutId(), event.getStatus());
    }

    private PayoutStatusUpdater payoutStatusUpdater;
}
