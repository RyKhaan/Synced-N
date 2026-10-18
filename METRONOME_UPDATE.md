# Compact metronome and complete transpose controls

Open the Synced-N folder in Android Studio. Keep your own SDK location in local.properties.

Both member and admin Song Details screens include a compact metronome beneath the existing song card. Play/Stop produces audible clicks through media audio. Set 30–240 BPM with the numeric field or +/−, tap Tap repeatedly for tempo, and tap the beats label for 1–12 beats per bar. The first beat has a higher-pitched accent. Settings are remembered per song. Playback stops on leaving the screen or backgrounding the app. Turn up media volume to hear it.

All 12 chromatic transpose keys appear in two rows; the selected key is highlighted. Original key resets the transposition. Existing saved chord sheets and role rules are retained: vocalists see lyrics; instrumentalists see lyrics and chords. The original song data is not overwritten by transposition or practice tempo changes.

Complete chart means all twelve transpose keys; the app does not download song lyrics. Enter your own or licensed lyrics and chords using the existing Edit Song screen, for example [C]your lyric [G/B]text. No song library or copyrighted lyrics have been added.

Existing team, login, resources, Gradle configuration and other screens are preserved. No microphone permission, external sound files, or extra dependencies are required.

Validation: standalone Java chord and PCM timing checks. Full Android build and device playback must still be verified in Android Studio.
