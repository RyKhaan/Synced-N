// Firebase & Cloud Configuration synced with Android app (app/google-services.json & CloudinaryUploader.kt)
export const FIREBASE_CONFIG = {
  apiKey: "AIzaSyB1lu8TMwEAOdfXhEQlAAMJzVa1YXWDlg8",
  authDomain: "synced-n.firebaseapp.com",
  projectId: "synced-n",
  storageBucket: "synced-n.firebasestorage.app",
  messagingSenderId: "74207597857",
  appId: "1:74207597857:android:1f7987442f6c75f6741a60"
};

export const CLOUDINARY_CONFIG = {
  cloudName: "ffb3fdkm",
  uploadPreset: "synced_preset",
  uploadUrl: "https://api.cloudinary.com/v1_1/ffb3fdkm/image/upload"
};

export const DEFAULT_MINISTRY_ROLES = [
  "Worship Leader",
  "Substitute Leader",
  "Vocalist",
  "Song Leader",
  "Guitarist",
  "Acoustic Guitarist",
  "Electric Guitarist",
  "Keyboardist",
  "Bassist",
  "Drummer",
  "Sound Operator"
];

export const MUSICAL_KEYS = [
  "C", "C#", "Db", "D", "D#", "Eb", "E", "F",
  "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B",
  "Am", "Bm", "Cm", "C#m", "Dm", "D#m", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m"
];

export const TEAM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
