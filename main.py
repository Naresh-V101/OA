"""
main.py  –  Smart Knee OA ML Service  (FastAPI)
================================================
Endpoints:
  GET  /health        – liveness check
  POST /predict       – run Random Forest prediction
  GET  /model-info    – model metadata

⚠️  DISCLAIMER: All predictions are produced by a model trained on SYNTHETIC
    data. Results are for DEMONSTRATION / EDUCATIONAL purposes only and
    are NOT clinically validated. They must NOT be used for medical diagnosis.
"""

from __future__ import annotations

import json
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ─────────────────────────────────────────────────────────────────────────────
# App bootstrap
# ─────────────────────────────────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load the model at startup (FastAPI lifespan event)."""
    load_model()
    yield


app = FastAPI(
    title="Smart Knee OA – ML Service",
    description=(
        "Preliminary knee osteoarthritis risk estimation using a Random Forest "
        "trained on SYNTHETIC data. For demonstration/educational use only."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─────────────────────────────────────────────────────────────────────────────
# Model loading
# ─────────────────────────────────────────────────────────────────────────────
BASE_DIR = Path(__file__).parent
MODEL_PATH    = BASE_DIR / "models" / "rf_model.pkl"
META_PATH     = BASE_DIR / "models" / "model_metadata.json"
FEATURES_PATH = BASE_DIR / "models" / "feature_columns.pkl"

_pipeline        = None
_metadata: dict  = {}
_feature_columns = []

LABELS = ["LOW", "MODERATE", "HIGH"]


def load_model():
    global _pipeline, _metadata, _feature_columns
    if not MODEL_PATH.exists():
        raise RuntimeError(
            f"Model file not found: {MODEL_PATH}. "
            "Please run train_model.py first."
        )
    _pipeline        = joblib.load(MODEL_PATH)
    _feature_columns = joblib.load(FEATURES_PATH)
    with open(META_PATH) as f:
        _metadata = json.load(f)


# Startup is handled by the lifespan context manager above


# ─────────────────────────────────────────────────────────────────────────────
# Schemas
# ─────────────────────────────────────────────────────────────────────────────
class PredictRequest(BaseModel):
    # Accelerometer
    accel_mean_x:         Optional[float] = None
    accel_mean_y:         Optional[float] = None
    accel_mean_z:         Optional[float] = None
    accel_std_x:          Optional[float] = None
    accel_std_y:          Optional[float] = None
    accel_std_z:          Optional[float] = None
    accel_magnitude_mean: Optional[float] = None
    # Gyroscope
    gyro_mean_x:          Optional[float] = None
    gyro_mean_y:          Optional[float] = None
    gyro_mean_z:          Optional[float] = None
    gyro_std_x:           Optional[float] = None
    gyro_std_y:           Optional[float] = None
    gyro_std_z:           Optional[float] = None
    gyro_magnitude_mean:  Optional[float] = None
    # Flex
    flex_mean:            Optional[float] = None
    flex_std:             Optional[float] = None
    # FSR
    fsr_mean:             Optional[float] = None
    fsr_std:              Optional[float] = None
    # Vibration
    vibration_event_count: Optional[int]  = None
    # Clinical context
    pain_score:           Optional[float] = Field(None, ge=0, le=10)
    age:                  Optional[int]   = Field(None, ge=1, le=129)


class PredictResponse(BaseModel):
    risk_category:  str
    probabilities:  dict[str, float]
    prediction_score: float
    model_version:  str
    is_demo:        bool
    disclaimer:     str
    missing_features: list[str]


# ─────────────────────────────────────────────────────────────────────────────
# Helper: build feature vector, imputing missing values
# ─────────────────────────────────────────────────────────────────────────────
# Imputation defaults derived from synthetic training-data statistics
IMPUTATION_DEFAULTS: dict[str, float] = {
    "accel_mean_x":          0.02,
    "accel_mean_y":         -0.05,
    "accel_mean_z":          9.81,
    "accel_std_x":           0.30,
    "accel_std_y":           0.30,
    "accel_std_z":           0.15,
    "accel_magnitude_mean":  9.82,
    "gyro_mean_x":           0.01,
    "gyro_mean_y":           0.02,
    "gyro_mean_z":          -0.01,
    "gyro_std_x":            0.45,
    "gyro_std_y":            0.45,
    "gyro_std_z":            0.22,
    "gyro_magnitude_mean":   0.50,
    "flex_mean":            2800.0,
    "flex_std":              200.0,
    "fsr_mean":             2400.0,
    "fsr_std":               250.0,
    "vibration_event_count":   8.0,
    "pain_score":              5.0,
    "age":                    55.0,
}


def build_feature_vector(req: PredictRequest) -> tuple[np.ndarray, list[str]]:
    req_dict = req.model_dump()
    missing  = []
    values   = []

    for col in _feature_columns:
        val = req_dict.get(col)
        if val is None:
            missing.append(col)
            val = IMPUTATION_DEFAULTS.get(col, 0.0)
        values.append(float(val))

    return np.array(values).reshape(1, -1), missing


# ─────────────────────────────────────────────────────────────────────────────
# Endpoints
# ─────────────────────────────────────────────────────────────────────────────
@app.get("/health", tags=["Utility"])
def health():
    model_loaded = _pipeline is not None
    return {
        "status":        "ok" if model_loaded else "model_not_loaded",
        "model_version": _metadata.get("model_version", "unknown"),
        "is_demo":       True,
    }


@app.post("/predict", response_model=PredictResponse, tags=["Prediction"])
def predict(req: PredictRequest):
    if _pipeline is None:
        raise HTTPException(503, "Model not loaded. Run train_model.py first.")

    X, missing = build_feature_vector(req)

    proba = _pipeline.predict_proba(X)[0]
    classes = _pipeline.classes_

    prob_dict = {cls: float(p) for cls, p in zip(classes, proba)}
    # Ensure all three labels present
    for lbl in LABELS:
        prob_dict.setdefault(lbl, 0.0)

    predicted_label = classes[int(np.argmax(proba))]
    prediction_score = float(np.max(proba))

    return PredictResponse(
        risk_category    = predicted_label,
        probabilities    = prob_dict,
        prediction_score = prediction_score,
        model_version    = _metadata.get("model_version", "unknown"),
        is_demo          = True,
        disclaimer       = (
            "⚠️ DEMONSTRATION ONLY – This result is generated by a model trained on "
            "SYNTHETIC data. It is NOT clinically validated and must NOT be used for "
            "medical diagnosis or to replace professional medical advice."
        ),
        missing_features = missing,
    )


@app.get("/model-info", tags=["Utility"])
def model_info():
    if not _metadata:
        raise HTTPException(503, "Model metadata not available.")
    return _metadata
