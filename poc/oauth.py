"""Gestione OAuth2 SmartThings: persistenza dei token e refresh automatico.

Punto cruciale: il refresh token RUOTA a ogni refresh (SmartThings ne restituisce
uno nuovo). Per questo salviamo SEMPRE su disco il set di token aggiornato, con
scrittura atomica, così un riavvio del Pi riprende dal refresh token valido.
"""
import base64
import json
import os
import time

import requests

TOKEN_URL = "https://auth-global.api.smartthings.com/oauth/token"
# margine prima della scadenza per evitare di usare un token "al limite"
EXPIRY_MARGIN_S = 120


class TokenStore:
    """Persistenza su file JSON dei token (access, refresh, scadenza assoluta)."""

    def __init__(self, path):
        self.path = path

    def load(self):
        if not os.path.exists(self.path):
            return None
        with open(self.path, "r", encoding="utf-8") as f:
            return json.load(f)

    def save(self, data):
        # scrittura atomica: prima su file temporaneo, poi rename
        tmp = self.path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        os.replace(tmp, self.path)


def _basic_auth_header(client_id, client_secret):
    raw = f"{client_id}:{client_secret}".encode()
    return "Basic " + base64.b64encode(raw).decode()


def _post_token(client_id, client_secret, data):
    resp = requests.post(
        TOKEN_URL,
        headers={
            "Authorization": _basic_auth_header(client_id, client_secret),
            "Content-Type": "application/x-www-form-urlencoded",
        },
        data=data,
        timeout=30,
    )
    resp.raise_for_status()
    return _normalize(resp.json())


def _normalize(tok):
    """Aggiunge il timestamp assoluto di scadenza dell'access token."""
    expires_in = int(tok.get("expires_in", 0))
    tok["expires_at"] = time.time() + expires_in - EXPIRY_MARGIN_S
    return tok


def exchange_code(client_id, client_secret, code, redirect_uri):
    """Scambia l'authorization code con access+refresh token (una tantum)."""
    return _post_token(client_id, client_secret, {
        "grant_type": "authorization_code",
        "client_id": client_id,
        "code": code,
        "redirect_uri": redirect_uri,
    })


def refresh(client_id, client_secret, refresh_token):
    """Rinnova i token usando il refresh token corrente."""
    return _post_token(client_id, client_secret, {
        "grant_type": "refresh_token",
        "client_id": client_id,
        "refresh_token": refresh_token,
    })


def get_access_token(client_id, client_secret, store):
    """Restituisce un access token valido, rinnovando e RISALVANDO se serve.

    Solleva RuntimeError se non esiste ancora un token salvato (va eseguito prima
    authorize.py per il consenso iniziale).
    """
    tok = store.load()
    if not tok:
        raise RuntimeError(
            "Nessun token salvato. Esegui prima 'python authorize.py' "
            "per il consenso iniziale."
        )
    if time.time() < tok.get("expires_at", 0):
        return tok["access_token"]

    # access token scaduto -> refresh + persistenza del nuovo set (refresh token ruota!)
    new_tok = refresh(client_id, client_secret, tok["refresh_token"])
    store.save(new_tok)
    return new_tok["access_token"]
