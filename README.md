# Battery Tracking Research App

An Android application designed for research purposes to monitor and record a device's battery percentage every second for a duration of 10 hours.

## Overview

This app continuously collects battery level data and synchronizes it to a Google Sheet in real-time. The application operates as a foreground service to ensure reliable background execution, adhering to Android's background processing policies.

## Features

- Monitors battery percentage every second for 10 hours
- Stores data locally using SQLite database to ensure data integrity
- Syncs data to Google Sheets in batches every minute
- Displays a persistent notification while the service is running
- Automatically stops after 10 hours
- Provides Google Sign-In authentication for secure access to Google Sheets API

## Data Format

The app records the following data points:
- Device ID (unique identifier for the device)
- Timestamp (UNIX timestamp in milliseconds)
- Battery Percentage (integer value from 0-100)

## Setup Instructions

### Google Cloud Setup

1. Create a Google Cloud project
   - Go to the [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project

2. Enable the Google Sheets API
   - Navigate to "APIs & Services" > "Library"
   - Search for "Google Sheets API" and enable it

3. Create OAuth 2.0 credentials
   - Go to "APIs & Services" > "Credentials"
   - Create an OAuth client ID (Application type: Android)
   - Add your application's package name and SHA-1 signing certificate

4. Create a Google Sheet
   - Create a new spreadsheet in Google Sheets
   - Add headers: "Device ID", "Timestamp", "Battery Percentage"
   - Note the spreadsheet ID (found in the URL: `https://docs.google.com/spreadsheets/d/[SPREADSHEET_ID]/edit`)

### App Setup

1. Clone this repository
2. Open the project in Android Studio
3. Update the DEFAULT_SHEET_ID in MainActivity.kt with your Google Sheet ID
4. Build and install the app on your Android device (Android 5.0/API 21 or higher)

## Usage

1. Launch the app
2. Sign in with a Google account that has access to the specified Google Sheet
3. Verify or update the Google Sheet ID if needed
4. Press "Start Tracking" to begin monitoring battery levels
5. The app will display a notification while tracking is active
6. The service will automatically stop after 10 hours
7. You can manually stop the service using the "Stop Tracking" button or the notification action

## Requirements

- Android 5.0 (API 21) or higher
- Google Play Services
- Internet connection for data syncing
- Permissions:
  - Internet access
  - Foreground service

## Technical Details

- The app uses a foreground service to ensure it continues running even when in the background
- Battery levels are monitored using the Android BatteryManager system service
- Data is stored locally in SQLite and then synced to Google Sheets
- The app implements batch syncing to optimize network usage and stay within API quotas
- Authentication is handled through Google Sign-In with OAuth 2.0

## Recommendations for Research Use

- Keep the device plugged in during the 10-hour tracking period to ensure the service runs uninterrupted
- For extended research periods, the app can be restarted after the 10-hour period
- The device should have a stable internet connection to ensure timely data syncing

## Potential Limitations

- Minor timing drift may occur if the device enters deep sleep
- The Google Sheets API has quotas that may affect real-time syncing if exceeded

## License

[Insert your license information here] 