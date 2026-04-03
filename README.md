# MediSync 💊

A smart medication and supplement reminder app built with Android (Java) and Firebase.

---

## Features

- Email/password authentication (Firebase Auth)
- Add and manage medication schedules
- Daily dose logging — Taken, Skipped, or Delayed
- Dose history with date grouping and PDF export
- Symptom checker
- Scheduled notifications via WorkManager
- Dark mode support
- Long-press to delete a schedule

---

## Tech Stack

- **Language:** Java
- **Min SDK:** 24
- **Backend:** Firebase Auth + Firestore
- **Notifications:** WorkManager
- **Charts:** MPAndroidChart
- **UI:** ConstraintLayout, CardView, Material3 DayNight theme

---

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- A Firebase project with **Email/Password** authentication enabled
- Java 11 or higher

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/MediSync.git
cd MediSync
```

### 2. Set up Firebase

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Create a new project (or use an existing one)
3. Add an **Android app** with package name `com.health.medisync`
4. Enable **Email/Password** under Authentication → Sign-in method
5. Create a **Firestore Database** in test mode
6. Download `google-services.json` and place it in the `app/` folder

```
MediSync/
  app/
    google-services.json   ← place it here
    src/
    ...
```

### 3. Add SHA-1 fingerprint (required for Firebase Auth)

In Android Studio open the terminal and run:

```bash
./gradlew signingReport
```

Copy the **SHA-1** value and add it in Firebase Console under:
**Project Settings → Your Apps → Android app → Add fingerprint**

### 4. Sync and Run

1. Open the project in Android Studio
2. Click **File → Sync Project with Gradle Files**
3. Run the app on an emulator or physical device

---

## Firestore Data Structure

```
users/
  {userId}/
    schedules/
      {scheduleId}/
        pillName: string
        amount: string
        days: string
        foodOption: string       // "Before Food" | "During Food" | "After Food"
        notificationTime: string // "10:00 AM"
        createdAt: timestamp

    logs/
      {logId}/
        scheduleId: string
        pillName: string
        status: string           // "Taken" | "Skipped" | "Delayed"
        timestamp: long
```

---

## Project Structure

```
app/src/main/java/com/health/medisync/
  LoginActivity.java
  SignUpActivity.java
  MainActivity.java
  AddScheduleActivity.java
  LogDoseActivity.java
  HistoryActivity.java
  SymptomCheckerActivity.java
  SettingsActivity.java
  ReminderWorker.java
  Medication.java
  DoseLog.java

app/src/main/res/
  layout/
    activity_main.xml
    activity_login.xml
    activity_sign_up.xml
    activity_add_schedule.xml
    activity_log_dose.xml
    activity_history.xml
    activity_symptom_checker.xml
    activity_settings.xml
    item_med_card.xml
    item_history_row.xml
    item_history_date_header.xml
  drawable/
    btn_green.xml
    btn_green_rounded.xml
    btn_grey.xml
    btn_fab_green.xml
    btn_back_bg.xml
    btn_orange_rounded.xml
    input_bg_light.xml
```

---

## Permissions

The app requires the following permissions declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Important Notes

- `google-services.json` is **not included** in this repository for security reasons. Each developer must generate their own from the Firebase Console.
- Notifications are scheduled via `WorkManager` at the time set by the user when adding a schedule.
- Dark mode preference is saved in `SharedPreferences` under the key `dark_mode` in the file `MediSyncPrefs`.

---

## License

This project is for educational and personal use.
