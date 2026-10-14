# nexus.net — Xray VPN client for Android

Kotlin + Jetpack Compose. Uses the official Xray core (via `libv2ray.aar`, pinned to `v26.10.10`)
with its built-in TUN inbound, so no extra tun2socks library is needed.

## Features
- Paste a **subscription URL** → servers, traffic, expiry, support/site links are loaded automatically
  (base64 link lists and Xray-JSON configs both work)
- Paste single **vless:// vmess:// trojan:// ss://** links, or raw **Xray JSON**
- VLESS + Reality + XHTTP, Shadowsocks, Trojan, VMess (ws / grpc / httpupgrade / h2 / tcp too)
- Latency test per server / whole provider (the ⚡ button), "Check" for the selected server
- Per-app split tunneling (bypass selected apps, or tunnel only selected apps)
- Russian and English UI (globe button or Settings)

## Build the APK
**Option A — GitHub (no setup):** create a repo, upload this folder, open *Actions → Build APK*;
the APK is attached to the run as the `nexus-net-apk` artifact (`app-universal-release.apk`).

**Option B — Android Studio:** open this folder, let it sync (the Xray core ~65 MB downloads
automatically into `app/libs/`), then *Build → Build APK(s)*.

**Option C — command line:** install Android SDK (platform 35) + JDK 17, then `./gradlew assembleRelease`.

The release build is signed with the debug key so it installs directly. Use your own keystore
before publishing anywhere.

## Notes
- The app itself is excluded from the tunnel (needed so the core's own sockets don't loop, and so
  subscription updates work while connected).
- Speeds on the main screen are this app's real network traffic (UID counters).
