"""
train_model.py
==============
Generates synthetic demonstration data and trains a Random Forest Classifier
for knee OA preliminary risk assessment.

⚠️  IMPORTANT DISCLAIMER
   This script generates SYNTHETIC data. The trained model is for
   DEMONSTRATION / EDUCATIONAL PURPOSES ONLY and is NOT clinically validated.
   It must NOT be used for actual medical diagnosis.

Usage:
    python train_model.py
"""

import os
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, confusion_matrix
import joblib

# ─────────────────────────────────────────────────────────────────────────────
# 1. Synthetic data generation
# ─────────────────────────────────────────────────────────────────────────────
RANDOM_STATE = 42
N_SAMPLES    = 1500          # 500 per class
MODEL_VERSION = "1.0.0-demo"

np.random.seed(RANDOM_STATE)


def generate_synthetic_dataset(n_per_class: int = 500) -> pd.DataFrame:
    """
    Generate plausible synthetic sensor features for three OA risk classes.
    
    Clinical reasoning behind distributions (demonstration only):
    - LOW   : Smooth movement, low pain, normal flex/FSR, few vibration events.
    - MODERATE: Some movement irregularity, moderate pain, reduced flex range.
    - HIGH  : High movement variability, high pain, very restricted flex/FSR.
    """
    records = []

    for label, params in [
        ("LOW",      dict(accel_std=0.15, gyro_std=0.20, flex_mean=2200, fsr_mean=1800,
                          vib_count=2,  pain_score_mean=2, age_mean=45)),
        ("MODERATE", dict(accel_std=0.30, gyro_std=0.45, flex_mean=2800, fsr_mean=2400,
                          vib_count=8,  pain_score_mean=5, age_mean=58)),
        ("HIGH",     dict(accel_std=0.55, gyro_std=0.80, flex_mean=3400, fsr_mean=3200,
                          vib_count=18, pain_score_mean=8, age_mean=68)),
    ]:
        p = params
        for _ in range(n_per_class):
            ax_m = np.random.normal(0.02, p["accel_std"])
            ay_m = np.random.normal(-0.05, p["accel_std"])
            az_m = np.random.normal(9.81, p["accel_std"] * 0.5)
            ax_s = np.random.uniform(0.05, p["accel_std"] * 2)
            ay_s = np.random.uniform(0.05, p["accel_std"] * 2)
            az_s = np.random.uniform(0.02, p["accel_std"])

            gx_m = np.random.normal(0.01, p["gyro_std"])
            gy_m = np.random.normal(0.02, p["gyro_std"])
            gz_m = np.random.normal(-0.01, p["gyro_std"])
            gx_s = np.random.uniform(0.02, p["gyro_std"] * 1.8)
            gy_s = np.random.uniform(0.02, p["gyro_std"] * 1.8)
            gz_s = np.random.uniform(0.01, p["gyro_std"])

            accel_mag = np.sqrt(ax_m**2 + ay_m**2 + az_m**2)
            gyro_mag  = np.sqrt(gx_m**2 + gy_m**2 + gz_m**2)

            flex_m = np.random.normal(p["flex_mean"], 200)
            flex_s = np.random.uniform(50, 300)
            fsr_m  = np.random.normal(p["fsr_mean"],  250)
            fsr_s  = np.random.uniform(50, 350)

            vib = max(0, int(np.random.poisson(p["vib_count"])))
            pain = int(np.clip(np.random.normal(p["pain_score_mean"], 1.5), 0, 10))
            age  = int(np.clip(np.random.normal(p["age_mean"], 8), 18, 90))

            records.append({
                "accel_mean_x":         ax_m,
                "accel_mean_y":         ay_m,
                "accel_mean_z":         az_m,
                "accel_std_x":          ax_s,
                "accel_std_y":          ay_s,
                "accel_std_z":          az_s,
                "accel_magnitude_mean": accel_mag,
                "gyro_mean_x":          gx_m,
                "gyro_mean_y":          gy_m,
                "gyro_mean_z":          gz_m,
                "gyro_std_x":           gx_s,
                "gyro_std_y":           gy_s,
                "gyro_std_z":           gz_s,
                "gyro_magnitude_mean":  gyro_mag,
                "flex_mean":            flex_m,
                "flex_std":             flex_s,
                "fsr_mean":             fsr_m,
                "fsr_std":              fsr_s,
                "vibration_event_count": vib,
                "pain_score":           pain,
                "age":                  age,
                "risk_category":        label,
            })

    df = pd.DataFrame(records)
    df = df.sample(frac=1, random_state=RANDOM_STATE).reset_index(drop=True)
    return df


