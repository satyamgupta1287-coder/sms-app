# SMS Forwarder → Telegram (personal use)

Forwards incoming SMS on **your own phone** to **your own Telegram bot**.
Not published to Play Store — sideload only.

## 1. Create your Telegram bot
1. Open Telegram, message `@BotFather`, send `/newbot`, follow prompts.
2. Copy the bot token it gives you (looks like `123456789:AAExxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`).
3. Send any message to your new bot (so it has a chat to reply into).
4. Open this URL in a browser: `https://api.telegram.org/bot<TOKEN>/getUpdates`
5. Find `"chat":{"id":123456789,...}` in the response — that number is your `chat_id`.

## 2. Configure the app
Open `app/build.gradle` and replace these two lines (they appear twice — once
in `debug`, once for `release`):

```gradle
buildConfigField "String", "BOT_TOKEN", "\"PUT_YOUR_BOT_TOKEN_HERE\""
buildConfigField "String", "CHAT_ID", "\"PUT_YOUR_CHAT_ID_HERE\""
```

Put your real token and chat id in there.

## 3. Build the APK
**Option A — Android Studio:** Open this folder as a project, let it sync
(it will auto-generate the Gradle wrapper), then Build → Build Bundle(s) /
APK(s) → Build APK(s).

**Option B — GitHub Actions (no desktop needed):** Push this folder to a repo
and add a workflow that runs `gradle assembleDebug` with JDK 17 (temurin),
then uploads the APK via `actions/upload-artifact`. No `gradlew` is included
in this zip, so the workflow needs system Gradle (`gradle` action) rather
than `./gradlew`.

## 4. Install & set up on your phone
1. Install the APK (allow "install from unknown sources" if asked).
2. Open the app, tap **Grant SMS Permission & Start**.
3. Tap **Disable Battery Optimization** and set it to unrestricted for this
   app — otherwise Android may kill it in the background and SMS forwarding
   will stop working silently.

## How it works
- `SmsReceiver` listens for the system's `SMS_RECEIVED` broadcast.
- Every incoming SMS (sender + body) is sent to your Telegram bot via the
  Bot API's `sendMessage` endpoint.
- `ForwarderForegroundService` keeps a low-priority persistent notification
  running so the process stays alive for reliable delivery.
- `BootReceiver` restarts the service after a phone reboot.

## Notes
- This only reads SMS on the device it's installed on, with permission you
  grant at runtime. It does not touch any other device.
- Your bot token is effectively a password to your bot — don't commit it to
  a public repo. Consider moving it to `local.properties` (git-ignored) and
  reading it in `build.gradle` via `Properties` instead of hardcoding, once
  you're comfortable with the basic version working.
