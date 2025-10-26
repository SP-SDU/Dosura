# Dosura

Medication adherence app for elderly patients and caregivers

## Setup

### Requirements

- Android Studio (Hedgehog or later)
- JDK 17+ (included with Android Studio)
- Android SDK (API 34)

### Running the App

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Run on emulator or physical device (API 26+)

```bash
# Or build from command line
.\gradlew build
```

## Project Structure (MVVM + Clean Architecture)

```txt
app/src/main/java/dk/sdu/dosura/
 ../data/
   ../local/          # Room database, DAOs, entities
   ../repository/     # Data repositories
 ../di/               # Hilt dependency injection
 ../domain/           # Business logic
 ../presentation/     # UI layer (Jetpack Compose)
   ../patient/        # Patient role screens
   ../caregiver/      # Caregiver role screens
   ../common/         # Shared components
   ../onboarding/     # Role selection
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM
- **DI**: Hilt
- **Database**: Room
- **Async**: Coroutines + Flow
- **Navigation**: Navigation Compose
- **Image Loading**: Coil
- **Preferences**: DataStore

## Key Features

### Patient Mode

- Add medications with photos
- Set custom reminder schedules
- Log medication taken/missed/skipped
- Generate 6-digit link code for caregivers
- View adherence statistics

### Caregiver Mode

- Link to multiple patients via code
- Monitor patient adherence in real-time
- View medication history
- Send motivational messages

## Build Info

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Gradle**: 8.13
- **Kotlin**: 2.0.21
