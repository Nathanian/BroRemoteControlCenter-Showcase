# Changelog

## [Unreleased]
- Health probe immediate reply with configurable 4s timeout
- Centralized HealthConfig for ping/pong intervals and probe window
- Ping jitter ±10% and MQTT reconnect exponential backoff (max 60s)
- Prevent double scheduling using persisted next_trigger
- Main-thread dispatch for MQTT callbacks
- Reboot command waits for publish ack before reboot
- Exact alarms for crash restarts and watchdog
- BootReceiver retries config check every 5 minutes up to 6 times
- Diagnostics screen shows health config, next triggers, MQTT backoff
- README updated with defaults and strategies
