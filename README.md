# Scanner Settings (DataWedge)

Small Android app that lets users change a handful of DataWedge settings without opening the
full DataWedge UI. Each toggle sends a DataWedge `SET_CONFIG` intent that updates one parameter
on one existing profile.

## Customize

Edit `app/src/main/java/com/example/dwsettings/MainActivity.kt`:

- `PROFILE_NAME` - the DataWedge profile that your users' app/browser is associated with.
- `SETTINGS` - one line per toggle. The parameter names are placeholders from Zebra's docs;
  verify them against your DataWedge version:
  https://techdocs.zebra.com/datawedge/latest/guide/api/setconfig/

## Build

Push to `main` (or run the "Build APK" workflow from the Actions tab). Download the APK from
the run's **Artifacts** section (it arrives zipped).

This produces a **debug** build, signed with a throwaway key. Before deploying to the fleet,
set up a release build signed with a key you keep, and reuse that same key for every update.

## Deployment notes

- Create the target profile on the scanners first (reference device -> export -> OEMConfig).
  The app only changes parameters on an existing profile.
- If you disable the DataWedge UI and restrict the DataWedge intent APIs to an allowlist
  (DataWedge Manager CSP), add this app (package `com.example.dwsettings`) to that allowlist.
- Add this app (not DataWedge) to the Managed Home Screen allowed apps list.
- Re-importing the profile through OEMConfig can overwrite users' choices; the app has a
  "Re-apply all settings" button for that case.
