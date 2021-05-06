# LADB: A local ADB shell for Android!

## Working:
LADB bundles with an ADB server within the app. Normally, this server cannot connect to the local device because it requires an active USB connection. However, wireless ADB debugging feature of Android-11 allows the LADB to create a local server and pass-on the adb shell commands as if they are comming from a USB connected PC, but in reality without requiring any connection to a computer.

## Setup:
1. "About phone" > Tap "Build number" 7 times.
2. "System" > "Advanced" > "Developer options" > "Wireless debugging" in the "DEBUGGING" section > on/enable.
3. "Developer options" > ADB Debugging > on/enable.

## Troubleshooting:

If you encounter "device unauthorized" or "multiple devices connected":
1. Close LADB completely.
2. Disconnect any USB connection(s).
3. "Developer options" > "Wireless debugging" > off/disable.
4. "Developer options" > "Revoke debugging authorizations".
5. Reboot/Restart the Device.
6. Start the process from the 2nd step of the section "Initial Setup".


Still confused? e-mail me at:
tylernij@gmail.com
