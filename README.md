# LADB
A local ADB shell for Android!

## How does it work?
LADB bundles an ADB server within the app libraries. Normally, this server cannot connect to the local device because it requires an active USB connection. However, Android's Wireless ADB Debugging feature allows the server and the client to speak to each other locally.

## Initial Setup:
1. "About phone" > Tap "Build number" 7 times
2. "System" > "Advanced" > "Developer options" > "Wireless debugging" in the "DEBUGGING" section > on/enable.
3. "Developer options" > ADB Debugging > on/enable

## Troubleshooting:
If you encounter "device unauthorized" or "multiple devices connected", try this:
1. Close LADB completely.
2. Disconnect any USB connection(s).
3. "Developer options" > "Wireless debugging" > off/disable
4. "Developer options" > "Revoke debugging authorizations"
5. Reboot/Restart the Device.
6. Start the process from the 2nd step of the section "Initial Setup".


Still confused? Email me at tylernij@gmail.com.
