# Setup
## Backup / Restore
You want to backup your settings before uninstalling other versions of Slide.
This will help save filters, accounts, etc. It is also a good idea in general.

### Backup and restore steps
1. Use `Backup to file` found in `Settings` under `Backup and restore`
2. Uninstall ccrama's or any other version of Slide
3. Install the Slide apk from this project's
[releases](https://github.com/edgan/Slide/releases) page
4. Use `Restore from file` found in `Settings` under `Backup and restore`
5. Select your dated backup `txt` file in `Downloads`
6. Close Slide
7. Open Slide

## Reddit Client ID
A Reddit Client ID is needed to access Reddit from 3rd party clients.

### Normal way
See [CLIENT_ID_NORMAL.md](/docs/CLIENT_ID_NORMAL.md)

### QR code way
See [CLIENT_ID_QR_CODE.md](/docs/CLIENT_ID_QR_CODE.md)

# Common errors
## Error: Invalid request to Oauth API
![Oauth error](/screenshots/oauth_error.png)

The most likely cause for this is the `redirect uri` is set incorrectly. The
big tell is if you can view Reddit in guest mode, aka without logging in.

## Keyboard won't come up for input fields on login screen
A new button has been added, in `7.4.3` to the top right of the Login screen. It uses your default
browser to open a custom `Chrome` tab. Note this requires your default browser to be a `Chrome`
based browser. `Firefox` has been tested, and does not work.

Also before trying to login with the new button you need to go to
`Settings | Apps | Slide | Open by Default` and add `www.ccrama.me`. This is for historical
reasons, and with the recent changes made to client IDs by `Reddit`, it can't be changed.

See [Issue 280](https://github.com/cygnusx-1-org/Slide/issues/280) for more details.

## Correct username and password does not work
Slide depends on
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
by default for logging into Reddit. So if having the login issue, your best
course of action would be to upgraded to the latest version of Android
possible, and then the latest version of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
possible.

Reddit's login password now requires
[XHR](https://en.wikipedia.org/wiki/XMLHttpRequest) to work. Older versions of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
don't support [XHR](https://en.wikipedia.org/wiki/XMLHttpRequest).

The current and known good version of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
is `131.0.6778.135`.

### WebView updating
Updating
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
can be tricky. You likely can't search and see it in the
[Google Play Store](https://play.google.com/store/games) app on your phone.

The best way is to find the app in the `Apps` section of `Settings`. The search
box in the top right can make it easier to find in the long list of apps. Once
you select
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
go to the bottom. You can see your version. Click on `App details`. This will
take you directly to
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview)
listing in the [Google Play Store](https://play.google.com/store/games) app. If
there is an update available it will be shown.

### Alternative versions of WebView
There are
[Dev](https://play.google.com/store/apps/details?id=com.google.android.webview.dev),
[Beta](https://play.google.com/store/apps/details?id=com.google.android.webview.beta),
and
[Canary](https://play.google.com/store/apps/details?id=com.google.android.webview.canary)
versions of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview).
These aren't recommended, but under rare circumstances they might be useful to
get a newer version of
[Android System Webview](https://play.google.com/store/apps/details?id=com.google.android.webview).

Once installed you need to enable
[Developer options](https://developer.android.com/studio/debug/dev-options),
you can go to them in `Settings`. Within is an option called
`WebView implementation` where you can pick which `WebView` is active.

## Notifications
See [NOTIFICATIONS.md](/NOTIFICATIONS.md)
