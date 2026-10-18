# Automatic arrangement when pasting lyrics/chords

Open Add Song or Edit Song as a leader, then paste into the existing Lyrics & Chords field. Regular Paste and Paste as plain text arrange the pasted block automatically. Keyboard clipboard panels that submit a multi-line block are also supported.

- Inline `[C]lyrics [G/B]here` becomes a chord row above a lyric row.
- Existing chord-above-lyrics sheets keep their supplied columns.
- Tabs expand to four-column tab stops; Windows line endings, nonbreaking spaces, trailing spaces and extra blank lines are cleaned up.
- Standalone section names such as Verse 1, Chorus and Bridge become section headings.
- The editor uses monospace and horizontal scrolling so long chord/lyric pairs do not wrap independently.
- Missing chords are not invented and lyrics are not rewritten. Chords can only be placed at the positions supplied in the pasted sheet. You can adjust positions manually after pasting.
- Pasting over a selection replaces only that selection; the rest of the song is preserved. Normal character typing and the Enter key are not reformatted.
- Saved songs are not automatically rewritten when opened. Tap Save Song to persist your edits.

The song viewer retains transposition, vocalist lyrics-only display, instrumentalist lyrics/chords, auto-scroll, metronome, minimal styling and the prior overlap/performance fixes. Transposition now preserves existing chord column anchors where there is space; adjacent expanded chord names receive separation if necessary.

Validation: JVM tests cover inline conversion, existing columns, tabs, section names, chord qualities, idempotence, text-only lyrics, partial-paste newline boundaries and transposition alignment. Existing chord/role tests also pass. Android compilation and actual keyboard/clipboard gestures remain to be checked on a device.

Open Synced-N in Android Studio, retain your own local.properties SDK path, sync and Run. Test a fresh paste, replacement paste, manual editing, save/reopen, transpose, and both member role views.
