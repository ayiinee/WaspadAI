from __future__ import annotations

import os
from pathlib import Path
from dotenv import load_dotenv

from dotenv import dotenv_values

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ROOT_ENV = PROJECT_ROOT / ".env"
BACKEND_ENV = Path(__file__).resolve().parents[1] / ".env"

if BACKEND_ENV.is_file():
    load_dotenv(BACKEND_ENV)

if ROOT_ENV.is_file():
    load_dotenv(ROOT_ENV)

for env_file in (ROOT_ENV, BACKEND_ENV):
    if env_file.is_file():
        for key, value in dotenv_values(env_file).items():
            if key and key.startswith("WASPADAI_") and key not in os.environ and value is not None:
                os.environ[key] = value
