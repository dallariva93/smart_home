# Guida per ottenere dati dal climatizzatore Samsung tramite SmartThings

## Prerequisiti
- Account Samsung con SmartThings configurato
- Climatizzatore Samsung già collegato a SmartThings
- Python 3.x installato (opzionale, per script automatizzati)

## Step 1: Ottenere un Personal Access Token (PAT)

1. Vai a https://account.smartthings.com/tokens
2. Accedi con il tuo account Samsung
3. Clicca su "Generate new token"
4. Inserisci un nome per il token (es. "Climatizzatore API")
5. Seleziona gli scope necessari:
   - `r:devices:*` - per leggere i dispositivi
   - `r:locations:*` - per leggere le location
6. Clicca su "Generate Token"
7. **IMPORTANTE**: Copia il token immediatamente, non sarà più visibile dopo

**Nota**: I PAT sono validi per 24 ore. Per uso a lungo termine, considera l'implementazione di OAuth2.0.

## Step 2: Identificare il Device ID del climatizzatore

### Opzione A: Tramite API REST
```bash
curl -X GET "https://api.smartthings.com/v1/devices" \
  -H "Authorization: Bearer <IL_TUO_TOKEN>"
```

La risposta conterrà tutti i dispositivi. Cerca quello con:
- `deviceType`: contiene "airConditioner" o simile
- `name`: il nome del tuo climatizzatore nell'app SmartThings
- Annota il `deviceId` (es. "abc123-def456-ghi789")

### Opzione B: Tramite Python (pysmartthings)
```python
import aiohttp
import asyncio
import pysmartthings

async def get_devices():
    token = 'IL_TUO_TOKEN'
    async with aiohttp.ClientSession() as session:
        api = pysmartthings.SmartThings(session, token)
        devices = await api.devices()
        for device in devices:
            print(f"Name: {device.name}")
            print(f"Device ID: {device.device_id}")
            print(f"Type: {device.device_type}")
            print("---")

asyncio.run(get_devices())
```

## Step 3: Ottenere lo stato completo del dispositivo

### Endpoint API
```
GET https://api.smartthings.com/v1/devices/{deviceId}/status
```

### Esempio con curl
```bash
curl -X GET "https://api.smartthings.com/v1/devices/{DEVICE_ID}/status" \
  -H "Authorization: Bearer <IL_TUO_TOKEN>"
```

### Esempio con Python
```python
import aiohttp
import asyncio

async def get_device_status():
    token = 'IL_TUO_TOKEN'
    device_id = 'IL_TUO_DEVICE_ID'
    
    url = f"https://api.smartthings.com/v1/devices/{device_id}/status"
    headers = {"Authorization": f"Bearer {token}"}
    
    async with aiohttp.ClientSession() as session:
        async with session.get(url, headers=headers) as response:
            data = await response.json()
            print(data)

asyncio.run(get_device_status())
```

## Step 4: Estrarre i dati specifici

La risposta dell'API contiene i dati organizzati per componenti e capabilities. Per un climatizzatore Samsung, tipicamente troverai:

### Temperatura
```json
{
  "components": {
    "main": {
      "temperatureMeasurement": {
        "temperature": {
          "value": 24.5,
          "unit": "C"
        }
      }
    }
  }
}
```

### Umidità (se supportata)
```json
{
  "components": {
    "main": {
      "relativeHumidityMeasurement": {
        "humidity": {
          "value": 45,
          "unit": "%"
        }
      }
    }
  }
}
```

### Stato del climatizzatore
```json
{
  "components": {
    "main": {
      "airConditionerMode": {
        "airConditionerMode": {
          "value": "cool"
        }
      },
      "thermostatCoolingSetpoint": {
        "coolingSetpoint": {
          "value": 22,
          "unit": "C"
        }
      },
      "fanMode": {
        "fanMode": {
          "value": "auto"
        }
      }
    }
  }
}
```

### Consumo energetico (se supportato)
```json
{
  "components": {
    "main": {
      "powerMeter": {
        "power": {
          "value": 1200,
          "unit": "W"
        }
      }
    }
  }
}
```

## Step 5: Script Python completo per estrarre tutti i dati

