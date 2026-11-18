# Scanner Settings (DataWedge)

Small Android app that lets users change five DataWedge settings (Barcode input > Configure
scanner settings) without opening the full DataWedge UI:

| Setting                  | DataWedge parameter          | Control                        |
|--------------------------|------------------------------|--------------------------------|
| Aim timer                | `aim_timer` (0 - 60000)      | Number box + Set button        |
| Aim type                 | `aim_type` (0 - 8)           | Dropdown                       |
| Picklist mode            | `picklist` (0 - 2)           | Dropdown                       |
| Decode audio feedback    | `decode_audio_feedback_uri`  | Dropdown of system tones       |
| Decode haptic feedback   | `decode_haptic_feedback`     | Dropdown: On / Off             |

Each change sends a DataWedge `SET_CONFIG` intent that updates only that one parameter on one
existing profile. Reference:
https://techdocs.zebra.com/datawedge/latest/guide/api/setconfig/

## Customize

Edit `app/src/main/java/com/example/dwsettings/MainActivity.kt`:

- `PROFILE_NAME` - the DataWedge profile your users' app/browser is associated with.

## Known limits / things to verify on a real scanner

- The screen shows what was last set from this app, not a live read of the profile. A dropdown
  shows "(no change)" until someone picks a value.
- "Default" under audio feedback sends an empty string, which per Zebra resets the parameter to
  its default. How to switch the beep fully OFF still needs testing on a device.
- Support for these parameters varies by scanner model; the docs point to each device's
  Integrator Guide. Test on every model you deploy.
- The units/behaviour of the aim timer depend on the aim type and scanner. Check Zebra's
  reader-parameters docs.

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
