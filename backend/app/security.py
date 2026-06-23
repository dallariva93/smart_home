"""Autenticazione semplice app -> backend tramite header X-API-Key."""
from fastapi import Header, HTTPException

from .config import get_settings


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if x_api_key != get_settings().app_api_key:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")
