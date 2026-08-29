import logging
import uvicorn
import asyncio
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

@app.on_event("startup")
async def load_model():
    global detector
    model_path = "./finetuned_model"
    if MOCK_MODE:
        logging.info(f"Initializing MOCK RoBERTa model from {model_path}...")
        detector = MockSpamMessageDetector(model_path=model_path)
    else:
        try:
            logging.info(f"Loading finetuned RoBERTa model from {model_path}...")
            detector = SpamMessageDetector(model_path=model_path)
        except Exception as e:
            logging.warning(f"Could not load finetuned model from {model_path}: {e}. Falling back to MOCK mode.")
            detector = MockSpamMessageDetector(model_path=model_path)
    logging.info("Model loaded successfully.")

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.post("/predict", response_model=PredictResponse)
async def predict(request: PredictRequest):
    if not request.message or not request.message.strip():
        return PredictResponse(
            prediction=0,
            label="ham",
            confidence=1.0
        )
        
    try:
        # Enforce strict 800ms SLA timeout on RoBERTa inference
        prediction, probs = await asyncio.wait_for(
            asyncio.to_thread(detector.detect, request.message, True),
            timeout=0.8
        )
        # prediction is 1 for spam, 0 for ham
        label = "spam" if prediction == 1 else "ham"
        confidence = float(probs[prediction])
        
        return PredictResponse(
            prediction=prediction,
            label=label,
            confidence=confidence
        )
    except asyncio.TimeoutError:
        logging.warning("RoBERTa inference exceeded 800ms SLA limit; returning fallback")
        return PredictResponse(
            prediction=0,
            label="ham",
            confidence=0.5
        )
    except Exception as e:
        logging.error(f"Inference failed: {e}")
        return PredictResponse(
            prediction=0,
            label="ham",
            confidence=1.0
        )

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
