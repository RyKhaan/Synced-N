package com.example.syncedn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java chord parser: no Android dependencies and no changes to stored source text. */
public final class ChordSheet {
    private ChordSheet() {}
    private static final String[] SHARPS = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
    private static final String[] FLATS = {"C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B"};
    private static final Pattern CHORD = Pattern.compile(
        "^([A-G])([#b♯♭]?)((?:(?:maj|min|dim|aug|sus|add|omit|no|alt|m|M)|[0-9#b♯♭(),°ø+\\-])*)(?:/([A-G])([#b♯♭]?))?$");
    private static final Pattern INLINE = Pattern.compile("\\[([^\\]\\r\\n]+)\\]");
    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final Pattern KEY = Pattern.compile("^([A-G][#b♯♭]?)(?:m| minor| major)?$");
    public static final String SAMPLE =
        "[Verse 1]\n[C]We lift our [G/B]voices in [Am7]praise[F]\n"
        + "[C/E]Together we [F]sing a new [Gsus4]song[G]\n\n"
        + "[Chorus]\n[F]Hope is a [G]light that will [C]guide us home";

    public static int pitch(String note) {
        String value = note.replace("♯","#").replace("♭","b");
        if (!value.matches("[A-G][#b]?")) return -1;
        int result;
        switch (value.charAt(0)) {
            case 'C': result=0; break; case 'D': result=2; break;
            case 'E': result=4; break; case 'F': result=5; break;
            case 'G': result=7; break; case 'A': result=9; break;
            default: result=11;
        }
        if (value.endsWith("#")) result++;
        if (value.endsWith("b")) result--;
        return ((result % 12) + 12) % 12;
    }
    public static int keyPitch(String key) {
        Matcher match = KEY.matcher(key.trim());
        return match.matches() ? pitch(match.group(1)) : -1;
    }
    public static boolean isChord(String value) {
        return CHORD.matcher(value).matches() || value.equals("N.C.");
    }
    public static String transposeChord(String value, int steps, boolean flats) {
        Matcher match = CHORD.matcher(value);
        if (!match.matches() || steps % 12 == 0) return value;
        String[] names = flats ? FLATS : SHARPS;
        int root = ((pitch(match.group(1)+match.group(2)) + steps) % 12 + 12) % 12;
        String result = names[root] + match.group(3);
        if (match.group(4) != null) {
            int bass = ((pitch(match.group(4) + match.group(5)) + steps) % 12 + 12) % 12;
            result += "/" + names[bass];
        }
        return result;
    }
    private static boolean chordLine(String line) {
        boolean found=false;
        for (String token : line.trim().split("\\s+")) {
            if (token.matches("[|:%]+")) continue;
            if (!isChord(token)) return false;
            found=true;
        }
        return found;
    }
    public static String render(String source, int steps, boolean lyricsOnly, boolean flats) {
        List<String> output=new ArrayList<>();
        for (String line : source.replace("\r","").split("\n",-1)) {
            Matcher match=INLINE.matcher(line);
            StringBuilder lyrics=new StringBuilder();
            StringBuilder chords=new StringBuilder();
            int end=0; boolean inline=false;
            while (match.find()) {
                lyrics.append(line, end, match.start());
                if (isChord(match.group(1))) {
                    inline=true;
                    String chord=transposeChord(match.group(1), steps, flats);
                    int anchor=Math.max(lyrics.length(), chords.length()==0 ? 0 : chords.length()+1);
                    while(chords.length()<anchor) chords.append(' ');
                    chords.append(chord);
                } else lyrics.append(match.group());
                end=match.end();
            }
            lyrics.append(line.substring(end));
            if(inline) {
                if(!lyricsOnly && chords.length()>0) output.add(chords.toString());
                if(lyrics.toString().trim().length()>0) output.add(lyrics.toString());
            } else if(chordLine(line)) {
                if(!lyricsOnly) {
                    Matcher tokens=TOKEN.matcher(line);
                    StringBuffer transposed=new StringBuffer();
                    while(tokens.find()) tokens.appendReplacement(transposed,
                        Matcher.quoteReplacement(transposeChord(tokens.group(), steps, flats)));
                    tokens.appendTail(transposed); output.add(transposed.toString());
                }
            } else output.add(line);
        }
        StringBuilder result=new StringBuilder();
        for(String line : output) { if(result.length()>0) result.append('\n'); result.append(line); }
        return result.toString().replaceAll("\n{3,}","\n\n").replaceAll("\\A\n+|\n+\\z", "");
    }
    /** Match stable member IDs first; legacy name-only assignments remain supported. */
    public static String assignedRoles(String uid, String email, String fullName, String username,
            List<java.util.Map<String,String>> assignments) {
        List<String> roles = new ArrayList<>();
        for(java.util.Map<String,String> row : assignments) {
            String assignedId = row.get("uid") == null ? "" : row.get("uid").trim();
            String assignedEmail = clean(row.get("email"));
            String assignedName = clean(row.get("name"));
            boolean matches;
            if(!assignedId.isEmpty()) matches = uid != null && !uid.trim().isEmpty() && assignedId.equals(uid.trim());
            else if(!assignedEmail.isEmpty()) matches = assignedEmail.equals(clean(email));
            else matches = !assignedName.isEmpty() &&
                (assignedName.equals(clean(fullName)) || assignedName.equals(clean(username)));
            String role=row.get("role");
            if(matches && role!=null && !role.trim().isEmpty() && !roles.contains(role.trim())) roles.add(role.trim());
        }
        StringBuilder result=new StringBuilder();
        for(String role:roles) { if(result.length()>0) result.append(" / "); result.append(role); }
        return result.toString();
    }
    private static String clean(String value) {
        return value==null ? "" : value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    /** Combined vocal/instrument roles retain chords. Non-instrumental roles use lyrics. */
    public static boolean isInstrumentalist(String role) {
        String text=role.toLowerCase(Locale.ROOT);
        for(String word : new String[]{"guitar","keyboard","piano","bassist","drum","percussion",
                "instrument","violin","cello","flute","sax","trumpet","trombone","ukulele"}) {
            if(text.contains(word)) return true;
        }
        if(text.matches(".*\\borgan(?:ist)?\\b.*")) return true;
        boolean vocal=text.contains("vocal") || text.contains("singer") || text.contains("choir");
        return text.matches(".*\\bbass\\b.*") && (!vocal || text.contains("/") || text.contains(","));
    }
}
