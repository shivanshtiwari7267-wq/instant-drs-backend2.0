"""
Central configuration for sports supported by Tool 3.
This allows us to easily add specific trained models for different sports later.
"""

import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OPENVINO_MODEL_PATH = os.path.join(BASE_DIR, "yolov8n_openvino_model")

SPORTS_CONFIG = {
    "cricket": {
        "display_name": "Cricket",
        "model_path": OPENVINO_MODEL_PATH, # using exported OpenVINO FP32 model
        "ball_class_id": 32, # 'sports ball' in COCO dataset
        "confidence_threshold": 0.2
    },
    "volleyball": {
        "display_name": "Volleyball",
        "model_path": "yolov8n.pt",
        "ball_class_id": 32,
        "confidence_threshold": 0.2
    },
    "basketball": {
        "display_name": "Basketball",
        "model_path": "yolov8n.pt",
        "ball_class_id": 32,
        "confidence_threshold": 0.2
    },
    "football": {
        "display_name": "Football",
        "model_path": "yolov8n.pt",
        "ball_class_id": 32,
        "confidence_threshold": 0.2
    },
    "tennis": {
        "display_name": "Tennis",
        "model_path": "yolov8n.pt",
        "ball_class_id": 32,
        "confidence_threshold": 0.2
    }
}

def get_sport_config(sport_name: str) -> dict:
    """
    Retrieves the configuration for a given sport.
    
    Args:
        sport_name (str): The name of the sport (e.g., 'cricket', 'volleyball')
        
    Returns:
        dict: The configuration dictionary for the sport, or None if not found.
    """
    return SPORTS_CONFIG.get(sport_name.lower())

def get_supported_sports() -> list:
    """Returns a list of supported sport names."""
    return list(SPORTS_CONFIG.keys())
