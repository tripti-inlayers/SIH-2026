# Patterns for message NLP analysis rule engine

URGENCY_PATTERNS = [
    r"act now", r"immediately", r"within \d+ hours?", r"within 24 hours",
    r"account will be (suspended|blocked|terminated|closed)",
    r"urgent action required", r"last chance", r"before it is too late",
    r"click to view", r"click here", r"track here", r"view order", r"click on the link",
    r"turant", r"abhi", r"24 ghante ke andar", r"account band ho jayega", r"aaj hi"
]

CREDENTIAL_PATTERNS = [
    r"password", r"otp", r"pin", r"cvv", r"login details", r"secret code",
    r"verify your pin", r"share (your )?otp", r"enter (your )?password",
    r"otp bhejein", r"pin verify karein", r"login karein"
]

FINANCIAL_PATTERNS = [
    r"bank account", r"payment", r"upi id", r"credit card", r"debit card",
    r"refund", r"cashback", r"transfer money", r"fee required", r"pay rs",
    r"paisa bhejein", r"khata", r"bank details"
]

REWARD_PATTERNS = [
    r"you have won", r"congratulations", r"claim your prize", r"lucky winner",
    r"lottery", r"free gift", r"inr \d+ reward", r"jeeta hai", r"inam"
]

THREAT_PATTERNS = [
    r"legal action", r"police report", r"arrest warrant", r"court order",
    r"penalty", r"fine will be imposed", r"electricity disconnected",
    r"bijli kat jayegi", r"legal notice"
]

GENERIC_SOCIAL_ENG_PATTERNS = [
    r"dear customer", r"valued user", r"kindly update", r"click here to verify",
    r"track your package", r"confirm your address", r"your order .* (shipped|delivered|dispatched)",
    r"order rs\.?\s*\d+", r"order #?\d+ has been shipped", r"delivery pending", r"parcel on the way"
]

ORG_NAMES = [
    "state bank", "sbi", "hdfc", "icici", "axis bank", "punjab national bank",
    "india post", "irctc", "airtel", "jio", "vi", "telecom", "electricity board"
]
