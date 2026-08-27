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
        if "urgent" in text.lower() or "http" in text.lower():
            prediction = 1
            probs = [0.1, 0.9]
        else:
            prediction = 0
            probs = [0.95, 0.05]
        if return_probs:
            return prediction, probs
        return prediction

@app.on_event("startup")
async def load_model():
    global detector
    model_path = "/Users/vaibhav/.gemini/antigravity/scratch/phishing-detector-ai/finetuned_model"
    if MOCK_MODE:
        logging.info(f"Initializing MOCK RoBERTa model from {model_path}...")
        detector = MockSpamMessageDetector(model_path=model_path)
    else:
        logging.info(f"Loading finetuned RoBERTa model from {model_path}...")
        detector = SpamMessageDetector(model_path=model_path)
    logging.info("Model loaded successfully.")

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