### Versione base (dati principali)
```python
import aiohttp
import asyncio
import json

async def get_ac_data():
    token = 'IL_TUO_TOKEN'
    device_id = 'IL_TUO_DEVICE_ID'
    
    url = f"https://api.smartthings.com/v1/devices/{device_id}/status"
    headers = {"Authorization": f"Bearer {token}"}
    
    async with aiohttp.ClientSession() as session:
        async with session.get(url, headers=headers) as response:
            if response.status == 200:
                data = await response.json()
                
                # Estrai i dati principali
                components = data.get('components', {})
                main = components.get('main', {})
                
                result = {
                    'timestamp': asyncio.get_event_loop().time(),
                    'device_id': device_id
                }
                
                # Temperatura
                temp_meas = main.get('temperatureMeasurement', {})
                if temp_meas:
                    temp = temp_meas.get('temperature', {})
                    result['temperature'] = temp.get('value')
                    result['temperature_unit'] = temp.get('unit')
                
                # Umidità
                humidity = main.get('relativeHumidityMeasurement', {})
                if humidity:
                    hum = humidity.get('humidity', {})
                    result['humidity'] = hum.get('value')
                    result['humidity_unit'] = hum.get('unit')
                
                # Modalità AC
                ac_mode = main.get('airConditionerMode', {})
                if ac_mode:
                    mode = ac_mode.get('airConditionerMode', {})
                    result['ac_mode'] = mode.get('value')
                
                # Setpoint temperatura
                setpoint = main.get('thermostatCoolingSetpoint', {})
                if setpoint:
                    sp = setpoint.get('coolingSetpoint', {})
                    result['cooling_setpoint'] = sp.get('value')
                    result['cooling_setpoint_unit'] = sp.get('unit')
                
                # Ventilatore
                fan = main.get('fanMode', {})
                if fan:
                    fm = fan.get('fanMode', {})
                    result['fan_mode'] = fm.get('value')
                
                # Potenza
                power = main.get('powerMeter', {})
                if power:
                    p = power.get('power', {})
                    result['power'] = p.get('value')
                    result['power_unit'] = p.get('unit')
                
                # Stato on/off
                switch = main.get('switch', {})
                if switch:
                    s = switch.get('switch', {})
                    result['power_state'] = s.get('value')
                
                print(json.dumps(result, indent=2))
                return result
            else:
                print(f"Error: {response.status}")
                print(await response.text())

asyncio.run(get_ac_data())
```

### Versione completa (tutte le capabilities Samsung)
```python
import aiohttp
import asyncio
import json
from datetime import datetime

async def get_ac_data_full():
    token = 'IL_TUO_TOKEN'
    device_id = 'IL_TUO_DEVICE_ID'
    
    url = f"https://api.smartthings.com/v1/devices/{device_id}/status"
    headers = {"Authorization": f"Bearer {token}"}
    
    async with aiohttp.ClientSession() as session:
        async with session.get(url, headers=headers) as response:
            if response.status == 200:
                data = await response.json()
                
                components = data.get('components', {})
                main = components.get('main', {})
                
                result = {
                    'timestamp': datetime.now().isoformat(),
                    'device_id': device_id
                }
                
                # Dati ambientali
                temp_meas = main.get('temperatureMeasurement', {})
                if temp_meas:
                    temp = temp_meas.get('temperature', {})
                    result['temperature'] = temp.get('value')
                    result['temperature_unit'] = temp.get('unit')
                
                humidity = main.get('relativeHumidityMeasurement', {})
                if humidity:
                    hum = humidity.get('humidity', {})
                    result['humidity'] = hum.get('value')
                    result['humidity_unit'] = hum.get('unit')
                
                air_quality = main.get('airQualitySensor', {})
                if air_quality:
                    aq = air_quality.get('airQuality', {})
                    result['air_quality'] = aq.get('value')
                
                odor = main.get('odorSensor', {})
                if odor:
                    o = odor.get('odorLevel', {})
                    result['odor_level'] = o.get('value')
                
                dust = main.get('dustSensor', {})
                if dust:
                    d = dust.get('dustLevel', {})
                    result['dust_level'] = d.get('value')
                
                fine_dust = main.get('veryFineDustSensor', {})
                if fine_dust:
                    fd = fine_dust.get('fineDustLevel', {})
                    result['fine_dust_level'] = fd.get('value')
                
                # Controllo climatizzatore
                switch = main.get('switch', {})
                if switch:
                    s = switch.get('switch', {})
                    result['power_state'] = s.get('value')
                
                ac_mode = main.get('airConditionerMode', {})
                if ac_mode:
                    mode = ac_mode.get('airConditionerMode', {})
                    result['ac_mode'] = mode.get('value')
                
                fan_mode = main.get('airConditionerFanMode', {})
                if fan_mode:
                    fm = fan_mode.get('fanMode', {})
                    result['fan_mode'] = fm.get('value')
                
                oscillation = main.get('fanOscillationMode', {})
                if oscillation:
                    osc = oscillation.get('fanOscillationMode', {})
                    result['oscillation_mode'] = osc.get('value')
                
                setpoint = main.get('thermostatCoolingSetpoint', {})
                if setpoint:
                    sp = setpoint.get('coolingSetpoint', {})
                    result['cooling_setpoint'] = sp.get('value')
                    result['cooling_setpoint_unit'] = sp.get('unit')
                
                # Consumo energetico
                power = main.get('powerConsumptionReport', {})
                if power:
                    p = power.get('powerConsumption', {})
                    result['power_consumption'] = p.get('value')
                    result['power_unit'] = p.get('unit')
                
                # Modalità speciali Samsung (custom)
                spi_mode = main.get('custom.spiMode', {})
                if spi_mode:
                    sm = spi_mode.get('spiMode', {})
                    result['spi_mode'] = sm.get('value')
                
                tropical_mode = main.get('custom.airConditionerTropicalNightMode', {})
                if tropical_mode:
                    tm = tropical_mode.get('tropicalNightMode', {})
                    result['tropical_night_mode'] = tm.get('value')
                
                auto_clean = main.get('custom.autoCleaningMode', {})
                if auto_clean:
                    ac = auto_clean.get('autoCleaningMode', {})
                    result['auto_cleaning_mode'] = ac.get('value')
                
                dnd_mode = main.get('custom.doNotDisturbMode', {})
                if dnd_mode:
                    dnd = dnd_mode.get('doNotDisturbMode', {})
                    result['do_not_disturb_mode'] = dnd.get('value')
                
                periodic_sensing = main.get('custom.periodicSensing', {})
                if periodic_sensing:
                    ps = periodic_sensing.get('periodicSensing', {})
                    result['periodic_sensing'] = ps.get('value')
                
                # Stato filtri
                dust_filter = main.get('custom.dustFilter', {})
                if dust_filter:
                    df = dust_filter.get('dustFilter', {})
                    result['dust_filter_status'] = df.get('value')
                
                fine_dust_filter = main.get('custom.veryFineDustFilter', {})
                if fine_dust_filter:
                    fdf = fine_dust_filter.get('veryFineDustFilter', {})
                    result['fine_dust_filter_status'] = fdf.get('value')
                
                deodor_filter = main.get('custom.deodorFilter', {})
                if deodor_filter:
                    dfr = deodor_filter.get('deodorFilter', {})
                    result['deodor_filter_status'] = dfr.get('value')
                
                hepa_filter = main.get('custom.electricHepaFilter', {})
                if hepa_filter:
                    hf = hepa_filter.get('electricHepaFilter', {})
                    result['hepa_filter_status'] = hf.get('value')
                
                print(json.dumps(result, indent=2))
                return result
            else:
                print(f"Error: {response.status}")
                print(await response.text())

asyncio.run(get_ac_data_full())
```

