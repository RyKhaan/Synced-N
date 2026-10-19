package com.example.syncedn;

import org.junit.Test;
import static org.junit.Assert.*;

public class SheetScrollClockTest {
    @Test public void slowScrollAccumulatesFractionalPixels() {
        SheetScrollClock clock = new SheetScrollClock();
        int position = 0;
        for (int i = 0; i < 100; i++) position = clock.advance(10, 1, 1f, 1000);
        assertTrue(Math.abs(position - 8) <= 1);
    }

    @Test public void equalTimeGivesEqualDistanceAtDifferentFrameRates() {
        SheetScrollClock fastFrames = new SheetScrollClock();
        SheetScrollClock slowFrames = new SheetScrollClock();
        int a = 0, b = 0;
        for (int i = 0; i < 100; i++) a = fastFrames.advance(10, 4, 2f, 10000);
        for (int i = 0; i < 50; i++) b = slowFrames.advance(20, 4, 2f, 10000);
        assertEquals(a, b);
        assertTrue(Math.abs(a - 64) <= 1);
    }

    @Test public void speedCanChangeWhilePlayingAndResumeFromCurrentOffset() {
        SheetScrollClock clock = new SheetScrollClock();
        clock.reset(100);
        int position = 0;
        for (int i = 0; i < 100; i++) position = clock.advance(10, 1, 1f, 1000);
        assertTrue(Math.abs(position - 108) <= 1);
        clock.reset(position);
        for (int i = 0; i < 100; i++) position = clock.advance(10, 10, 1f, 1000);
        assertTrue(Math.abs(position - 188) <= 2);
    }

    @Test public void clampsAtSheetEndAndRejectsLongFrameJumps() {
        SheetScrollClock clock = new SheetScrollClock();
        clock.reset(198);
        assertEquals(200, clock.advance(60, 10, 1f, 200));
        assertEquals(200, clock.advance(60, 10, 1f, 200));
        clock.reset(0);
        assertTrue(clock.advance(60000, 1, 1f, 10000) <= 1);
        assertEquals(0, clock.advance(-100, 1, 1f, 0));
        assertEquals(1, SheetScrollClock.clampSpeed(-1));
        assertEquals(10, SheetScrollClock.clampSpeed(50));
    }
}
