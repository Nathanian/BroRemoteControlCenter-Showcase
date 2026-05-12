# Project Audit

## Existing Components
- **Commands**: Handlers for remote actions such as starting/stopping tunnels and device management (e.g., `StartTunnelCommandHandler`, `RebootCommandHandler`).
- **MQTT**: `MqttClientManager`, `MqttConnectionListener`, and `MqttMessageHandler` coordinate broker connectivity and message delivery.
- **Services**: Components like `RemoteControlService`, `JobHeartbeatService`, `WatchdogManager`, `BootReceiver`, and helper classes (e.g., `AlarmRestartHelper`, `HealthMonitor`) manage background work and lifecycle.
- **Utilities**: Helpers for service control, networking, configuration, logging, shell commands, and device information (`ServiceUtils`, `NetworkMonitor`, `BotConfigReader`, etc.).
- **UI**: Activities and fragments (`MainActivity`, `DiagnosticsActivity`, `TunnelViewFragment`, `SettingsActivity`) provide diagnostic and configuration interfaces.
- **Models**: `DeviceInfo` and `TunnelViewModel` expose application state.

## Newly Added Modules
Only the core `app` module is present; no additional Gradle modules are currently included in the project.

## API‑Level Guards
- `ServiceUtils.startForegroundServiceCompat` selects `startForegroundService` on API 26+ and falls back to `startService` on older versions.
- `RemoteControlService` creates notification channels on Android O+ and uses `quitSafely` for worker threads on API 18+.
- `AlarmRestartHelper` switches between `getForegroundService`/`getService` and uses `setAndAllowWhileIdle` on API 23+ to schedule alarms.
- `NetworkMonitor` registers a default network callback on API 24+ and falls back to a custom request on earlier releases.
