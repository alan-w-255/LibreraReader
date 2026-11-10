# Librera FD — Android TV / Dangbei X7 Pro build (branch `dangbei-tv`)

Minimal TV support on top of upstream master (10.0.31+), goal: remote-only reading
on the Dangbei X7 Pro projector. All changes are no-ops on touch devices.

## Changes
1. **AndroidManifest.xml**
   - `LEANBACK_LAUNCHER` intent-filter on `MainTabs2` (shows in TV launchers)
   - `android:banner="@drawable/tv_banner"` on `<application>`
   - `uses-feature`: `android.software.leanback` + `android.hardware.touchscreen`, both `required="false"` (installable on TV)
2. **Reader D-pad (always active, independent of the volume-keys setting)**
   - `DPAD_LEFT / DPAD_RIGHT`: next/prev page — already in upstream `PREV_KEYS`/`NEXT_KEYS` defaults
   - `DPAD_CENTER`: toggle reading menu (`doShowHideWrapperControlls()`) — added in both `HorizontalViewActivity` (paged) and `DocumentWrapperUI` (vertical scroll)
   - `DPAD_UP / DPAD_DOWN`: prev/next page in paged mode (`HorizontalViewActivity`); scroll in vertical mode (upstream already had it)
   - D-pad pages also dismiss open dialogs / clear text selection first, mirroring upstream key handling
3. **Library focus navigation**: `browse_item_grid.xml` / `browse_item_list.xml` item roots are now `focusable` with a `tv_focus_card` focused-border selector, so the D-pad moves through the book grid/list and OK opens the book. Star/menu buttons inside each card remain individually focusable.
4. **`app/src/fdroid/assets/fonts.zip`** built from `Builder/fonts/ttf` so custom fonts work offline.

## Build (ko machine)
```
# 1. MuPDF natives (see Builder/link_to_mupdf_1.28.5.sh; needs librera_ndk_dir in GRADLE_USER_HOME gradle.properties)
cd Builder && ./link_to_mupdf_1.28.5.sh
# 2. APK
cd .. && JAVA_HOME=~/android-build/jdk21 ANDROID_HOME=~/android-sdk GRADLE_USER_HOME=~/android-build/.gradle \
  ./gradlew --no-daemon assembleFdroidRelease
```
Signing via `RELEASE_STORE_FILE/...` properties (personal keystore, CN=Librera TV Dangbei).
