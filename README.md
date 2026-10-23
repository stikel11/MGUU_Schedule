# MGUU Schedule

An unofficial Android application for viewing and managing the class schedule of Moscow Metropolitan Governance University (MGUU).

## Status

Development is currently on hold.

Latest version: 0.73 beta

This version may not work correctly and contains bugs.

## About

MGUU Schedule provides a convenient way to access the university schedule from an Android device.

The application is designed around a simple principle: the schedule should be quick to access, easy to understand, and useful throughout the academic day.

The project is developed using modern Android technologies and focuses on a clean interface, smooth interactions, and integration with Android system features.

## Features

- View the university class schedule
- Quickly switch between study days
- Swipe between dates
- Current lesson information
- Upcoming lesson notifications
- Android Live Updates
- Local notes for lessons
- Deadlines and study tasks
- Student rating / BRS information
- Weather information
- Automatic schedule updates
- Schedule search
- Share a day's schedule
- Generate a schedule image
- Edge-to-edge interface
- Local caching for offline access

## Screenshots

<img width="1214" height="1080" alt="Group 7" src="https://github.com/user-attachments/assets/3909fb11-f1e5-4b92-a675-e01aacbecbf9" />
<img width="1214" height="1080" alt="Group 8" src="https://github.com/user-attachments/assets/b19b7f4f-681c-4ac0-b5b7-479461053bb0" />
<img width="1214" height="1080" alt="Group 9" src="https://github.com/user-attachments/assets/c83af237-ba78-4afc-a98d-e5ce33c03965" />

## Technology Stack

MGUU Schedule is written in Kotlin and built using modern Android development tools.

Main technologies:

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- Room
- WorkManager
- Kotlin Coroutines
- Jetpack Navigation
- DataStore

The application also uses Android system APIs for notifications, Live Updates, background tasks, and other platform features.

## Schedule Data

MGUU does not provide a publicly available schedule API.

Because of this, the application retrieves schedule data from the university's public portal:

https://portal.mguu.ru

The retrieved data is parsed and converted into the application's internal schedule model.

Schedule data is then stored locally, allowing the application to provide fast access to previously downloaded information without constantly requesting the university portal.

## Local Storage

The application uses local storage for information such as:

- Schedule data
- Lessons
- Lesson notes
- Tasks and deadlines
- Rating data
- Application state and preferences

Background tasks are used to update data and handle scheduled notifications.

## Installation

The latest APK releases are available on the repository's [Releases](../../releases) page.

1. Open the Releases page.
2. Select the required version.
3. Download the APK.
4. Install it on an Android device.

App also available on [RuStore](https://www.rustore.ru/catalog/app/com.mguuschedule) as Preorder

> MGUU Schedule is distributed as an APK and is not an official application of Moscow Metropolitan Governance University.

## Privacy

MGUU Schedule does not require a separate application account.

The application processes locally stored data on the device and communicates with the university services required to retrieve schedule and related information.

Do not commit passwords, API keys, authentication tokens, or other sensitive information to the repository.

## Disclaimer

MGUU Schedule is an unofficial third-party application and is not affiliated with, endorsed by, or sponsored by Moscow Metropolitan Governance University.

University names, trademarks, and related materials belong to their respective owners.

## License

The project does not currently have a public license.
