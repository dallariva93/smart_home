"""Stampa lo stato grezzo dei dispositivi per scoprire capability/attributi reali.

Utile per verificare/aggiustare la mappatura dei field in poller.py: alcuni nomi di
attributo (in particolare i custom.* dei filtri) variano tra modelli di climatizzatore.
Mostra prima un elenco appiattito 'capability.attributo = valore', poi il JSON completo.
"""
import json
import os

import requests
from dotenv import load_dotenv

import oauth

load_dotenv()

CLIENT_ID = os.environ["ST_CLIENT_ID"]
CLIENT_SECRET = os.environ["ST_CLIENT_SECRET"]
STORE = oauth.TokenStore(os.environ.get("ST_TOKEN_STORE", "token_store.json"))
DEVICE_IDS = [d.strip() for d in os.environ.get("DEVICE_IDS", "").split(",") if d.strip()]


def main():
    token = oauth.get_access_token(CLIENT_ID, CLIENT_SECRET, STORE)
    for device_id in DEVICE_IDS:
        url = f"https://api.smartthings.com/v1/devices/{device_id}/status"
        resp = requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=30)
        resp.raise_for_status()
        data = resp.json()

        print(f"\n===== {device_id} =====")
        main_comp = data.get("components", {}).get("main", {})
        for cap, attrs in sorted(main_comp.items()):
            for attr, body in attrs.items():
                if isinstance(body, dict) and "value" in body:
                    unit = body.get("unit", "")
                    print(f"  {cap}.{attr} = {body.get('value')!r} {unit}".rstrip())
        print("\n--- JSON completo ---")
        print(json.dumps(data, indent=2))


if __name__ == "__main__":
    main()
