"""
tests/test_ml_service.py
Tests for the Python ML service – model loading, prediction pipeline,
and FastAPI endpoints using TestClient.
"""

import pytest
import numpy as np
from pathlib import Path


# ─────────────────────────────────────────────────────────────────────────────
# Model / training tests (require model to be trained first)
# ─────────────────────────────────────────────────────────────────────────────
MODEL_AVAILABLE = (Path(__file__).parent.parent / "models" / "rf_model.pkl").exists()


@pytest.mark.skipif(not MODEL_AVAILABLE, reason="Model not trained yet – run train_model.py")
class TestModelLoading:
    def test_model_file_exists(self):
        assert (Path(__file__).parent.parent / "models" / "rf_model.pkl").exists()

    def test_feature_columns_file_exists(self):
        assert (Path(__file__).parent.parent / "models" / "feature_columns.pkl").exists()

    def test_metadata_file_exists(self):
        assert (Path(__file__).parent.parent / "models" / "model_metadata.json").exists()

    def test_model_predicts_valid_label(self):
        import joblib
        pipeline = joblib.load(Path(__file__).parent.parent / "models" / "rf_model.pkl")
        X = np.array([[0.02, -0.05, 9.81, 0.15, 0.15, 0.10, 9.82,
                        0.01,  0.02, -0.01, 0.20, 0.20, 0.10,  0.30,
                        2200.0, 100.0, 1800.0, 120.0,
                        2, 2, 45]])
        pred = pipeline.predict(X)
        assert pred[0] in ["LOW", "MODERATE", "HIGH"]

    def test_model_returns_probabilities(self):
        import joblib
        pipeline = joblib.load(Path(__file__).parent.parent / "models" / "rf_model.pkl")
        X = np.array([[0.02, -0.05, 9.81, 0.15, 0.15, 0.10, 9.82,
                        0.01,  0.02, -0.01, 0.20, 0.20, 0.10,  0.30,
                        2200.0, 100.0, 1800.0, 120.0,
                        2, 2, 45]])
        proba = pipeline.predict_proba(X)[0]
        assert len(proba) == 3
        assert abs(sum(proba) - 1.0) < 1e-6


# ─────────────────────────────────────────────────────────────────────────────
# FastAPI endpoint tests
# ─────────────────────────────────────────────────────────────────────────────
@pytest.mark.skipif(not MODEL_AVAILABLE, reason="Model not trained yet – run train_model.py")
class TestApiEndpoints:
    @pytest.fixture(autouse=True)
    def client(self):
        from fastapi.testclient import TestClient
        import sys
        sys.path.insert(0, str(Path(__file__).parent.parent))
        from main import app, load_model
        load_model()
        self.client = TestClient(app)

    def test_health_endpoint(self):
        r = self.client.get("/health")
        assert r.status_code == 200
        assert r.json()["status"] == "ok"

    def test_predict_with_all_features(self):
        payload = {
            "accel_mean_x": 0.02,  "accel_mean_y": -0.05, "accel_mean_z": 9.81,
            "accel_std_x":  0.15,  "accel_std_y":   0.15, "accel_std_z":  0.10,
            "accel_magnitude_mean": 9.82,
            "gyro_mean_x":  0.01,  "gyro_mean_y":   0.02, "gyro_mean_z": -0.01,
            "gyro_std_x":   0.20,  "gyro_std_y":    0.20, "gyro_std_z":   0.10,
            "gyro_magnitude_mean": 0.30,
            "flex_mean": 2200.0, "flex_std": 100.0,
            "fsr_mean":  1800.0, "fsr_std":  120.0,
            "vibration_event_count": 2,
            "pain_score": 2, "age": 45
        }
        r = self.client.post("/predict", json=payload)
        assert r.status_code == 200
        body = r.json()
        assert body["risk_category"] in ["LOW", "MODERATE", "HIGH"]
        assert "probabilities" in body
        assert body["is_demo"] is True

    def test_predict_with_missing_features(self):
        """Endpoint must still respond when some features are absent (imputation)."""
        payload = {"pain_score": 7, "age": 65}
        r = self.client.post("/predict", json=payload)
        assert r.status_code == 200
        body = r.json()
        assert body["risk_category"] in ["LOW", "MODERATE", "HIGH"]
        assert len(body["missing_features"]) > 0

    def test_predict_invalid_pain_score(self):
        payload = {"pain_score": 15}  # > 10, should fail validation
        r = self.client.post("/predict", json=payload)
        assert r.status_code == 422

    def test_model_info_endpoint(self):
        r = self.client.get("/model-info")
        assert r.status_code == 200
        info = r.json()
        assert "model_version" in info
        assert info["is_demo"] is True


# ─────────────────────────────────────────────────────────────────────────────
# Synthetic data generation test (no model required)
# ─────────────────────────────────────────────────────────────────────────────
class TestSyntheticDataGeneration:
    def test_dataset_shape(self):
        import sys
        sys.path.insert(0, str(Path(__file__).parent.parent))
        from train_model import generate_synthetic_dataset, FEATURE_COLUMNS, LABEL_COLUMN
        df = generate_synthetic_dataset(n_per_class=30)
        assert len(df) == 90
        for col in FEATURE_COLUMNS:
            assert col in df.columns
        assert df[LABEL_COLUMN].nunique() == 3

    def test_class_balance(self):
        import sys
        sys.path.insert(0, str(Path(__file__).parent.parent))
        from train_model import generate_synthetic_dataset, LABEL_COLUMN
        df = generate_synthetic_dataset(n_per_class=100)
        counts = df[LABEL_COLUMN].value_counts()
        assert (counts == 100).all()
