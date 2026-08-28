# Predefined SMS Test Templates & Classifier Audit

This document contains 20 generalized test templates derived from `finetune_data.csv`. Each template includes dynamic placeholders, expected classifications under SancharSaathi risk rules, reasons, and negative contrast tests to ensure robust validation.

---

### TEST TEMPLATE 01 — PhonePe Login OTP
- **Template:** "Your Phonepe login OTP is {OTP}"
- **Dynamic fields:** `{OTP}` = 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Standard single-factor authentication OTP.
- **What developer should test:** Test with different 6-digit values for `{OTP}` (e.g. 192834, 009281) and verify classification remains LOW.
- **Legitimate Contrast:** "PhonePe: Do not share your OTP 123456 with anyone."
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 2

---

### TEST TEMPLATE 02 — Amazon Login OTP
- **Template:** "Your Amazon login OTP is {OTP}. Do not share this with anyone."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Standard login verification with typical safety disclaimer.
- **What developer should test:** Change OTP and verify it stays LOW.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 3

---

### TEST TEMPLATE 03 — Google Verification Code
- **Template:** "Use {OTP} as your verification code for Google."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Routine security validation.
- **What developer should test:** Verify varying OTP structures (e.g., 554321).
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 4

---

### TEST TEMPLATE 04 — Netflix Verification Code
- **Template:** "Your Netflix verification code is {OTP}."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Routine service activation code.
- **What developer should test:** Modify OTP, verify LOW output.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 5

---

### TEST TEMPLATE 05 — Transaction OTP (HDFC)
- **Template:** "{OTP} is your OTP for transaction of Rs {AMOUNT} on HDFC Bank."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code, `{AMOUNT}` = Numeric currency value
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Standard financial transaction transaction warning.
- **What developer should test:** Test with large amounts (e.g. Rs 50000) and small amounts (Rs 50). Verify result remains LOW.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 6

---

### TEST TEMPLATE 06 — Transaction OTP (SBI)
- **Template:** "Never share your OTP. Your SBI bank OTP is {OTP}."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Direct multi-factor authentication warning.
- **What developer should test:** Verify no spam matching occurs on this specific sequence.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 7

---

### TEST TEMPLATE 07 — Swiggy Login Code
- **Template:** "Your Swiggy login code is {OTP}. Valid for {DURATION} minutes."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code, `{DURATION}` = integer value (1-10)
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Temporary authentication token.
- **What developer should test:** Ensure the duration does not cause urgency triggers.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 8

---

### TEST TEMPLATE 08 — Zomato Login
- **Template:** "Zomato: {OTP} is your OTP to login."
- **Dynamic fields:** `{OTP}` = 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Single-brand authentication tag.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 9

---

### TEST TEMPLATE 09 — Uber Code
- **Template:** "Your Uber code is {OTP}. Never share this code."
- **Dynamic fields:** `{OTP}` = 4 or 6-digit numeric code
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Direct transport login code.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 10

---

### TEST TEMPLATE 10 — WhatsApp Registration
- **Template:** "Your WhatsApp registration code is {OTP_FORMATTED}."
- **Dynamic fields:** `{OTP_FORMATTED}` = 3-digit-hyphen-3-digit code (e.g. 123-456)
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Messaging client onboarding.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 11

---

### TEST TEMPLATE 11 — PayPal Security Code
- **Template:** "PayPal: Your security code is {OTP}. It expires in {DURATION} minutes."
- **Dynamic fields:** `{OTP}` = 6-digit numeric, `{DURATION}` = integer
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Secure online checkout code.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 18

---

### TEST TEMPLATE 12 — Legitimate Package Delivery
- **Template:** "Your package from Amazon is out for delivery today. {URL}"
- **Dynamic fields:** `{URL}` = Valid tracking web link
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Standard customer shipment notify with a tracking link.
- **What developer should test:** Verify that standard URLs to `amazon.in` or secure domains do not match warning rules.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 28

---

