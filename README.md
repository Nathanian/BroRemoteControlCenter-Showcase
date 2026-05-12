# Bro Remote Control Center Showcase

A showcase version of my Android-based remote maintenance and control platform for embedded and robotic systems.

This project was designed to remotely monitor, manage, and maintain Android-based robots and embedded devices over unstable customer networks using lightweight communication and tunneling technologies.

The application focuses on reliability, low hardware requirements, and compatibility with older Android systems commonly used in robotics environments.

## Features

* MQTT-based remote communication
* Remote command execution
* SSH tunnel integration
* Android foreground service architecture
* Automatic reconnect and watchdog systems
* Persistent background operation
* Device status monitoring
* Embedded-system friendly architecture
* Android 7 compatibility
* Remote maintenance workflows
* Configurable infrastructure setup
* Lightweight network communication

## Technical Highlights

* Java-based Android application
* MQTT communication architecture
* SSH tunneling workflows
* Foreground/background Android services
* Watchdog and reconnect handling
* Broadcast receiver integration
* Embedded/robotics-oriented system design
* Service lifecycle management
* Network resilience handling
* Config-based infrastructure abstraction

## Example Use Cases

* Remote debugging of Android robots
* Remote app maintenance
* Network diagnostics
* Remote support workflows
* Embedded Android device management
* Fleet maintenance scenarios

## Architecture Overview

The application uses a lightweight communication architecture:

1. Android device establishes MQTT connectivity
2. Commands are received remotely through subscribed topics
3. SSH tunneling can be initiated dynamically
4. Remote debugging and maintenance tools connect through the tunnel
5. Watchdog and reconnect systems maintain reliability during unstable network conditions

## Android Compatibility

This project was intentionally designed for compatibility with older Android devices, especially Android 7 (API 24/25), which are still commonly used in embedded and robotics hardware.

## Public Showcase Notes

This repository is a sanitized public showcase version of an internal production-oriented project.

The following were removed or replaced:

* Internal infrastructure references
* Server IPs and domains
* Credentials and sensitive configuration
* Customer-specific data
* Proprietary assets and setup information

Placeholder values are used where required.

## Tech Stack

* Java
* Android SDK
* MQTT
* SSH
* Android Services
* Broadcast Receivers
* Gradle

## Screenshots

<img width="990" height="613" alt="ControlCenter" src="https://github.com/user-attachments/assets/52439a09-8b0a-46a5-8b3c-b9231c9b641f" />

## Author

Jan Herold
Application Developer / Android & Robotics Development
