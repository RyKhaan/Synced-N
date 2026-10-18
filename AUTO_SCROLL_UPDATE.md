# Lyrics and chords auto-scroll

On either dashboard, open Song Details. A compact footer stays below the scrolling content with Start scroll / Pause and a speed slider.

- Speed 1 is slowest; 10 is fastest. Adjust while running; the setting is saved for each song.
- Start moves to the sheet if you are above it. Pause and Start resume from the current position. Starting again after the end returns to the beginning of the sheet.
- Touch the scrolling content to pause immediately before manual scrolling or selecting lyrics. The footer remains available for speed changes and Pause.
- Scrolling ends when the bottom of the sheet is visible. Short sheets that already fit do not keep scrolling into the Reference Link section.
- Leaving the page, backgrounding the activity, or detaching the controls stops all auto-scroll callbacks. Resume manually with Start scroll. The screen stays awake only while auto-scroll runs.
- Works for both lyrics-only vocalist sheets and lyrics/chords instrumentalist sheets. Existing role rules, key transposition, metronome and schedule overlap fixes are retained.

The animation uses elapsed time and fractional pixels at display frame callbacks, with no sleeping loops, repeated screen rebuilding, or network requests. Speed is a reading pace; it is independent of metronome BPM and song timing.

Open Synced-N in Android Studio, keep your own local.properties SDK path, sync and run. Test with a sheet longer than the screen; start, pause, resume, change speed, touch/scroll manually, go Back and reopen, then background and resume the app.

Validation: 11 JVM auto-scroll assertions cover fractional motion, different frame rates, speed changes, resume offsets, end bounds and stalled-frame protection. Android compilation, visual rendering, manual gestures and device playback remain to be verified on a device.
