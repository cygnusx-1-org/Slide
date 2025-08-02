# Android device debugging via USB

## What
You can get crash logs from your `Android` device with `USB debugging` and `adb`.

## Why
Crash logs, aka backtraces, make it sigificantly easier to fix the bug that caused the crash.

## How
### Further documentation
[Official documentation](https://developer.android.com/studio/debug/dev-options)

[XDA documentation](https://www.xda-developers.com/install-adb-windows-macos-linux/)

[Stackoverflow page about adb](https://stackoverflow.com/questions/6854127/filter-logcat-to-get-only-the-messages-from-my-application-in-android)

[Howtogeek page about administrator privileges](https://www.howtogeek.com/194041/how-to-open-the-command-prompt-as-administrator-in-windows-10/)

### Things you will need
1. `Android` device, aka phone or tablet
2. `Windows`, `Linux`, or `macOS` computer
3. `USB` cable to connect between the computer and the Android device, likely `USB-A to C` or `USB-C to C`
4. The correct `ADB`(Android Debug Bridge) for your operating system

### Steps
1. Enabling `Developer options` on your `Android` device
2. Enable `USB debugging` on your `Android` device
3. Connect the `USB` cable between the computer and `Android` device
4. Download correct copy of `adb` for your operating system, [see below](https://github.com/edgan/Slide/blob/master/DEBUGGING.md#adb-downloads)
5. Unzip `adb` zip
6. Unlock the `Android device`

#### Windows
7. Open a command prompt with `administrator privileges` on the computer
8. `cd` to directory with `adb`
9. Run `adb.exe shell`

#### Linux or macOS
7. Open a terminal window on the computer
8. `cd` to directory with `adb`
9. Run `sudo ./adb shell` and enter your password

10. Allow the debugging when prompted
11. Type `exit` and hit enter

#### Windows
12. Run `adb logcat | findstr AndroidRuntime`

#### Linux or macOS
12. Run `sudo adb logcat | grep AndroidRuntime`

13. Look at the timestamps on the left, and copy the block of lines that have very close timestamps
14. Paste it into a [new Github issue](https://github.com/edgan/Slide/issues/new?template=Blank+issue)
15. Create the new [Github issue](https://github.com/edgan/Slide/issues)
16. Disable `USB debugging` in `Developer options` on your `Android` device after you are done

#### Example of the crash log
```
02-08 17:36:07.739 25025 25025 E AndroidRuntime: FATAL EXCEPTION: main
02-08 17:36:07.739 25025 25025 E AndroidRuntime: Process: me.edgan.redditslide, PID: 25025
02-08 17:36:07.739 25025 25025 E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method 'void android.widget.RelativeLayout.setVisibility(int)' on a null object reference
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at me.edgan.redditslide.Activities.MainActivity$AsyncNotificationBadge.onPostExecute(MainActivity.java:5145)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at me.edgan.redditslide.Activities.MainActivity$AsyncNotificationBadge.onPostExecute(MainActivity.java:5071)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.os.AsyncTask.finish(AsyncTask.java:771)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.os.AsyncTask.-$$Nest$mfinish(Unknown Source:0)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.os.AsyncTask$InternalHandler.handleMessage(AsyncTask.java:788)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.os.Handler.dispatchMessage(Handler.java:109)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.os.Looper.loopOnce(Looper.java:232)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.os.Looper.loop(Looper.java:317)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at android.app.ActivityThread.main(ActivityThread.java:8787)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at java.lang.reflect.Method.invoke(Native Method)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:591)
02-08 17:36:07.739 25025 25025 E AndroidRuntime: 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:871)
```

#### ADB downloads
[Windows](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)

[Linux](https://dl.google.com/android/repository/platform-tools-latest-linux.zip)

[macOS](https://dl.google.com/android/repository/platform-tools-latest-darwin.zip)


#### Instructions from the documentation for enabling Developer options
```
Device                       Setting

Google Pixel                 Settings > About phone > Build number

Samsung Galaxy S8 and later  Settings > About phone > Software information > Build number

LG G6 and later              Settings > About phone > Software info > Build number

HTC U11 and later            Settings > About > Software information > More > Build number or Settings > System > About phone > Software information > More > Build number

OnePlus 5T and later         Settings > About phone > Build number
```

#### Enabling USB debugging on your device
```
Before you can use the debugger and other tools, you need to enable USB debugging, which allows Android Studio and other SDK tools to recognize your device when connected via USB.

Enable USB debugging in the device system settings under Developer options. You can find this option in one of the following locations, depending on your Android version:

Android 9 (API level 28) and higher: Settings > System > Advanced > Developer Options > USB debugging
```
