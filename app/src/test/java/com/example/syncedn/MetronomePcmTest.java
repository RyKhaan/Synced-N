package com.example.syncedn;

import org.junit.Test;
public class MetronomePcmTest {
    private static int checks;
    private static void expect(boolean value) {
        checks++;if(!value) throw new AssertionError("Metronome check "+checks+" failed");
    }
    @Test
    public void timingAndPcmRegression() {
        expect(MetronomePcm.clampBpm(0)==30);
        expect(MetronomePcm.clampBpm(999)==240);
        expect(MetronomePcm.clampMeter(0)==1);
        expect(MetronomePcm.clampMeter(99)==12);
        for(int bpm:new int[]{30,60,74,120,137,240}) {
            MetronomePcm clock=new MetronomePcm();short[] block=new short[480];
            for(int i=0;i<6000;i++) clock.fill(block,bpm,4);
            expect(clock.getFrame()==MetronomePcm.SAMPLE_RATE*60L);
            expect(clock.getTotalBeats()==bpm);
        }
        MetronomePcm clock=new MetronomePcm();short[] block=new short[480];
        clock.fill(block,120,3);
        expect(clock.getBeat()==0 && clock.getTotalBeats()==1);
        long energy=0;for(short sample:block) energy+=Math.abs((int)sample);
        expect(energy>10000);
        clock.fill(block,120,3);clock.fill(block,120,3);clock.fill(block,120,3);
        boolean silent=true;for(short sample:block) if(sample!=0) silent=false;
        expect(silent);
        for(int i=4;i<50;i++) clock.fill(block,120,3);
        clock.fill(block,120,3);expect(clock.getBeat()==1);
        for(int i=1;i<50;i++) clock.fill(block,120,3);
        clock.fill(block,120,3);expect(clock.getBeat()==2);
        for(int i=1;i<50;i++) clock.fill(block,120,3);
        clock.fill(block,120,3);expect(clock.getBeat()==0);
        // Change tempo during playback; the next beat starts the new interval.
        MetronomePcm tempo=new MetronomePcm();
        for(int i=0;i<100;i++) tempo.fill(block,60,4);
        tempo.fill(block,120,4);expect(tempo.getTotalBeats()==2);
        for(int i=1;i<50;i++) tempo.fill(block,120,4);
        tempo.fill(block,120,4);expect(tempo.getTotalBeats()==3);
        // Accent and ordinary beats use different audible waveforms.
        MetronomePcm accent=new MetronomePcm();short[] first=new short[480];accent.fill(first,120,4);
        for(int i=1;i<50;i++) accent.fill(block,120,4);
        short[] second=new short[480];accent.fill(second,120,4);
        expect(!java.util.Arrays.equals(first,second));
        System.out.println("Passed "+checks+" metronome timing/audio-generation checks.");
    }
}
