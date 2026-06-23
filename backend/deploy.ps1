<#
.SYNOPSIS
  Deploy del backend sulla VM Oracle in un comando.

.DESCRIPTION
  Impacchetta SOLO il codice (app/, requirements.txt, deploy/) escludendo .venv,
  __pycache__ e i segreti, lo trasferisce sulla VM, lo estrae e riavvia il servizio.
  I file di stato/segreti sulla VM (.env, token_store_backend.json, *_config.json, …)
  NON vengono toccati.

.PARAMETER Deps
  Reinstalla anche le dipendenze (usa quando cambi requirements.txt).

.PARAMETER Env
  Invia anche il file .env locale (sovrascrive quello sulla VM).

.EXAMPLE
  .\deploy.ps1            # codice + restart
  .\deploy.ps1 -Deps      # codice + pip install + restart
  .\deploy.ps1 -Env       # codice + .env + restart
#>
param(
    [switch]$Deps,
    [switch]$Env
)
$ErrorActionPreference = "Stop"

# ---- CONFIG: compila una volta ----
$IP     = "<IP_PUBBLICO_VM>"                         # IP pubblico della VM Oracle
$KEY    = "$env:USERPROFILE\.ssh\oracle_clima"       # chiave SSH
$REMOTE = "clima/backend"                            # cartella sulla VM (relativa a ~)
$SERVICE = "clima-backend"
# ------------------------------------

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$tar = Join-Path $env:TEMP "clima-backend.tgz"

Write-Host "1/4  Impacchetto il codice..." -ForegroundColor Cyan
tar --exclude=__pycache__ --exclude=*.pyc -czf $tar -C $here app requirements.txt deploy

Write-Host "2/4  Trasferisco ed estraggo sulla VM..." -ForegroundColor Cyan
scp -i $KEY $tar "ubuntu@${IP}:/tmp/clima-backend.tgz"
ssh -i $KEY ubuntu@$IP "mkdir -p ~/$REMOTE && tar -xzf /tmp/clima-backend.tgz -C ~/$REMOTE && rm /tmp/clima-backend.tgz"

if ($Env) {
    Write-Host "   + invio .env" -ForegroundColor Cyan
    scp -i $KEY (Join-Path $here ".env") "ubuntu@${IP}:$REMOTE/.env"
}

if ($Deps) {
    Write-Host "3/4  Installo le dipendenze..." -ForegroundColor Cyan
    ssh -i $KEY ubuntu@$IP "cd ~/$REMOTE && .venv/bin/pip install -q -r requirements.txt"
} else {
    Write-Host "3/4  (dipendenze saltate; usa -Deps se requirements.txt e' cambiato)" -ForegroundColor DarkGray
}

Write-Host "4/4  Riavvio il servizio..." -ForegroundColor Cyan
$status = ssh -i $KEY ubuntu@$IP "sudo systemctl restart $SERVICE && sleep 2 && systemctl is-active $SERVICE"
if ($status -eq "active") {
    Write-Host "OK: deploy completato, servizio attivo." -ForegroundColor Green
} else {
    Write-Host "ATTENZIONE: il servizio risulta '$status'. Controlla: journalctl -u $SERVICE -n 50" -ForegroundColor Red
}
Remove-Item $tar -ErrorAction SilentlyContinue
