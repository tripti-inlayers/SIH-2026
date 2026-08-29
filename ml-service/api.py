import logging
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

try:
    from detector import SpamMessageDetector
    MOCK_MODE = False
except ImportError as e:
    logging.warning(f"Could not import SpamMessageDetector: {e}. Running in MOCK mode.")
    MOCK_MODE = True

app = FastAPI(title="SancharSaathi ML API")

# Initialize the detector globally so it's loaded only once at startup
detector = None

class PredictRequest(BaseModel):
    message: str

class PredictResponse(BaseModel):
    prediction: int
    label: str
    confidence: float

class MockSpamMessageDetector:
    def __init__(self, model_path):
        self.model_path = model_path
    
    def detect(self, text, return_probs=False):
        lower = text.lower()
        if "indiapost" in lower or "gov.in" in lower:
            prediction = 0
            probs = [0.95, 0.05]
        elif "urgent" in lower or "pin" in lower or "xyz" in lower or "tk" in lower or "verify" in lower:
            prediction = 1
            probs = [0.1, 0.9]
        else:
            prediction = 0
            probs = [0.90, 0.10]

        if return_probs:
            return prediction, probs
        return prediction

import os

@app.on_event("startup")
async def load_model():
    global detector
    model_path = "./finetuned_model"
    model_exists = os.path.exists(model_path) and os.path.isdir(model_path) and len(os.listdir(model_path)) > 0

    if MOCK_MODE or not model_exists:
        logging.info(f"Initializing MOCK RoBERTa model (MOCK_MODE={MOCK_MODE}, model_exists={model_exists})...")
        detector = MockSpamMessageDetector(model_path=model_path)
    else:
        logging.info(f"Loading finetuned RoBERTa model from {model_path}...")
        try:
            detector = SpamMessageDetector(model_path=model_path)
            logging.info("Fine-tuned RoBERTa model loaded successfully.")
        except Exception as e:
            logging.error(f"Failed to load finetuned model: {e}. Falling back to MOCK detector.")
            detector = MockSpamMessageDetector(model_path=model_path)

@app.post("/predict", response_model=PredictResponse)
async def predict(request: PredictRequest):
    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="Message cannot be empty")
        
    try:
        prediction, probs = detector.detect(request.message, return_probs=True)
        # prediction is 1 for spam, 0 for ham
        label = "spam" if prediction == 1 else "ham"
        confidence = probs[prediction]
        
        return PredictResponse(
            prediction=prediction,
            label=label,
            confidence=confidence
        )
    except Exception as e:
        logging.error(f"Inference failed: {e}")
        raise HTTPException(status_code=500, detail="Inference failed")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