### TEST TEMPLATE 13 — Legitimate Electricity Bill
- **Template:** "Your electricity bill of Rs {AMOUNT} is due on {DATE}. {URL}"
- **Dynamic fields:** `{AMOUNT}` = Numeric amount, `{DATE}` = Date string, `{URL}` = Payment link
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Standard monthly utility notification.
- **What developer should test:** Change amounts and dates; verify it stays LOW.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 30

---

### TEST TEMPLATE 14 — Bank Credit Alert
- **Template:** "Salary of Rs {AMOUNT} credited to your A/c XX{ACCOUNT_NUMBER}."
- **Dynamic fields:** `{AMOUNT}` = Float amount, `{ACCOUNT_NUMBER}` = 4-digit bank account tail
- **Expected classification:** LOW (Ham)
- **Expected risk:** 0–39
- **Expected reason:** Regular inbound transaction credit notice.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 31

---

### TEST TEMPLATE 15 — Account Suspension Phishing
- **Template:** "URGENT: Your account has been suspended. Click here to verify your details {URL}"
- **Dynamic fields:** `{URL}` = Suspicious domain link
- **Expected classification:** HIGH (Spam)
- **Expected risk:** 80–100
- **Expected reason:** Urgent threat of account lockout + prompt to click suspicious URL.
- **What developer should test:** Verify changing `{URL}` to any domain continues to block the link.
- **Legitimate Contrast:** "Your monthly account statement is available. Please login to your netbanking app to check details." (Expected: LOW)
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 22

---

### TEST TEMPLATE 16 — KYC Verification Urgency Phishing
- **Template:** "URGENT: Verify your {ORG} KYC immediately to avoid suspension {URL}"
- **Dynamic fields:** `{ORG}` = E-Wallet/Bank name, `{URL}` = Malicious KYC domain link
- **Expected classification:** HIGH (Spam)
- **Expected risk:** 80–100
- **Expected reason:** Urgent threat of account block combined with suspicious external link.
- **What developer should test:** Swap `{ORG}` to PhonePe, Paytm, SBI, or HDFC. Result must be HIGH.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 27

---

### TEST TEMPLATE 17 — Reward Points Expiration Phishing
- **Template:** "Dear customer, you have {AMOUNT} reward points expiring today. {URL}"
- **Dynamic fields:** `{AMOUNT}` = Integer points, `{URL}` = Suspicious redemption link
- **Expected classification:** SUSPICIOUS (Spam)
- **Expected risk:** 50–79
- **Expected reason:** Expiring reward baiting to redirect user to an external link.
- **What developer should test:** Vary the point counts. Result remains SUSPICIOUS.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 33

---

### TEST TEMPLATE 18 — Pending Package Fee Phishing
- **Template:** "You have a pending package delivery. Pay the ${AMOUNT} fee here {URL}"
- **Dynamic fields:** `{AMOUNT}` = Currency decimal, `{URL}` = Fake payment gateway link
- **Expected classification:** HIGH (Spam)
- **Expected risk:** 80–100
- **Expected reason:** Social engineering delivery scam attempting financial capture.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 26

---

### TEST TEMPLATE 19 — Unusual Login Secure Phishing
- **Template:** "Warning: Unusual login attempt on your account. Please secure it at {URL}"
- **Dynamic fields:** `{URL}` = External link
- **Expected classification:** HIGH (Spam)
- **Expected risk:** 80–100
- **Expected reason:** Security panic bait designed to steal credentials.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 24

---

### TEST TEMPLATE 20 — Confirm Delivery Info Phishing
- **Template:** "Action required: confirm your delivery details. {URL}"
- **Dynamic fields:** `{URL}` = External link
- **Expected classification:** SUSPICIOUS (Spam)
- **Expected risk:** 50–79
- **Expected reason:** Delivery tracking social engineering redirect.
- **SOURCE DATASET:** `finetune_data.csv`
- **SOURCE ROW(S):** Row 34
