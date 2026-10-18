package com.example.syncedn;

import java.util.regex.Pattern;

/** Paste cleanup only: preserves supplied lyrics/chords and never guesses missing music. */
public final class SheetPasteFormatter {
    private SheetPasteFormatter() {}
    private static final Pattern SECTION = Pattern.compile(
        "(?i)^(?:intro|verse|pre[- ]?chorus|chorus|bridge|interlude|instrumental|solo|outro|ending|tag)(?:\\s+\\d+)?\\s*:?$");

    /** Keep explicit newline boundaries so inserting between existing lines cannot join words. */
    public static String formatInsertion(String source) {
        if (source == null) return "";
        String value = source.replace("\r\n", "\n").replace('\r', '\n');
        String result = format(value);
        if (result.isEmpty()) return value;
        if (value.startsWith("\n")) result = "\n" + result;
        if (value.endsWith("\n")) result += "\n";
        return result;
    }

    public static String format(String source) {
        if (source == null || source.isEmpty()) return "";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\u00a0', ' ').replace("\ufeff", "");
        StringBuilder cleaned = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            StringBuilder expanded = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\t') {
                    int spaces = 4 - expanded.length() % 4;
                    while (spaces-- > 0) expanded.append(' ');
                } else expanded.append(c);
            }
            String value = expanded.toString().replaceAll(" +$", "");
            String heading = value.trim();
            if (SECTION.matcher(heading).matches()) {
                heading = heading.replaceFirst(":$", "").trim();
                value = "[" + heading + "]";
            }
            if (cleaned.length() > 0) cleaned.append('\n');
            cleaned.append(value);
        }
        return ChordSheet.render(cleaned.toString(), 0, false, false);
    }
}
