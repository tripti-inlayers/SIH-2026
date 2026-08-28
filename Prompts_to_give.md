# SancharSaathi Regression Test Sheet & Predefined SMS Templates

This document serves as the developer's regression test sheet for SancharSaathi's message security pipeline. It details 16 predefined template-style tests validated against existing training datasets.

> [!NOTE]
> These templates are validated against existing training and fine-tuning datasets and are intended for pipeline integration and fusion auditing. They do NOT constitute an independent machine learning test set.

---

### TEST TEMPLATE 01 — PhonePe Login OTP
- **Template:** "Your Phonepe login OTP is {OTP}"
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric verification code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Matches standard OTP structure. Since no URL is present, the local template matching runs instantly, bypassing backend.
- **Positive Variants:**
  - "Your Phonepe login OTP is 987123"
  - "Your Phonepe login OTP is 001928"
- **Negative/Contrast Variants:**
  - "PhonePe: URGENT verify your account login OTP at https://phonepe-secure.in to avoid block" (Expected: HIGH)

---

### TEST TEMPLATE 02 — Amazon Login OTP
- **Template:** "Your Amazon login OTP is {OTP}. Do not share this with anyone."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric verification code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Standard multi-factor authentication message. Local matcher returns LOW instantly.
- **Positive Variants:**
  - "Your Amazon login OTP is 112233. Do not share this with anyone."
  - "Your Amazon login OTP is 654321. Do not share this with anyone."
- **Negative/Contrast Variants:**
  - "Your Amazon login OTP is 123456. Confirm your login urgently at http://amazn-login-verify.com" (Expected: HIGH)

---

### TEST TEMPLATE 03 — Google Verification Code
- **Template:** "Use {OTP} as your verification code for Google."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric verification code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Standard login security check. Zero URLs present, resolves instantly locally as LOW.
- **Positive Variants:**
  - "Use 998822 as your verification code for Google."
  - "Use 102030 as your verification code for Google."
- **Negative/Contrast Variants:**
  - "Google: Google account security alert. Click here to verify http://g-security-auth.xyz" (Expected: HIGH)

---

### TEST TEMPLATE 04 — Netflix Verification Code
- **Template:** "Your Netflix verification code is {OTP}."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric verification code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Routine verification code. Resolves instantly locally as LOW.
- **Positive Variants:**
  - "Your Netflix verification code is 881290."
- **Negative/Contrast Variants:**
  - "Your Netflix account is suspended. Verify credentials at http://netflix-account-verify.club" (Expected: HIGH)

---

### TEST TEMPLATE 05 — Transaction OTP (HDFC)
- **Template:** "{OTP} is your OTP for transaction of Rs {AMOUNT} on HDFC Bank."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric verification code
  - `{AMOUNT}`: Numeric transaction amount (e.g. 500.00)
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Transaction authorization. Checked locally.
- **Positive Variants:**
  - "112244 is your OTP for transaction of Rs 15000 on HDFC Bank."
  - "002931 is your OTP for transaction of Rs 120.50 on HDFC Bank."
- **Negative/Contrast Variants:**
  - "HDFC Bank: Alert! Rs 15000 debited. If not done by you, block immediately at http://hdfc-fraud-alert.info" (Expected: HIGH)

---

### TEST TEMPLATE 06 — Transaction OTP (SBI)
- **Template:** "Never share your OTP. Your SBI bank OTP is {OTP}."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric verification code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Standard credit/debit card multi-factor authentication. Resolves instantly locally.
- **Positive Variants:**
  - "Never share your OTP. Your SBI bank OTP is 773322."
- **Negative/Contrast Variants:**
  - "Never share your OTP. SBI: Urgently verify your SBI bank account details to avoid blockade: http://sbi-verify.xyz" (Expected: HIGH)

---

