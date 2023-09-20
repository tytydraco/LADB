# LADB

A local ADB shell for Android!

# How does it work?

LADB bundles an ADB server within the app libraries. Normally, this server cannot connect to the local device because it
requires an active USB connection. However, Android's Wireless ADB Debugging feature allows the server and the client to
speak to each other locally.

# Initial Setup

Use split-screen more or a pop-out window with LADB and Settings at the same time. This is because Android will
invalidate the pairing information if the dialog gets dismissed. Add a Wireless Debugging connection, and copy the
pairing code and port into LADB. Keep both windows open until the Settings dialog dismisses itself.

Here is a nice tutorial from XDA on how to do it: [link](https://www.xda-developers.com/debloat-your-phone-run-adb-shell-commands-no-root-no-pc/).

Here is a video tutorial: [link](https://www.youtube.com/shorts/v7hT6rMYqh4).

# Issues

LADB is sadly incompatible with Shizuku at the current moment. That means that if you have Shiuzuku installed, LADB will
usually fail to connect properly. You must uninstall it and reboot to use LADB.

# Troubleshooting

Most errors can be fixed by clearing the app data for LADB, removing all Wireless Debugging connections from Settings,
and rebooting.

# License

While this project is GPLv3 licensed, I would like to add a parameter: please do not publish unofficial (user) LADB
builds to the Google Play Store.

# Support

Still confused? Email me at tylernij+LADB@gmail.com.

We also have a Telegram server here: https://t.me/ladb_support.

# Privacy Policy

LADB does not send any device data outside the app. Your data is not collected or processed.
