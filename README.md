# Fiche

Fiche is an android media tracker app for movies, series, books and games. Show your friends the shows you enjoy and keep track of what you watched, or still want to watch.

## License

Fiche is licensed under the GNU General Public License v3.0 (GPL-3.0).

Copyright © 2025 Grand Sphere Studios

## Features

- Track: Keep track of movies, shows, books and games.
- Track: Completed episodes, sequels and prequels.
- Personal ratings: Rate shows based on your enjoyment.
- Compare interests: See what you enjoy compared to others.
- Recommendations: Get recommendations based on shows you enjoyed.
- Share: Share tracked lists or favourites.
- Themes: Custom app colouring.

## Available catalogues

- [TvMaze](https://www.tvmaze.com)
- [TMDB](https://www.themoviedb.org) (requires free API key)
- [Jikan](https://jikan.moe)
- [MyAnimeList](https://myanimelist.net) (requires free API key)
- [Open Library](https://openlibrary.org)
- [RAWG](https://rawg.io) (requires free API key)
- [TasteDive](https://tastedive.com) (requires free API key)

## Technical Information

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM
- **Database**: Room
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 16 (API 36)
- **Build**: Gradle 8.9, JDK 17
- **License**: GNU GPL-3.0

## Getting Started

### Prerequisites

- JDK 17
- Android SDK command-line tools (`cmdline-tools`, `platform-tools`, `emulator`, `platforms;android-36`, `build-tools`)
- `ANDROID_HOME` set, or `sdk.dir` in `local.properties`

### Build

1. Clone the repository:

   ```bash
   git clone https://github.com/GrandSphere/Fiche.git
   cd Fiche
   ```

2. Build the debug APK (use `gradlew.bat` on Windows):

   ```bash
   ./gradlew assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/Fiche-(version)-debug.apk`.

   Release builds (`./gradlew assembleRelease`) require a `keystore.properties` file in the repository root.

### Debug on emulator

1. Install SDK packages:

   ```bash
   sdkmanager "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0" "system-images;android-36;google_apis;x86_64"
   ```

2. Create an AVD named `Pixel_10`. If the `pixel_10` hardware profile is missing, use another Pixel profile and keep the AVD name `Pixel_10`:

   ```bash
   avdmanager create avd -n Pixel_10 -d pixel_10 -k "system-images;android-36;google_apis;x86_64"
   ```

3. Start the emulator:

   ```bash
   emulator -avd Pixel_10
   ```

4. Install the debug build:

   ```bash
   ./gradlew installDebug
   ```

## Known Issues

Report issues at https://github.com/GrandSphere/Fiche/issues.
