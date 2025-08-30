# Reddit Client ID
A Reddit Client ID is needed to access Reddit from 3rd party clients.

## Reddit Client ID creation steps
![Create application](/screenshots/create_application.png)

1. Go to [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps) and login if
necessary
2. Click `create another app...`. Do not re-use any Client ID for any app other
than Slide.
3. Set the name to Slide
4. Set the type to `installed app`
5. Set redirect uri to `http://www.ccrama.me`. If the redirect uri is set
incorrectly it won't work.
6. Complete the `reCAPTCHA`
7. Click `create app`
8. Copy the Client ID of your newly created app. It is recommended to save it
in the notes of your entry for Reddit in your password manager.

![Client ID](/screenshots/client_id.png)

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
3. Enter your Client ID and press `OK`
4. Wait for Slide to restart

**Changing the Client ID:**
1. Go to `Settings` in the side bar
1. Select [`General`](/screenshots/settings.png)
2. Press [`Reddit Client ID override`](screenshots/enter_client_id_override.png)
3. Enter your [Client ID](screenshots/pre-saved_client_id_override.png). It is
best to copy and paste it.
4. Press [`OK`](screenshots/pre-saved_client_id_override.png)
5. Press [`OK`](screenshots/post-saved_client_id_override.png)
6. Wait for Slide to restart
