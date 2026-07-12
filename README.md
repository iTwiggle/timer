# timer
this is a lightweight app that bolsters discipline by offloading all of the responsibility onto the timer that randomly goes off with modifiable parameters in a 24-hour period sound and custom alerts to your phone when the timer goes off. blame the app for your need to get down and give it 20 ❤️🤜🏻🤛🏻👊🏻💪🏻

# 🎲 Randomizer Timer MVP

A lightweight web application designed to trigger custom audio alerts at completely unpredictable intervals within a 24-hour window. 

## 🚀 The Vision
To create a "chaos timer" that keeps you on your toes. Perfect for mindfulness check-ins, posture corrections, random daily challenges, or just breaking up the monotony of a standard routine.


> 📱 **Roadmap Note:** This project is starting as a lightweight web app MVP to nail the core logic and user experience, with the long-term goal of migrating into a native iOS/Android mobile app. Architectural choices should keep logic modular for easy porting.


## 🛠️ Current Tech Stack (MVP)
*   **Frontend:** HTML5, CSS3, JavaScript (Vanilla for speed, or insert React/Vite if you prefer)
*   **Audio:** Web Audio API for custom sound triggers
*   **Notifications:** Web Notifications API

## 🧠 How It Works (The Logic)
1. User sets a time window (default: 24 hours).
2. User selects or uploads a custom alert sound.
3. The app calculates $N$ number of random timestamps within that window.
4. Alerts trigger seamlessly in the browser.

## 📋 Immediate Todo List
- [ ] Initialize basic project structure (`index.html`, `styles.css`, `app.js`)
- [ ] Write the core randomization math function
- [ ] Implement basic browser audio playback on button click
- [ ] Request user permission for browser notifications

## 🔮 Future Rabbit Holes (Icebox)
*   Convert to Progressive Web App (PWA) for background mobile execution
*   Add custom sound upload and local storage saving
*   Cloud sync across devices
*   App on Android and iOS app stores

## 🤖 Native Android MVP

The Android implementation is now in this repository. It owns exact alarms and foreground audio playback, so prompts can fire while the app is closed, the Pixel is locked, or the device is sleeping.

### Included

- Exact or randomized prompts per day
- Active time window and minimum spacing
- Persistent multi-recording audio deck
- Shuffle without repeats
- MP3, WAV, M4A, AAC, OGG, and Opus importing
- Notification actions for **DONE** and **Snooze 10 min — excuses > motivation**
- Boot rescheduling and accountability history

### Phone-only build

GitHub Actions builds the installable debug APK on every push and pull request. Open a successful **Build Android APK** workflow run, download the `RandomChime-debug-apk` artifact, unzip it, and install `app-debug.apk` on Android.

### Stable cloud updates

Stable cloud update signing is the next deployment step. Its keystore must be stored as an encrypted GitHub Actions secret rather than committed to source. Google Play production releases will use a separate Play App Signing identity.
