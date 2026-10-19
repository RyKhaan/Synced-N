package com.example.syncedn;

/** Sample-clocked PCM generator. The UI never schedules the audio beats. */
public final class MetronomePcm {
    public static final int SAMPLE_RATE = 48000;
    private long frame;
    private double nextBeat;
    private long beatStart = -10000;
    private int beat = -1;
    private int previousMeter = 4;
    private long totalBeats;
    private boolean resetBar;
    public static int clampBpm(int bpm) { return Math.max(30, Math.min(240, bpm)); }
    public static int clampMeter(int meter) { return Math.max(1, Math.min(12, meter)); }
    public int getBeat() { return beat; }
    public long getTotalBeats() { return totalBeats; }
    public long getFrame() { return frame; }
    public void fill(short[] output, int bpm, int meter) {
        bpm=clampBpm(bpm); meter=clampMeter(meter);
        if(meter!=previousMeter) { resetBar=true; previousMeter=meter; }
        for(int i=0;i<output.length;i++,frame++) {
            if(frame >= nextBeat) {
                beat=resetBar ? 0 : (beat+1)%meter; resetBar=false; beatStart=frame; totalBeats++;
                nextBeat += SAMPLE_RATE * 60.0 / bpm;
            }
            long age=frame-beatStart;
            if(age>=0 && age<1200) {
                double frequency=beat==0 ? 1760.0 : 1100.0;
                double envelope=Math.min(1.0,age/16.0)*Math.exp(-age/240.0);
                output[i]=(short)Math.round(15000.0*envelope*Math.sin(2.0*Math.PI*frequency*age/SAMPLE_RATE));
            } else output[i]=0;
        }
    }
}
