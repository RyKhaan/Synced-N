package com.example.syncedn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.Pair;

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

    public static boolean hasChords(String source) {
        if (source == null || source.trim().isEmpty()) return false;
        Matcher match = INLINE.matcher(source);
        while (match.find()) {
            if (isChord(match.group(1))) return true;
        }
        for (String line : source.replace("\r", "").split("\n", -1)) {
            if (chordLine(line)) return true;
        }
        return false;
    }

    private static String normalizeHeader(String header) {
        String clean = header.replaceAll("[\\[\\]:().]", "").trim();
        if (clean.isEmpty()) return header;
        String[] parts = clean.split("\\s+");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String part = parts[i];
            if (part.equalsIgnoreCase("pre-chorus") || part.equalsIgnoreCase("prechorus")) {
                sb.append("Pre-Chorus");
            } else if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static String formatStanzas(String source) {
        if (source == null) return "";
        String[] lines = source.replace("\r", "").split("\n", -1);
        StringBuilder result = new StringBuilder();
        Pattern sectionHeader = Pattern.compile("^(?:\\[?(?:verse|chorus|bridge|pre-chorus|prechorus|intro|outro|vamp|hook|interlude|tag|ending|refrain|coda)(?:\\s*\\d*)?\\s*[\\]:.]?)$", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (sectionHeader.matcher(trimmed).matches()) {
                if (result.length() > 0 && !result.toString().endsWith("\n\n")) {
                    if (result.toString().endsWith("\n")) result.append('\n');
                    else result.append("\n\n");
                }
                result.append(normalizeHeader(trimmed));
            } else {
                if (result.length() > 0) result.append('\n');
                result.append(line);
            }
        }
        return result.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    public static String autoDetectSections(String source) {
        if (source == null || source.trim().isEmpty()) return "";
        String formatted = formatStanzas(source);
        Pattern headerPat = Pattern.compile("\\[(?i)(verse|chorus|bridge|pre-chorus|intro|outro|vamp|hook|tag|ending)[^\\]]*\\]");
        if (headerPat.matcher(formatted).find()) {
            return formatted;
        }

        String[] paragraphs = formatted.split("\\n\\s*\\n+");
        if (paragraphs.length <= 1) {
            return "[Verse]\n" + formatted.trim();
        }

        StringBuilder sb = new StringBuilder();
        int verseCount = 1;
        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i].trim();
            if (para.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            if (i == 0) {
                sb.append("[Verse ").append(verseCount++).append("]\n").append(para);
            } else if (i == 1) {
                sb.append("[Chorus]\n").append(para);
            } else if (i == 2) {
                sb.append("[Verse ").append(verseCount++).append("]\n").append(para);
            } else if (i == 3) {
                sb.append("[Chorus]\n").append(para);
            } else if (i == 4) {
                sb.append("[Bridge]\n").append(para);
            } else {
                sb.append("[Chorus]\n").append(para);
            }
        }
        return sb.toString();
    }

    public static String render(String source, int steps, boolean lyricsOnly, boolean flats) {
        String formatted = formatStanzas(source);
        List<String> output=new ArrayList<>();
        for (String line : formatted.split("\n",-1)) {
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

    public static List<String> getChordPalette(String key) {
        List<String> list = new ArrayList<>();
        if (key == null || key.trim().isEmpty()) {
            key = "C";
        }
        String cleanKey = key.trim();
        boolean isMinor = cleanKey.endsWith("m") && !cleanKey.toLowerCase(Locale.ROOT).endsWith("maj") && !cleanKey.toLowerCase(Locale.ROOT).endsWith("major");
        boolean flats = cleanKey.contains("b") || cleanKey.equalsIgnoreCase("F") || cleanKey.equalsIgnoreCase("Dm");
        String[] names = flats ? FLATS : SHARPS;
        int rootPitch = keyPitch(cleanKey);
        if (rootPitch < 0) rootPitch = 0; // fallback C

        if (isMinor) {
            // Natural / harmonic minor: i, ii°, III, iv, v, V7, VI, VII
            list.add(names[rootPitch] + "m");
            list.add(names[(rootPitch + 2) % 12] + "dim");
            list.add(names[(rootPitch + 3) % 12]);
            list.add(names[(rootPitch + 5) % 12] + "m");
            list.add(names[(rootPitch + 7) % 12] + "m");
            list.add(names[(rootPitch + 7) % 12] + "7");
            list.add(names[(rootPitch + 8) % 12]);
            list.add(names[(rootPitch + 10) % 12]);
        } else {
            // Major: I, ii, iii, IV, V, vi, vii°, V7
            list.add(names[rootPitch]);
            list.add(names[(rootPitch + 2) % 12] + "m");
            list.add(names[(rootPitch + 4) % 12] + "m");
            list.add(names[(rootPitch + 5) % 12]);
            list.add(names[(rootPitch + 7) % 12]);
            list.add(names[(rootPitch + 9) % 12] + "m");
            list.add(names[(rootPitch + 11) % 12] + "dim");
            list.add(names[(rootPitch + 7) % 12] + "7");
        }
        return list;
    }

    public static List<Pair<String, String>> splitSections(String source) {
        List<Pair<String, String>> result = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) {
            return result;
        }
        String formatted = formatStanzas(source);
        String[] lines = formatted.split("\n", -1);
        String currentHeader = "";
        StringBuilder currentBody = new StringBuilder();
        Pattern headerPattern = Pattern.compile("^\\s*(\\[[^\\]\\r\\n]+\\])\\s*$");

        for (String line : lines) {
            Matcher m = headerPattern.matcher(line);
            if (m.matches()) {
                if (!currentHeader.isEmpty() || currentBody.length() > 0) {
                    String h = currentHeader.isEmpty() ? "[Verse 1]" : currentHeader;
                    result.add(new Pair<>(h, currentBody.toString().trim()));
                    currentBody.setLength(0);
                }
                currentHeader = m.group(1).trim();
            } else {
                if (currentHeader.isEmpty() && line.trim().length() > 0) {
                    currentHeader = "[Verse 1]";
                }
                if (currentBody.length() > 0) {
                    currentBody.append("\n");
                }
                currentBody.append(line);
            }
        }
        if (!currentHeader.isEmpty() || currentBody.length() > 0) {
            String h = currentHeader.isEmpty() ? "[Verse 1]" : currentHeader;
            result.add(new Pair<>(h, currentBody.toString().trim()));
        }
        return result;
    }

    public static String assembleSections(List<Pair<String, String>> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Pair<String, String> section : sections) {
            String header = section.getFirst() == null ? "" : section.getFirst().trim();
            String body = section.getSecond() == null ? "" : section.getSecond().trim();
            if (header.isEmpty() && body.isEmpty()) continue;
            if (!header.startsWith("[")) header = "[" + header;
            if (!header.endsWith("]")) header = header + "]";
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(header);
            if (!body.isEmpty()) {
                sb.append("\n").append(body);
            }
        }
        return sb.toString();
    }
}