### TEST TEMPLATE 07 — Swiggy Login Code
- **Template:** "Your Swiggy login code is {OTP}. Valid for {DURATION} minutes."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric login code
  - `{DURATION}`: Numeric time limit (e.g. 5)
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** E-commerce authentication token, does not match high urgency phishing. Resolves instantly locally.
- **Positive Variants:**
  - "Your Swiggy login code is 883921. Valid for 10 minutes."
- **Negative/Contrast Variants:**
  - "Swiggy order cancellation alert. Claim your refund immediately at http://swiggy-refund.click" (Expected: HIGH)

---

### TEST TEMPLATE 08 — Zomato Login
- **Template:** "Zomato: {OTP} is your OTP to login."
- **Dynamic variables:**
  - `{OTP}`: 6-digit numeric login code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Direct brand login code. Resolves instantly locally.
- **Positive Variants:**
  - "Zomato: 102033 is your OTP to login."
- **Negative/Contrast Variants:**
  - "Zomato: Congratulations! You won a free meal coupon. Redeem here: http://zomato-lucky-coupon.tk" (Expected: HIGH)

---

### TEST TEMPLATE 09 — Uber Login Code
- **Template:** "Your Uber code is {OTP}. Never share this code."
- **Dynamic variables:**
  - `{OTP}`: 4-digit or 6-digit numeric code
- **Expected Category:** `local_template` / `ml_model`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Transportation check-in/login warning. Resolves instantly locally.
- **Positive Variants:**
  - "Your Uber code is 1992. Never share this code."
- **Negative/Contrast Variants:**
  - "Uber: Your account has been reported for suspicious login. Confirm identity at http://uber-secure-support.com" (Expected: HIGH)

---

### TEST TEMPLATE 10 — Legitimate Package Delivery
- **Template:** "Your package from Amazon is out for delivery today. {URL}"
- **Dynamic variables:**
  - `{URL}`: Official secure domain link (e.g. amazon.in/track)
- **Expected Category:** `threat_intel` / `ml_model` / `url`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Since a URL is present, local templates alone are insufficient. The pipeline triggers PhishDestroy and ML model. Clean reputations keep the score low.
- **Positive Variants:**
  - "Your package from Amazon is out for delivery today. https://amazon.in/gp/track/12345"
- **Negative/Contrast Variants:**
  - "Your package from Amazon is pending due to unpaid address fees. Confirm details immediately: http://amazon-delivery-update.top" (Expected: HIGH)

---

### TEST TEMPLATE 11 — Legitimate Electricity Bill
- **Template:** "Your electricity bill of Rs {AMOUNT} is due on {DATE}. {URL}"
- **Dynamic variables:**
  - `{AMOUNT}`: Numeric bill total
  - `{DATE}`: Billing date string
  - `{URL}`: Official secure domain utility payment link
- **Expected Category:** `threat_intel` / `ml_model` / `url`
- **Expected Risk Class:** LOW (0–39)
- **Expected Behavior:** Triggers full PhishDestroy domain analysis and RoBERTa classification because of the URL. Safe URL check keeps overall score LOW.
- **Positive Variants:**
  - "Your electricity bill of Rs 4230 is due on 30-08-2026. https://billpay.bsesdelhi.com"
- **Negative/Contrast Variants:**
  - "Power cut tonight at 10 PM. Urgently pay your electricity bill of Rs 4230 to avoid disconnection: http://bses-bill-portal.xyz" (Expected: HIGH)

---

### TEST TEMPLATE 12 — Account Suspension Phishing
- **Template:** "URGENT: Your account has been suspended. Click here to verify your details {URL}"
- **Dynamic variables:**
  - `{URL}`: Obfuscated or malicious domain link
- **Expected Category:** `threat_intel` / `ml_model` / `message` / `url`
- **Expected Risk Class:** HIGH (80–100)
- **Expected Behavior:** Multi-signal trigger: local urgency rule matched (+1.8 pts), ML model spam classifier detects phishing (+22.5 pts), PhishDestroy flags the domain threat (+48.0 pts). Total exceeds 70, raising the score to the Confirmed Threat Floor of at least 80.
- **Positive Variants:**
  - "URGENT: Your account has been suspended. Click here to verify your details http://sbi-suspension-resolve.tk"
