# Android Remote Maintenance Center (Sanitized Public Showcase)

This repository is a **sanitized public demo** of an Android-based remote maintenance app for embedded/robotic systems.

> All customer-specific, infrastructure-specific, and credential-like values have been replaced with placeholders.

## What this project demonstrates

- Foreground `RemoteControlService` lifecycle for persistent device control.
- MQTT connectivity via Eclipse Paho with:
  - reconnect with exponential backoff + jitter,
  - Last Will/online-offline signaling,
  - command/topic subscription handling.
- Command execution flow (`ping`, tunnel start/stop, reboot, ADB checks, IP query).
- SSH tunnel management using JSch from Android (`TunnelViewModel`).
- Health/watchdog subsystem with ping/pong monitoring and periodic watchdog jobs.
- Basic diagnostics UI for MQTT state, backoff, and events.

## Sanitization notes

The original private values were replaced by placeholders such as:

- `YOUR_SERVER_IP`
- `YOUR_MQTT_BROKER`
- `YOUR_MQTT_USERNAME`
- `YOUR_MQTT_PASSWORD`
- `YOUR_TOPIC`
- `YOUR_CLIENT_ID`
- `YOUR_CUSTOMER`
- `YOUR_SERIAL_NUMBER`

Sensitive materials removed/replaced:

- Embedded private key material replaced with a placeholder file.
- Customer/device identity values in bundled config replaced.
- Hard-coded login topic values replaced by a configurable constant.

## Configuration

### 1) MQTT / infrastructure constants
Edit:

- `app/src/main/java/com/bro/brorcc/utils/Constants.java`

Set placeholders to your test environment values.

### 2) Device identity topic config
Use:

- `app/src/main/assets/bot_config.example.json` (template)

Copy to:

- `app/src/main/assets/bot_config.json`

and fill with your own non-sensitive demo values.

### 3) SSH key for tunnel demo
Replace:

- `app/src/main/assets/brovnc-key.pem`

with a test key appropriate for your own infrastructure.

## Architecture overview

- **Service layer**: `RemoteControlService` owns runtime behavior, command dispatch, and MQTT integration.
- **MQTT layer**: `MqttClientManager` encapsulates broker lifecycle, subscriptions, publish helpers, and reconnect strategy.
- **Tunnel layer**: `TunnelViewModel` handles SSH key provisioning and session/tunnel setup.
- **Watchdog/health**: `HealthMonitor`, `WatchdogManager`, `WatchdogScheduler`, and `JobHeartbeatService` supervise liveness and recovery.
- **Command handlers**: classes under `commands/` perform specific remote actions and send MQTT responses.

## Disclaimer

This repository is intended for portfolio/showcase and educational purposes. It is not bundled with production credentials, customer records, or private infrastructure details.