## Step 6: Installare dipendenze Python (se necessario)

```bash
pip install aiohttp pysmartthings
```

## Note importanti

### Limitazioni dei PAT
- I Personal Access Tokens scadono dopo 24 ore
- Per uso continuativo, implementa OAuth2.0
- Non condividere mai il token

### Rate limits
- List Devices: 1000 richieste per 15 minuti
- Get Device Status: 350 richieste per minuto
- Rispetta i limiti per evitare blocchi

### Capacità supportate
Non tutti i climatizzatori supportano tutte le capabilities. Verifica cosa è disponibile nel tuo dispositivo specifico controllando la risposta dell'API.

### Debug
Se ricevi errori:
- 401: Token non valido o scaduto
- 403: Permessi insufficienti
- 404: Device ID non corretto
- 429: Rate limit superato

## Esempio reale: Dispositivi nel tuo account SmartThings

Dalla chiamata all'endpoint `/devices`, il tuo account contiene:

### Climatizzatori Samsung Room A/C
1. **Condizionatore salotto**
   - Device ID: `d878b679-bfd2-b7a7-0c76-eeccb3bf500f`
   - Room ID: `83a71e17-cc84-4c33-b6d4-ab038f643ec8`

2. **Condozionatore camera**
   - Device ID: `dae77b84-a355-3757-3273-b21855e63db2`
   - Room ID: `68ca90c0-0bf4-4b2a-8215-40d9f115192b`

### Capabilities disponibili per i climatizzatori Samsung
I tuoi climatizzatori supportano le seguenti capabilities principali:

**Dati ambientali:**
- `temperatureMeasurement` - Temperatura ambiente
- `relativeHumidityMeasurement` - Umidità relativa
- `airQualitySensor` - Qualità dell'aria
- `odorSensor` - Sensore odori
- `dustSensor` - Sensore polvere
- `veryFineDustSensor` - Sensore polvere fine

**Controllo climatizzatore:**
- `switch` - On/Off
- `airConditionerMode` - Modalità (cool, heat, auto, etc.)
- `airConditionerFanMode` - Modalità ventilatore
- `fanOscillationMode` - Oscillazione ventilatore
- `thermostatCoolingSetpoint` - Setpoint temperatura raffreddamento

**Consumo energetico:**
- `powerConsumptionReport` - Report consumo energetico

**Modalità speciali Samsung (custom):**
- `custom.spiMode` - Modalità SPI
- `custom.airConditionerTropicalNightMode` - Modalità notte tropicale
- `custom.autoCleaningMode` - Modalità pulizia automatica
- `custom.doNotDisturbMode` - Modalità non disturbare
- `custom.periodicSensing` - Sensing periodico
- `custom.airConditionerOdorController` - Controllo odori

**Filtri:**
- `custom.dustFilter` - Filtro polvere
- `custom.veryFineDustFilter` - Filtro polvere fine
- `custom.deodorFilter` - Filtro deodorante
- `custom.electricHepaFilter` - Filtro HEPA elettrico

**Altri dispositivi nell'account:**
- OnePlus Nord 3 5G (presence sensor)
- Samsung 7 Series TV (50")

## Risorse utili

- [SmartThings API Documentation](https://developer.smartthings.com/docs/api/public/)
- [pysmartthings library](https://github.com/pySmartThings/pysmartthings)
- [SmartThings Developer Portal](https://developer.smartthings.com/)
