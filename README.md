# LADB
A local ADB shell for Android!

# How does it work?
LADB bundles an ADB server within the app libraries. Normally, this server cannot connect to the local device because it requires an active USB connection. However, Android's Wireless ADB Debugging feature allows the server and the client to speak to each other locally.

# Initial Setup:
1. About -> Build Number -> Click 7 times
2. Developer Settings -> Wireless ADB Debugging -> On
3. Developer Settings -> ADB Debugging -> On

# Troubleshooting:
If you encounter "device unauthorized" or "multiple devices connected", try this:
1. Enable Airplane Mode
2. Disconnect any USB devices
3. Kill and restart LADB

Still not working? Try this:
1. Close LADB completely
2. Developer Settings -> Wireless ADB Debugging -> Off
3. Developer Settings -> ADB Debugging -> Off
4. Developer Settings -> Revoke authorizations
5. Reboot
6. Developer Settings -> Wireless ADB Debugging -> On
7. Developer Settings -> ADB Debugging -> On
8. Enable Airplane Mode
9. Open LADB

# Credit
Thanks to Surge1223 for compiling ADB for the ARM/ARM64 architecture.

# License
While this project is GPLv3 licensed, I would like to add an additional parameter: please do not publish unofficial (user) LADB builds to the Google Play Store.

Still confused? Email me at tylernij@gmail.com.
