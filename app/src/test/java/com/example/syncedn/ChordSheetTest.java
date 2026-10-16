package com.example.syncedn;

import org.junit.Test;
public class ChordSheetTest {
    private static int count;
    private static void same(String expected,String actual) {
        count++; if(!expected.equals(actual)) throw new AssertionError("Expected: "+expected+"; got: "+actual);
    }
    private static void check(boolean result) {
        count++; if(!result) throw new AssertionError("Check "+count+" failed");
    }
    @Test
    public void parserAndAssignmentRegression() {
        same("D",ChordSheet.transposeChord("C",2,false));
        same("Bm7",ChordSheet.transposeChord("Am7",2,false));
        same("D/F#",ChordSheet.transposeChord("C/E",2,false));
        same("Abmaj7/Eb",ChordSheet.transposeChord("Bbmaj7/F",-2,true));
        same("G#m7b5",ChordSheet.transposeChord("F#m7b5",2,false));
        same("D7(b9)",ChordSheet.transposeChord("C7(b9)",2,false));
        same("Cadd9",ChordSheet.transposeChord("Cadd9",12,true));
        same("Love",ChordSheet.transposeChord("Love",2,false));
        same("[Verse 1]\nWe lift our voices",ChordSheet.render("[Verse 1]\n[C]We lift our [G/B]voices",2,true,false));
        same("My grace is here",ChordSheet.render("C   F   G\nMy grace is here",0,true,false));
        same("D   G   A\nMy grace is here",ChordSheet.render("C   F   G\nMy grace is here",2,false,false));
        same("[Chorus]\nAmazing Grace",ChordSheet.render("[Chorus]\nAmazing Grace",4,true,false));
        check(ChordSheet.render("[C]Hello [G/B]world",2,false,false).contains("A/C#"));
        check(ChordSheet.isInstrumentalist("Vocalist / Guitarist"));
        check(ChordSheet.isInstrumentalist("Keyboardist"));
        check(ChordSheet.isInstrumentalist("Drummer"));
        check(!ChordSheet.isInstrumentalist("Vocalist"));
        check(!ChordSheet.isInstrumentalist("Choir"));
        check(!ChordSheet.isInstrumentalist(""));
        check(ChordSheet.keyPitch("Eb")==3);
        check(ChordSheet.keyPitch("Am")==9);
        check(ChordSheet.keyPitch("H")==-1);
        String source="[F#7/A#]Original [C#m]text";
        ChordSheet.render(source,5,false,true);
        same("[F#7/A#]Original [C#m]text",source);
        check(!ChordSheet.render(ChordSheet.SAMPLE,3,true,false).contains("[C]"));
        same("     D\nHold on", ChordSheet.render("Hold [C]on",2,false,false));
        same("Bb/D", ChordSheet.transposeChord("A/C#",1,true));
        same("Hello world", ChordSheet.render("[C]Hello [G]world",-1,true,true));
        java.util.List<java.util.Map<String,String>> rows=new java.util.ArrayList<>();
        java.util.Map<String,String> vocal=new java.util.HashMap<>();
        vocal.put("uid","UserABC");vocal.put("name","Ryan");vocal.put("role","Vocalist");rows.add(vocal);
        same("Vocalist",ChordSheet.assignedRoles("UserABC","","Renamed","",rows));
        same("",ChordSheet.assignedRoles("OtherUser","","Ryan","",rows));
        same("",ChordSheet.assignedRoles("userabc","","Ryan","",rows));
        java.util.Map<String,String> instrument=new java.util.HashMap<>();
        instrument.put("email","PLAYER@EXAMPLE.COM");instrument.put("role","Keyboardist");rows.add(instrument);
        same("Keyboardist",ChordSheet.assignedRoles("","player@example.com","","",rows));
        same("Vocalist / Keyboardist",ChordSheet.assignedRoles("UserABC","player@example.com","","",rows));
        check(ChordSheet.isInstrumentalist(ChordSheet.assignedRoles("UserABC","player@example.com","","",rows)));
        java.util.Map<String,String> legacy=new java.util.HashMap<>();
        legacy.put("name","  Ryan   Lloyd ");legacy.put("role","Guitarist");rows.add(legacy);
        same("Guitarist",ChordSheet.assignedRoles("","","ryan lloyd","",rows));
        same("",ChordSheet.assignedRoles("","","","",rows));
        check(!ChordSheet.isInstrumentalist("Bass Vocalist"));
        check(ChordSheet.isInstrumentalist("Organist"));
        check(!ChordSheet.isInstrumentalist("Organiser"));
        String[] allKeys={"C/E","C#/F","D/F#","D#/G","E/G#","F/A","F#/A#","G/B","G#/C","A/C#","A#/D","B/D#"};
        for(int target=0;target<12;target++) same(allKeys[target],ChordSheet.transposeChord("C/E",target,false));
        System.out.println("Passed "+count+" chord-sheet and identity tests.");
    }
}
