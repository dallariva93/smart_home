"""Consenso OAuth2 una tantum: apre il browser, cattura il code, salva i token.

ESEGUIRE UNA VOLTA sul proprio PC (serve un browser). Dopo questo passaggio, il
poller sul Raspberry rinnova i token da solo, senza alcuna interfaccia.

Flusso:
 1. apre l'URL di autorizzazione SmartThings nel browser;
 2. dopo login + consenso, Samsung reindirizza a ST_REDIRECT_URI con un ?code=...;
 3. un mini-server locale temporaneo cattura il code;
 4. il code viene scambiato con access+refresh token, salvati in ST_TOKEN_STORE.
"""
import os
import sys
import urllib.parse
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer

from dotenv import load_dotenv

import oauth

load_dotenv()

CLIENT_ID = os.environ["ST_CLIENT_ID"]
CLIENT_SECRET = os.environ["ST_CLIENT_SECRET"]
REDIRECT_URI = os.environ.get("ST_REDIRECT_URI", "http://localhost:8080/callback")
SCOPES = os.environ.get("ST_SCOPES", "r:devices:*")
STORE = oauth.TokenStore(os.environ.get("ST_TOKEN_STORE", "token_store.json"))

AUTHORIZE_URL = "https://api.smartthings.com/oauth/authorize"

_received = {}


class _CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)
        code = params.get("code", [None])[0]
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        if code:
            _received["code"] = code
            self.wfile.write(
                "<h2>Autorizzazione completata. Puoi chiudere questa scheda.</h2>".encode()
            )
        else:
            self.wfile.write(
                f"<h2>Nessun code ricevuto.</h2><pre>{parsed.query}</pre>".encode()
            )

    def log_message(self, *args):
        pass  # silenzia i log del server


def _extract_code(pasted):
    """Estrae il code sia da un URL completo (?code=...) sia da un code incollato."""
    pasted = pasted.strip()
    if "code=" in pasted:
        params = urllib.parse.parse_qs(urllib.parse.urlparse(pasted).query)
        return params.get("code", [None])[0]
    return pasted or None


def get_code_via_server():
    """Avvia un mini-server locale che cattura il redirect con il code."""
    parsed = urllib.parse.urlparse(REDIRECT_URI)
    server = HTTPServer((parsed.hostname, parsed.port or 80), _CallbackHandler)
    print(f"In ascolto su {REDIRECT_URI} per il redirect...")
    while "code" not in _received:
        server.handle_request()
    return _received["code"]


def get_code_manual():
    """Fallback: l'utente incolla l'URL di redirect (o il solo code)."""
    pasted = input(
        "\nDopo aver autorizzato, incolla qui l'URL completo del redirect "
        "(o il solo code) e premi Invio:\n> "
    )
    code = _extract_code(pasted)
    if not code:
        print("Nessun code riconosciuto.")
        sys.exit(1)
    return code


def main():
    manual = "--manual" in sys.argv
    qs = urllib.parse.urlencode({
        "response_type": "code",
        "client_id": CLIENT_ID,
        "scope": SCOPES,
        "redirect_uri": REDIRECT_URI,
    })
    url = f"{AUTHORIZE_URL}?{qs}"
    print("Apro il browser per l'autorizzazione.")
    print("Se non si apre automaticamente, visita:\n  " + url)
    webbrowser.open(url)

    code = get_code_manual() if manual else get_code_via_server()

    print("Code ricevuto. Scambio con i token...")
    tok = oauth.exchange_code(CLIENT_ID, CLIENT_SECRET, code, REDIRECT_URI)
    STORE.save(tok)
    print(f"Token salvati in '{STORE.path}'. Setup completato.")


if __name__ == "__main__":
    main()
