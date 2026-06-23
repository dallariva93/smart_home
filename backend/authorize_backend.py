"""Consenso OAuth una-tantum per l'app SmartThings DEDICATA al backend.

Esegui sul tuo PC (serve un browser), poi copia il file token sul server.
  python authorize_backend.py
Usa il redirect HTTPS configurato (default httpbin): dopo aver autorizzato, incolla
l'URL su cui sei finito (o il solo code) quando richiesto.
"""
import base64
import json
import sys
import time
import urllib.parse
import webbrowser

import httpx

from app.config import get_settings

AUTHORIZE_URL = "https://api.smartthings.com/oauth/authorize"
TOKEN_URL = "https://auth-global.api.smartthings.com/oauth/token"


def main():
    s = get_settings()
    qs = urllib.parse.urlencode({
        "response_type": "code",
        "client_id": s.st_client_id,
        "scope": s.st_scopes,
        "redirect_uri": s.st_redirect_uri,
    })
    url = f"{AUTHORIZE_URL}?{qs}"
    print("Apro il browser per l'autorizzazione del backend.")
    print("Se non si apre, visita:\n  " + url)
    webbrowser.open(url)

    pasted = input("\nIncolla l'URL di redirect (o il solo code): ").strip()
    code = pasted
    if "code=" in pasted:
        code = urllib.parse.parse_qs(urllib.parse.urlparse(pasted).query).get("code", [None])[0]
    if not code:
        print("Nessun code riconosciuto.")
        sys.exit(1)

    basic = "Basic " + base64.b64encode(f"{s.st_client_id}:{s.st_client_secret}".encode()).decode()
    r = httpx.post(
        TOKEN_URL,
        headers={"Authorization": basic, "Content-Type": "application/x-www-form-urlencoded"},
        data={
            "grant_type": "authorization_code",
            "client_id": s.st_client_id,
            "code": code,
            "redirect_uri": s.st_redirect_uri,
        },
        timeout=30,
    )
    r.raise_for_status()
    tok = r.json()
    tok["expires_at"] = time.time() + int(tok.get("expires_in", 0)) - 120
    with open(s.st_token_store, "w", encoding="utf-8") as f:
        json.dump(tok, f, indent=2)
    print(f"Token salvati in '{s.st_token_store}'. Copialo sul server Oracle.")


if __name__ == "__main__":
    main()
