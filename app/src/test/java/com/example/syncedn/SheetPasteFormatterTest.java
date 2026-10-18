package com.example.syncedn;

import org.junit.Test;
import static org.junit.Assert.*;

public class SheetPasteFormatterTest {
    @Test public void keepsNewlineBoundariesDuringPartialInsertion() {
        assertEquals("\nC\nSing\n", SheetPasteFormatter.formatInsertion("\r\n[C]Sing\r\n"));
        assertEquals("\n", SheetPasteFormatter.formatInsertion("\n"));
        assertEquals("before\nC\nSing\nafter", "before" + SheetPasteFormatter.formatInsertion("\n[C]Sing\n") + "after");
    }
    @Test public void convertsInlineChordsToAlignedRows() {
        assertEquals("[Verse 1]\nC     G/B\nShine with hope", SheetPasteFormatter.format("Verse 1:\r\n[C]Shine [G/B]with hope"));
    }

    @Test public void keepsExistingChordColumnsAndExpandsTabs() {
        assertEquals("C   G/B\nSing a song", SheetPasteFormatter.format("C\tG/B  \r\nSing a song  "));
        assertEquals("  C        G\n  Shine on me", SheetPasteFormatter.format("  C        G\n  Shine on me"));
    }

    @Test public void formattingIsIdempotentAndPreservesTextOnlyLyrics() {
        String source = "[Chorus]\n[F#m7]Shine with [B7]hope\n\nWords without chords.";
        String once = SheetPasteFormatter.format(source);
        assertEquals(once, SheetPasteFormatter.format(once));
        assertEquals("Words without chords.\nWe sing together.", SheetPasteFormatter.format("Words without chords.\nWe sing together."));
        assertEquals("Original lyric [aside] stays.", SheetPasteFormatter.format("Original lyric [aside] stays."));
    }

    @Test public void preservesBarsHeadingsAndChordQualities() {
        assertEquals("[Bridge]\n| Cmaj7 G/B | Am7 Dsus4 |\nKeep these words.", SheetPasteFormatter.format("Bridge\n| Cmaj7 G/B | Am7 Dsus4 |\nKeep these words."));
        assertEquals("[Verse 2]\nC  G\nA new day", SheetPasteFormatter.format("Verse 2\nC  G\nA new day"));
    }

    @Test public void transpositionKeepsOriginalAnchorsWhenThereIsRoom() {
        String result = ChordSheet.render("C        G/B\nShine on me", 1, false, true);
        assertEquals("Db       Ab/C\nShine on me", result);
        assertEquals("Shine on me", ChordSheet.render(SheetPasteFormatter.format("[C]Shine on [G/B]me"), 1, true, true));
    }
}
