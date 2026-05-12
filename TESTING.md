# Manual Testing Guide

## Boot, Package Update, and Quick-Boot
1. Install the app and reboot the device.
2. Verify `RemoteControlService` starts automatically after boot.
3. Perform a package update (reinstall without uninstalling) and confirm the service resumes with existing settings.
4. Trigger a quick-boot/fast-boot cycle and ensure scheduled jobs and alarms still fire.

## Doze and Battery Optimization
1. Connect the device to USB and enable developer options to simulate Doze.
2. Run `adb shell dumpsys deviceidle force-idle` and verify health pings and scheduled alarms continue at the reduced cadence.
3. Add the app to the battery-optimization whitelist and confirm normal ping/heartbeat cadence resumes.

## Crash-and-Restart, Connectivity, and Timing
1. Force-stop or crash the process and observe that `AlarmRestartHelper` relaunches the service within the backoff window.
2. Toggle Wi-Fi and mobile data; ensure network listeners notify components and MQTT reconnects.
3. Use `adb shell cmd jobscheduler run -f <pkg> <id>` and `adb shell dumpsys alarm` to verify job and alarm timing.
