# iosls

Modul LSPosed untuk mengubah lockscreen XOS (Transsion) jadi bergaya iOS:
notification center dengan pill jumlah notifikasi + kartu bertumpuk, serta
entri **Restart SystemUI** di power menu.

Target: `com.android.systemui` (XOS/HiOS berbasis Android 16, SystemUI 36.2.0).

## Download

APK siap install ada di [`release/iosls-xos-0.1.0.apk`](release/iosls-xos-0.1.0.apk).

## Install

1. Install APK.
2. LSPosed → Modules → aktifkan **iOS Lock Screen XOS** → scope **System UI**.
3. Reboot, atau pakai power menu → **Restart SystemUI**.
4. Kunci layar, kirim notifikasi. Pill "N notifications" muncul; tap/swipe-up
   pada pill untuk menampilkan kartu.

Log modul memakai tag `iOSLS` (LSPosed → Logs, atau `adb logcat | grep iOSLS`).

## Build

Butuh JDK 17 + Android SDK build-tools 36 + platform 36. Toolchain tidak
di-commit; skrip build mengharapkan:

- `tools/jdk17/`
- `tools/sdk/build-tools/36.0.0/`
- `tools/sdk/platforms/android-36/android.jar`

Lalu jalankan:

```bash
bash tools/build_module.sh
```

Output: `build/iosls-xos-0.1.0.apk`. Keystore dev dibuat otomatis di
`tools/signing/iosls.jks` (tidak di-commit) — update APK harus memakai keystore
yang sama, kalau hilang harus uninstall dulu.

## Status

v0.1.0:

- Power menu → Restart SystemUI.
- Lockscreen: kartu notifikasi disembunyikan, pill jumlah notifikasi tampil,
  tap/swipe-up pada pill membuka kartu.

Belum: kunci jam depth saat notifikasi ada, gesture dua tahap (0 → 1 → 2), dan
overlap kartu bertumpuk.
