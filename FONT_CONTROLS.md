# Song controls

Song Details on both dashboards has A− / A+ controls in the fixed footer. Range: 12–18sp; default: 13sp. Size is saved per song on this device and affects lyrics and chords together. Monospace alignment is retained. Resizing pauses automatic scrolling; tap Start scroll to resume after the sheet reflows.

Auto-scroll, speed, transpose and metronome controls use compact surfaces. Font and metronome buttons retain large touch targets. The rest of the UI and paste formatting are retained.

Android build/device testing remains pending: the editing environment has no Android SDK. Check saved size after reopening a song, both size limits, vocalist and instrumentalist views, and scrolling after resizing. Also verify audio and transposition on a device.

Overlap refinement: font readout widened; status moved to a separate line; control labels stay on one line; sheet line spacing increased. Previously saved values above 18sp are clamped and saved on opening the song. Device verification is still required.
