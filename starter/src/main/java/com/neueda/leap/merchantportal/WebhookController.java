package com.neueda.leap.merchantportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

// Nathan working on this file
// fixed the security issue - before anyone could hit this endpoint, now we check it's actually from our payment provider

@RestController
public class WebhookController {

    // the secret key - stored as an environment variable so it's not hardcoded in the code
    @Value("${webhook.payment.secret}")
    private String webhookSecret;

    // fixed a bug where these weren't being set properly, was going to cause a crash
    private final PayoutStatusUpdater payoutStatusUpdater;
    private final ObjectMapper objectMapper;

    public WebhookController(PayoutStatusUpdater payoutStatusUpdater, ObjectMapper objectMapper) {
        this.payoutStatusUpdater = payoutStatusUpdater;
        this.objectMapper = objectMapper;
    }

    // now checks the signature in the header before doing anything
    @PostMapping("/api/webhooks/payment-status")
    public void handlePaymentStatusWebhook(
            @RequestHeader("X-Signature-256") String signature,
            @RequestBody byte[] rawBody) throws Exception {

        // if the signature doesn't match, stop here and return a 401
        if (!isValidSignature(rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        // only update the payout if the request actually passed the check above
        PaymentStatusEvent event = objectMapper.readValue(rawBody, PaymentStatusEvent.class);
        payoutStatusUpdater.markSettled(event.getPayoutId(), event.getStatus());
    }

    private boolean isValidSignature(byte[] body, String signature) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        // comparing this way so the check always takes the same time (stops timing attacks)
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