- **Negative/Contrast Variants:**
  - "Your monthly bank account statement is ready. Please view your details on netbanking." (Expected: LOW)

---

### TEST TEMPLATE 13 — KYC Verification Urgency Phishing
- **Template:** "URGENT: Verify your {ORG} KYC immediately to avoid suspension {URL}"
- **Dynamic variables:**
  - `{ORG}`: Legitimate organization name (e.g. PhonePe)
  - `{URL}`: Malicious or unverified external link
- **Expected Category:** `threat_intel` / `ml_model` / `message` / `url`
- **Expected Risk Class:** HIGH (80–100)
- **Expected Behavior:** Urgency and credential keywords trigger local rules, model identifies spam semantics, and URL threat lookup queries PhishDestroy, triggering the threat floor.
- **Positive Variants:**
  - "URGENT: Verify your Paytm KYC immediately to avoid suspension http://paytm-kyc-verify.net"
  - "URGENT: Verify your HDFC Bank KYC immediately to avoid suspension http://hdfc-kyc-update.work"
- **Negative/Contrast Variants:**
  - "Your KYC request has been successfully approved by HDFC Bank." (Expected: LOW)

---

### TEST TEMPLATE 14 — Reward Points Expiration Phishing
- **Template:** "Dear customer, you have {AMOUNT} reward points expiring today. {URL}"
- **Dynamic variables:**
  - `{AMOUNT}`: Integer points value
  - `{URL}`: Suspicious reward redemption URL
- **Expected Category:** `threat_intel` / `ml_model` / `message` / `url`
- **Expected Risk Class:** SUSPICIOUS to HIGH (50–100)
- **Expected Behavior:** Expiring points baiting. ML model detects bait language. PhishDestroy checks reputation. Score fused accordingly.
- **Positive Variants:**
  - "Dear customer, you have 9800 reward points expiring today. http://sbi-reward-points.xyz"
- **Negative/Contrast Variants:**
  - "Your credit card earned 250 points this cycle. Check your points summary in-app." (Expected: LOW)

---

### TEST TEMPLATE 15 — Pending Package Fee Phishing
- **Template:** "You have a pending package delivery. Pay the ${AMOUNT} fee here {URL}"
- **Dynamic variables:**
  - `{AMOUNT}`: Numeric delivery fee (e.g., 2.50)
  - `{URL}`: Unverified domain link
- **Expected Category:** `threat_intel` / `ml_model` / `message` / `url`
- **Expected Risk Class:** HIGH (80–100)
- **Expected Behavior:** Combines delivery fraud semantics with an external payment capture link. Fully audited via ML model and PhishDestroy.
- **Positive Variants:**
  - "You have a pending package delivery. Pay the $1.99 fee here http://indiapost-delivery-fee.icu"
- **Negative/Contrast Variants:**
  - "Your courier package 1823901 has been dispatched via India Post. Track at indiapost.gov.in" (Expected: LOW)

---

### TEST TEMPLATE 16 — Unusual Login Secure Phishing
- **Template:** "Warning: Unusual login attempt on your account. Please secure it at {URL}"
- **Dynamic variables:**
  - `{URL}`: External threat link
- **Expected Category:** `threat_intel` / `ml_model` / `message` / `url`
- **Expected Risk Class:** HIGH (80–100)
- **Expected Behavior:** High threat level panic bait triggering credential theft. ML and PhishDestroy both participate in scoring.
- **Positive Variants:**
  - "Warning: Unusual login attempt on your account. Please secure it at http://netflix-secure-auth.top"
- **Negative/Contrast Variants:**
  - "New login detected on your Netflix account from Chrome on Windows." (Expected: LOW)