# ─────────────────────────────────────────────────────────────────────────────
# 2. Feature columns
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_COLUMNS = [
    "accel_mean_x", "accel_mean_y", "accel_mean_z",
    "accel_std_x",  "accel_std_y",  "accel_std_z",
    "accel_magnitude_mean",
    "gyro_mean_x",  "gyro_mean_y",  "gyro_mean_z",
    "gyro_std_x",   "gyro_std_y",   "gyro_std_z",
    "gyro_magnitude_mean",
    "flex_mean", "flex_std",
    "fsr_mean",  "fsr_std",
    "vibration_event_count",
    "pain_score",
    "age",
]

LABEL_COLUMN = "risk_category"
LABELS       = ["LOW", "MODERATE", "HIGH"]


# ─────────────────────────────────────────────────────────────────────────────
# 3. Train
# ─────────────────────────────────────────────────────────────────────────────
def train():
    print("=" * 60)
    print("  Smart Knee OA – Model Training  (DEMO / SYNTHETIC DATA)")
    print("=" * 60)

    df = generate_synthetic_dataset(n_per_class=N_SAMPLES // 3)
    df.to_csv("data/synthetic_training_data.csv", index=False)
    print(f"[+] Synthetic dataset saved: {len(df)} samples, {df[LABEL_COLUMN].value_counts().to_dict()}")

    X = df[FEATURE_COLUMNS].values
    y = df[LABEL_COLUMN].values

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=RANDOM_STATE, stratify=y)

    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", RandomForestClassifier(
            n_estimators=200,
            max_depth=12,
            min_samples_leaf=4,
            class_weight="balanced",
            random_state=RANDOM_STATE,
            n_jobs=-1,
        )),
    ])

    # Cross-validation
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=RANDOM_STATE)
    cv_scores = cross_val_score(pipeline, X_train, y_train, cv=cv, scoring="f1_macro")
    print(f"\n[+] 5-Fold CV F1 (macro): {cv_scores.mean():.3f} ± {cv_scores.std():.3f}")
    print("    NOTE: These scores are on synthetic data and have no clinical meaning.")

    pipeline.fit(X_train, y_train)

    # Evaluation on hold-out
    y_pred = pipeline.predict(X_test)
    print("\n[+] Hold-out test set classification report:")
    print(classification_report(y_test, y_pred, target_names=LABELS))
    print("[+] Confusion matrix:")
    print(confusion_matrix(y_test, y_pred, labels=LABELS))

    # Save artefacts
    os.makedirs("models", exist_ok=True)
    joblib.dump(pipeline, "models/rf_model.pkl")
    joblib.dump(FEATURE_COLUMNS, "models/feature_columns.pkl")

    metadata = {
        "model_version": MODEL_VERSION,
        "algorithm":     "RandomForestClassifier",
        "features":      FEATURE_COLUMNS,
        "labels":        LABELS,
        "is_demo":       True,
        "disclaimer":    (
            "This model is trained on SYNTHETIC data for demonstration purposes only. "
            "It is NOT clinically validated and must NOT be used for medical diagnosis."
        ),
        "n_samples":     len(df),
        "cv_f1_mean":    float(cv_scores.mean()),
        "cv_f1_std":     float(cv_scores.std()),
    }
    import json
    with open("models/model_metadata.json", "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"\n[+] Model saved to models/rf_model.pkl (version: {MODEL_VERSION})")
    print("[+] Training complete.\n")


if __name__ == "__main__":
    train()
