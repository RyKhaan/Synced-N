# Chord sheets update — your existing SyncedN project

This ZIP updates the project you supplied. Its screens, artwork, navigation design, authentication/Firebase files, resources, manifest, dependencies and Gradle configuration are retained.

## Open or merge
- Extract this ZIP and open the `SyncedN` folder in Android Studio, then sync and run.
- To merge into your existing project instead, replace only `MemberActivity.kt` and `AdminActivity.kt`, and add `ChordSheet.java` beside them in `app/src/main/java/com/example/syncedn/`.
- Optional regression tests are in `app/src/test/java/com/example/syncedn/ChordSheetTest.java`.
- No additional dependencies or manifest changes are needed.

## Enter a chord sheet using your existing editor
1. Log in as Leader and open Songs -> a song -> Edit Song.
2. In the existing Lyrics & Chords field, place chords in brackets at the lyric anchor. For example:

   [Verse 1]
   [C]We lift our [G/B]voices in [Am7]praise[F]

3. Set the original key, for example C, Eb, F# or Am, and save with the existing Save Song button.
4. SampleChordSheet.cho contains original demonstration text for testing. Replace it with the actual song's licensed lyrics/chords. No commercial song lyrics are bundled.

Both bracketed chords and lines containing only chords (`C  F  G  Am`) are supported. Use bracket markers for reliable alignment and lyric/chord separation. Section labels such as [Verse 1] and [Chorus] are retained.

## Transpose using the existing UI
- The existing C / Db / D / Eb / E / F chips now transpose the actual chords.
- Tap the existing Key label for all 12 keys or Original.
- Root notes and slash bass notes transpose; chord qualities, lyrics and the saved original sheet do not change.
- The target interval is remembered per song.
- Existing card styles and spacing remain. Instrumental sheet text is monospaced and can scroll sideways to keep chords aligned; vocalist text uses its existing wrapping style.

## Member role behavior
Open the service through Home -> Upcoming, then Songs or Song Lineups. Lineup titles now open their sheets without changing the row design.
- Vocalist / choir / singer: lyrics only, with chord markers/chord-only lines and transpose controls hidden.
- Guitarist, keyboardist, pianist, bassist, drummer and other instrumental roles: lyrics plus chords and the existing transpose controls.
- A person assigned both vocals and an instrument retains chords.
- The selected service's assignments take priority over the general team role.
- Direct Home -> Songs browsing uses the saved team-role map instead. Unassigned/unknown roles receive lyrics only.
- Stable member IDs are matched first when a record contains uid/userId/memberId, followed by email. Existing legacy name-only assignments use Profile Full Name or username, ignoring case and repeated spaces. A record for another UID cannot match by name.

## Test both cases
1. Save a sheet in Admin -> Edit Song.
2. Create/publish a future service and assign the test member as Vocalist. Make sure a legacy name-only assignment matches that member's profile Full Name or username.
3. Log in as that member, open Upcoming -> Songs -> the song: lyrics only.
4. In Admin, edit that same service assignment to Keyboardist or Guitarist; Review / Save Changes -> Publish Schedule.
5. Reopen the service and song as Member: chords and existing key controls appear. Tap D to verify C -> D, Am -> Bm, C/E -> D/F#.

Storage follows your supplied code: song sheets and service records are currently local preferences. This update does not add song/service synchronization between phones. Your Firebase login/team implementation is unchanged.

## Verification
- 50 parser/transposition/identity assertions passed using the actual Java engine, compiled for Java 11.
- Tested all 12 transposition intervals, slash chords, chord qualities, lyrics-only output, stable-ID precedence, email/name matching and combined assignments.
- Only MemberActivity.kt and AdminActivity.kt changed among your original files. Other original files were checked byte-for-byte.
- Unrelated screen-building functions were compared against the original project to check that they were unchanged.
- Full Gradle build and emulator/device visual checks could not run here because Android SDK and a cached Gradle distribution are unavailable. Run the project in your Android Studio environment.
