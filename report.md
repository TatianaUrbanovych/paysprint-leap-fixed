# Lab 13 — Cyber Challenge: PaySprint Merchant Portal Security Audit

**Codebase audited**: `starter/src/main/java/com/neueda/leap/merchantportal/`

---

## Executive Summary (non-technical)

The PaySprint Merchant Portal has four security and reliability issues. One allows any merchant
to view another merchant's confidential payout information. One allows a merchant to approve
their own payout requests with no independent oversight. One allows anyone on the internet to
falsely mark a payout as settled. And one means a failed bank transfer during the nightly run
is silently recorded as successful, causing financial loss with no audit trail.

All four need to be fixed before this service handles real money.

---

## Vulnerability 1 — Broken Access Control on Payout Lookup

**File**: MerchantController.java  
**OWASP 2025**: A01: Broken Access Control  

Any authenticated merchant can enumerate payout IDs and view payout amounts for every other
merchant. Fix: after fetching the payout, verify the caller's merchant ID matches
payout.getMerchantId() before returning data. Return 404 (not 403) to avoid leaking existence.

---

## Vulnerability 2 — Insecure Design: Self-Approval of Payouts

**File**: PayoutApprovalService.java  
**OWASP 2025**: A06: Insecure Design  

No check prevents the approving user from being the same as the requesting user. A merchant
can request and immediately approve their own payout. Fix: enforce
`approvingUserId != requestedByUserId` at the service layer.

---

## Vulnerability 3 — Unauthenticated Webhook

**File**: WebhookController.java  
**OWASP 2025**: A08: Software or Data Integrity Failures  

The payment-status webhook accepts POSTs from anyone with no signature verification. An
attacker can mark any payout as settled by sending an HTTP request to the endpoint. Fix:
verify an HMAC-SHA256 signature in a request header against a shared secret before processing.
Use constant-time comparison to prevent timing attacks.

---

## Vulnerability 4 — Batch Payout: Failure Silently Marked as Success

**File**: BatchPayoutJob.java  
**OWASP 2025**: A10: Mishandling of Exceptional Conditions  

When a bank transfer throws BankTransferException, the catch block marks the payout PAID
anyway. Money may have left the platform but never arrived at the merchant. Re-running the
batch risks double-paying some merchants. Fix: set status to FAILED on exception; require
manual review before any re-run of failed payouts.

---

## Remediation Priority

| # | Vulnerability | Severity |
|---|---------------|----------|
| 3 | Unauthenticated webhook | Critical |
| 4 | Batch failure marked as success | Critical |
| 1 | IDOR on payout lookup | High |
| 2 | Self-approval of payouts | High |
