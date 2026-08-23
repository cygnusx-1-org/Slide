## Reddit Client ID
A Reddit Client ID is needed to access Reddit from 3rd party clients.

## Alternative browser extensions
These let you select text, right click, and generate a QR code

### Chrome
[QR Code Generator](https://chromewebstore.google.com/detail/qr-code-generator/afpbjjgbdimpioenaedcjgkaigggcdpp?hl=en-US)

### Firefox
[Offline QR Code Generator](https://addons.mozilla.org/en-US/firefox/addon/offline-qr-code-generator/)

## Reddit Client ID creation steps
![Create application](/screenshots/create_application.png)

1. Install the [reddit-app-helper](https://github.com/cygnusx-1-org/reddit-app-helper/releases) browser extension.
  * `.zip` for [Chrome](https://www.google.com/chrome/)
  * `.xpi` for [Firefox](https://www.mozilla.org/en-US/firefox/new/)
2. Go to [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps) and login if
necessary
3. Click `create another app...`. Do not re-use any Client ID for any app other
than Slide.
4. Set the name to Slide
5. Set the type to `installed app`
6. Set redirect uri to `http://www.ccrama.me`. If the redirect uri is set
incorrectly it won't work.
7. Complete the `reCAPTCHA`
8. Click `create app`
9. Reload the [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps) page.
10. Click `[Show QR]` button to display the QR code for the `Slide` client ID.

![Client ID](/screenshots/client_id_with_show_qr_button.png)

> [!NOTE]
>
> This is just an example Client ID. It was created and deleted. Keep
> yours private.

## Adding a Reddit Client ID to Slide
The method of adding a Client ID to Slide depends on whether this is the
first time the app is being set up.

**Tutorial setup:**
1. Open Slide and press `GET STARTED`
2. Select your theme colors, if you like, and press `DONE`
3. Press camera button, and scan the QR code on the
[reddit.com/prefs/apps](https://www.reddit.com/prefs/apps) page.
4. Wait for Slide to restart

**Changing the Client ID:**
1. Go to `Settings` in the side bar
1. Select [`General`](/screenshots/settings.png)
2. Press [`Reddit Client ID override`](screenshots/enter_client_id_override.png)
3. Press camera button and scan the QR code on the
[reddit.com/prefs/apps](https://www.reddit.com/prefs/apps) page.

![Client ID](/screenshots/client_id_with_qr_code.png)

> [!NOTE]
>
> This is just an example Client ID and QR code. It was created and deleted.
> Keep both private.

4. Press [`OK`](screenshots/pre-saved_client_id_override.png)
5. Press [`OK`](screenshots/post-saved_client_id_override.png)
6. Wait for Slide to restart
