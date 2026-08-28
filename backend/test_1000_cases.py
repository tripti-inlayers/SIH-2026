import random
import time
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

safe_templates = [
    ("Hi {name}, your OTP for login is {otp}.", "SBI-BANK", ""),
    ("Your package from Amazon is out for delivery today.", "AMZ-DEL", "https://amazon.in/track/{id}"),
    ("Reminder: Doctor appointment at {time} tomorrow.", "CLINIC", ""),
    ("Your electricity bill of Rs {amount} is due on {date}.", "BESCOM", "https://bescom.co.in/pay"),
    ("Salary of Rs {amount} credited to your A/c XX{acc}.", "HDFC-BK", ""),
]

suspicious_templates = [
    ("Your account needs verification. Please update your address.", "INFO", "http://update-info-address.com/verify"),
    ("Dear customer, you have 1000 reward points expiring today.", "REWARDS", "http://redeem-points-now.xyz"),
    ("Action required: confirm your delivery details.", "DELIVERY", "http://track-parcel-update.tk/confirm"),
]

phishing_templates = [
    ("URGENT: Your bank account will be suspended. Verify your PIN immediately to avoid blocking.", "URGENT", "http://secure-bank0findia-verify.xyz/login"),
    ("Income Tax Refund of Rs {amount} approved. Claim here:", "IT-DEPT", "http://incometaxindia-refund-gov.in.weebly.com"),
    ("Dear SBI User, your YONO account is blocked. Update PAN card link:", "SBI-UPD", "http://sbi-yono-kyc-update.com/login"),
    ("Your electricity power will be disconnected at 9:30 PM tonight. Call officer at {phone} or pay here.", "POWER", "http://pay-electricity-bill-online.xyz"),
    ("Win a free iPhone! Click here now to claim.", "UNKNOWN", "http://free-iphone-win-{id}.tk"),
]

def generate_case(i):
    rand = random.random()
    if rand < 0.4:
        category = safe_templates
        true_label = "LOW"
    elif rand < 0.7:
        category = suspicious_templates
        true_label = "SUSPICIOUS"
    else:
        category = phishing_templates
        true_label = "HIGH"
        
    template, sender, url_template = random.choice(category)
    
    text = template.format(
        name=random.choice(["Rahul", "Priya", "Amit", "Sneha"]),
        otp=random.randint(100000, 999999),
        id=random.randint(1000, 9999),
        time=random.choice(["10:00 AM", "2:30 PM", "4:15 PM"]),
        date=random.choice(["10th Oct", "12th Nov", "1st Dec"]),
        amount=random.randint(500, 50000),
        acc=random.randint(1000, 9999),
        phone=f"98{random.randint(10000000, 99999999)}"
    )
    
    url = url_template.format(id=random.randint(1000, 9999)) if url_template else ""
    urls = [url] if url else []
    
    if url:
        text += f" {url}"
        
    payload = {
        "message_id": f"MSG-{i:04d}",
        "text": text,
        "urls": urls,
        "sender_id": sender,
        "timestamp_epoch_millis": int(time.time() * 1000),
        "source": "SMS"
    }
    return payload, true_label

def run_tests():
    print("Running 1000 cases to calculate accuracy...")
    
    correct = 0
    total = 0
    
    confusion_matrix = {
        "LOW": {"LOW": 0, "SUSPICIOUS": 0, "HIGH": 0},
        "SUSPICIOUS": {"LOW": 0, "SUSPICIOUS": 0, "HIGH": 0},
        "HIGH": {"LOW": 0, "SUSPICIOUS": 0, "HIGH": 0}
    }
    
    examples = []
    
    for i in range(1, 51):
        payload, true_label = generate_case(i)
        response = client.post("/api/v1/analyze", json=payload)
        
        if response.status_code == 200:
            data = response.json()
            predicted_label = data.get("risk_level")
            
            confusion_matrix[true_label][predicted_label] += 1
            
            if predicted_label == true_label:
                correct += 1
            total += 1
            
            if len(examples) < 20:
                examples.append({
                    "text": payload["text"],
                    "expected": true_label,
                    "predicted": predicted_label
                })
        else:
            print(f"Error on case {i}: {response.status_code}")
            
        if i % 10 == 0:
            print(f"Processed {i}/50 cases...")
            
    accuracy = (correct / total) * 100 if total > 0 else 0
    
    print("\n" + "="*40)
    print("        TEST RESULTS (50 Cases)       ")
    print("="*40)
    print(f"Overall Accuracy: {accuracy:.2f}%\n")
    
    print("Performance by Category:")
    for label in ["LOW", "SUSPICIOUS", "HIGH"]:
        class_total = sum(confusion_matrix[label].values())
        class_correct = confusion_matrix[label][label]
        class_acc = (class_correct / class_total) * 100 if class_total > 0 else 0
        print(f" - {label}: {class_acc:.2f}% ({class_correct}/{class_total})")
        
    print("\nConfusion Matrix (True \ Predicted):")
    print(f"{'True \\ Pred':<15} | {'LOW':<10} | {'SUSPICIOUS':<10} | {'HIGH':<10}")
    print("-" * 55)
    for true_lbl in ["LOW", "SUSPICIOUS", "HIGH"]:
        low_pred = confusion_matrix[true_lbl]["LOW"]
        susp_pred = confusion_matrix[true_lbl]["SUSPICIOUS"]
        high_pred = confusion_matrix[true_lbl]["HIGH"]
        print(f"{true_lbl:<15} | {low_pred:<10} | {susp_pred:<10} | {high_pred:<10}")

    print("\n" + "="*40)
    print("        20 SAMPLE EXAMPLES       ")
    print("="*40)
    for idx, ex in enumerate(examples[:20]):
        print(f"[{idx+1}] Text: {ex['text']}")
        print(f"    Expected: {ex['expected']} | Predicted: {ex['predicted']}")
        print("-" * 40)

if __name__ == "__main__":
    run_tests()
