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

// 12 Chromatic roots matching Synced N Mobile (MemberActivity.kt / AdminActivity.kt)
export const CHROMATIC_ROOTS = [
  "C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"
];

export const TEAM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

// Default seed worship catalog matching TeamRepository.kt getDefaultSeedSongs()
export const DEFAULT_SEED_SONGS = [
  {
    id: "seed_amazing_grace",
    title: "Amazing Grace",
    artist: "John Newton",
    key: "G",
    bpm: 72,
    referenceUrl: "https://www.youtube.com/watch?v=oaSKxSL5orc",
    sheet: "[Verse 1]\n[G]Amazing grace, how [C]sweet the [G]sound\nThat saved a [D]wretch like me\n[G]I once was lost, but [C]now am [G]found\nWas blind, but [D]now I [G]see\n\n[Verse 2]\n'Twas [G]grace that taught my [C]heart to [G]fear\nAnd grace my [D]fears re[G]lieved\nHow [G]precious did that [C]grace ap[G]pear\nThe hour I [D]first be[G]lieved\n\n[Chorus]\nMy chains are [C]gone, I've been set [G]free\nMy God, my [C]Savior has ransomed [G]me\nAnd like a [C]flood His mercy [Em]reigns\nUnending [Am]love, a[D]mazing [G]grace\n\n[Verse 3]\nThe [G]Lord has promised [C]good to [G]me\nHis word my [D]hope se[G]cures\nHe [G]will my shield and [C]portion [G]be\nAs long as [D]life en[G]dures"
  },
  {
    id: "seed_build_my_life",
    title: "Build My Life",
    artist: "Pat Barrett",
    key: "G",
    bpm: 68,
    referenceUrl: "https://www.youtube.com/watch?v=FR1p3pmRjEU",
    sheet: "[Verse 1]\n[G]Worthy of every song we could [C]ever sing\n[G]Worthy of all the praise we could [C]ever bring\n[G]Worthy of every breath we could [C]ever breathe\nWe live for [Am]You, [D]we live for [G]You\n\n[Verse 2]\n[G]Jesus, the name above every [C]other name\n[G]Jesus, the only one who could [C]ever save\n[G]Worthy of every breath we could [C]ever breathe\nWe live for [Am]You, [D]we live for [G]You\n\n[Chorus]\n[C]Holy, there is no one like [Am]You\nThere is none beside [G]You\nOpen up my eyes in [Em]wonder\nAnd [C]show me who You are and [Am]fill me\nWith Your heart and lead [G]me\nIn Your love to those [Em]around me\n\n[Bridge]\n[C]I will build my [D]life upon Your [Em]love\nIt is a [G]firm foundation\n[C]I will put my [D]trust in You a[Em]lone\nAnd I will [G]not be shaken"
  },
  {
    id: "seed_goodness_of_god",
    title: "Goodness of God",
    artist: "Bethel Music",
    key: "Ab",
    bpm: 70,
    referenceUrl: "https://www.youtube.com/watch?v=n3t6CdeiN1M",
    sheet: "[Verse 1]\n[Ab]I love You, Lord, for Your [Db]mercy never [Ab]fails me\n[Eb]All my [Fm]days, I've been [Db]held in Your [Eb]hands\nFrom the moment that I [Fm]wake up until I [Db]lay my [Ab]head\n[Db]I will sing of the [Eb]goodness of [Ab]God\n\n[Chorus]\n[Db]All my life You have been faithful\n[Ab]All my life You have been so, so good\n[Db]With every breath that I am [Ab]able\n[Db]I will sing of the [Eb]goodness of [Ab]God\n\n[Verse 2]\n[Ab]I love Your voice, You have [Db]led me through the [Ab]fire\nIn [Eb]darkest [Fm]night, You are [Db]close like no [Eb]other\nI've known You as a [Fm]Father, I've known You as a [Db]Friend\nAnd [Db]I have lived in the [Eb]goodness of [Ab]God\n\n[Bridge]\n[Ab/C]Your goodness is [Db]running after, it's [Eb]running after [Ab]me\n[Ab/C]Your goodness is [Db]running after, it's [Eb]running after [Ab]me\nWith my [Ab/C]life laid down, I'm sur[Db]rendered now, I [Eb]give You every[Fm]thing\n[Ab/C]Your goodness is [Db]running after, it's [Eb]running after [Ab]me"
  },
  {
    id: "seed_gratitude",
    title: "Gratitude",
    artist: "Brandon Lake",
    key: "B",
    bpm: 78,
    referenceUrl: "https://www.youtube.com/watch?v=UoHrBNTfzIs",
    sheet: "[Verse 1]\n[B]All my words fall short\n[E]I got nothing new\n[G#m]How could I express\n[F#]All my gratitude\n\n[Verse 2]\n[B]I could sing these songs\n[E]As I often do\n[G#m]But every song must end\n[F#]And You never do\n\n[Chorus]\n[B]So I throw up my hands and praise You again and again\n[G#m]'Cause all that I have is a hallelujah, [E]hallelujah\n[B]And I know it's not much, but I've nothing else fit for a King\n[G#m]Except for a heart singing hallelujah, [F#]hallelujah\n\n[Bridge]\nCome on, [B]my soul, don't you get shy on me\nLift up your [E]song, 'cause you've got a lion inside of those lungs\nGet up and [G#m]praise the [F#]Lord!"
  },
  {
    id: "seed_praise",
    title: "Praise",
    artist: "Elevation Worship",
    key: "G",
    bpm: 127,
    referenceUrl: "https://www.youtube.com/watch?v=f2oxGYpuLkw",
    sheet: "[Verse 1]\n[G]Let everything that has breath\nPraise the Lord, [C]praise the Lord\n[G]I'll praise in the valley, praise on the mountain\n[C]I'll praise when I'm sure, praise when I'm doubting\n\n[Verse 2]\n[G]I'll praise when outnumbered, praise when surrounded\n'Cause [C]praise is the water my enemies drown in\n[G]As long as I'm breathing, I've got a reason to\n\n[Chorus]\n[G]Praise the Lord, oh my soul\n[Em]Praise the Lord, oh my soul\n[C]I won't be quiet, my [D]rock and salvation\n[G]Praise the Lord!\n\n[Bridge]\n[G]I'll praise 'cause You're sovereign, praise 'cause You reign\n[Em]Praise 'cause You rose and defeated the grave\n[C]I'll praise 'cause You're faithful, [D]praise 'cause You're true\n[G]Praise 'cause there's nobody greater than You!"
  },
  {
    id: "seed_trust_in_god",
    title: "Trust in God",
    artist: "Elevation Worship",
    key: "C",
    bpm: 74,
    referenceUrl: "https://www.youtube.com/watch?v=QS04WbSnxok",
    sheet: "[Verse 1]\n[C]Blessed assurance, [F]Jesus is [C]mine\n[Am]Oh what a [G]foretaste of [F]glory divine\n[C]Heir of salvation, [F]purchase of [C]God\n[Am]Born of His [G]Spirit, washed [F]in His blood\n\n[Chorus]\n[C]I trust in God, my Savior\n[Am]The One who will never fail\n[F]He will never fail, [G]He will never [C]fail\n[C]I trust in God, my Savior\n[Am]The One who will never fail\n[F]He will never fail, [G]He will never [C]fail\n\n[Bridge]\n[C]I sought the Lord and He heard and He answered\n[Am]I sought the Lord and He heard and He answered\n[F]I sought the Lord and He heard and He answered\n[G]That's why I trust Him, that's why I [C]trust Him"
  },
  {
    id: "seed_holy_forever",
    title: "Holy Forever",
    artist: "Chris Tomlin",
    key: "C",
    bpm: 72,
    referenceUrl: "https://www.youtube.com/watch?v=IkHgxKemCRk",
    sheet: "[Verse 1]\n[C]A thousand generations falling [F]down in [C]worship\nTo sing the [Am]song of ages to the [G]Lamb\nAnd [C]all who've gone before us and [F]all who will be[C]lieve\nWill sing the [Am]song of ages to the [G]Lamb\n\n[Chorus]\nYour name is the [F]highest, Your name is the [Am]greatest\nYour name stands a[G]bove them all\nAll thrones and do[F]minions, all powers and po[Am]sitions\nYour name stands a[G]bove them all\nAnd the angels cry, [F]Holy, [Am]all creation cries, [G]Holy\nYou are lifted high, [F]Holy, [G]holy for[C]ever"
  },
  {
    id: "seed_way_maker",
    title: "Way Maker",
    artist: "Sinach",
    key: "E",
    bpm: 68,
    referenceUrl: "https://www.youtube.com/watch?v=QM8jQHE5AAk",
    sheet: "[Verse 1]\n[E]You are here, moving in our midst\n[B]I worship You, I worship You\n[C#m]You are here, working in this place\n[A]I worship You, I worship You\n\n[Chorus]\n[E]Way maker, miracle worker, [B]promise keeper\n[C#m]Light in the darkness, my God, [A]that is who You [E]are"
  }
];
