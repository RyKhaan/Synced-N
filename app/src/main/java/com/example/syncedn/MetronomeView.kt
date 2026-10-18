package com.example.syncedn

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Foreground-only metronome. No network, microphone permission or external audio files. */
internal class MetronomeView(
    context: Context,
    private val preferences: SharedPreferences,
    private val song: String,
    songBpm: Int
) : LinearLayout(context) {
    private val navy=Color.rgb(16,17,63)
    private val purple=Color.rgb(80,5,104)
    private val main=Handler(Looper.getMainLooper())
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var focusRequest: AudioFocusRequest?=null
    private var focusHeld=false
    private var closed=false
    @Volatile private var bpm=MetronomePcm.clampBpm(preferences.getInt("metro_bpm_$song",songBpm))
    @Volatile private var meter=MetronomePcm.clampMeter(preferences.getInt("metro_meter_$song",4))
    private class Session {
        @Volatile var running=true
        @Volatile var track: AudioTrack?=null
        var worker: Thread?=null
    }
    private var session: Session?=null
    private val status=label("BPM • stopped",11)
    private val bpmInput=EditText(context).apply {
        textSize=14f;setTextColor(navy);gravity=Gravity.CENTER
        setPadding(dp(4),0,dp(4),0);minimumWidth=0;minWidth=0
        inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true)
        imeOptions=EditorInfo.IME_ACTION_DONE;contentDescription="Metronome tempo, 30 to 240 BPM"
        setText(bpm.toString())
    }
    private val play=action("Play") { if(session==null) startPlayback() else stop() }
    private val taps=java.util.ArrayDeque<Long>()
    private val focusListener=AudioManager.OnAudioFocusChangeListener { change ->
        if(change!=AudioManager.AUDIOFOCUS_GAIN) main.post { stop() }
    }
    init {
        orientation=VERTICAL
        addView(LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(label("Metronome",14,true),LayoutParams(0,dp(40),1f))
            addView(TextView(context).apply {
                text="$meter beats ▾";textSize=12f;setTextColor(purple)
                gravity=Gravity.CENTER;isFocusable=true
                contentDescription="Choose beats per bar"
                setOnClickListener {
                    AlertDialog.Builder(context).setTitle("Beats per bar")
                        .setSingleChoiceItems((1..12).map { "$it beats" }.toTypedArray(),meter-1) { dialog,position ->
                            meter=position+1;text="$meter beats ▾"
                            preferences.edit().putInt("metro_meter_$song",meter).apply()
                            dialog.dismiss()
                        }.setNegativeButton("Cancel",null).show()
                }
            },LayoutParams(dp(88),dp(48)))
        })
        addView(LinearLayout(context).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(action("−") { changeBpm(bpm-1) },LayoutParams(dp(44),dp(48)))
            addView(bpmInput,LayoutParams(0,dp(48),1f))
            addView(action("+") { changeBpm(bpm+1) },LayoutParams(dp(44),dp(48)))
            addView(View(context),LayoutParams(dp(8),1))
            addView(play,LayoutParams(dp(56),dp(48)))
            addView(View(context),LayoutParams(dp(8),1))
            addView(action("Tap") { tapTempo() }.apply {
                contentDescription="Tap tempo. Tap repeatedly to set BPM"
            },LayoutParams(dp(48),dp(48)))
        })
        gap(6);addView(status)
        bpmInput.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val value=s?.toString()?.toIntOrNull()
                if(value!=null && value in 30..240) {
                    bpm=value;preferences.edit().putInt("metro_bpm_$song",bpm).apply();bpmInput.error=null
                }
            }
            override fun afterTextChanged(s:Editable?) {}
        })
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun gap(value:Int) { addView(View(context),LayoutParams(1,dp(value))) }
    private fun label(value:String,size:Int,bold:Boolean=false)=TextView(context).apply {
        text=value;textSize=size.toFloat();setTextColor(navy);includeFontPadding=false
        gravity=Gravity.CENTER_VERTICAL
        if(bold) typeface=SongControlStyle.font
    }
    private fun action(value:String,callback:()->Unit)=Button(context).apply {
        text=value;textSize=13f;isAllCaps=false
        typeface=SongControlStyle.font;includeFontPadding=false
        setTextColor(if(value=="Play") Color.WHITE else navy)
        background=SongControlStyle.background(resources, value=="Play")
        minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
        setPadding(0,0,0,0);stateListAnimator=null;setOnClickListener { callback() }
    }
    private fun changeBpm(value:Int) {
        bpm=MetronomePcm.clampBpm(value);bpmInput.setText(bpm.toString())
        preferences.edit().putInt("metro_bpm_$song",bpm).apply()
    }
    private fun tapTempo() {
        val now=android.os.SystemClock.elapsedRealtime()
        if(taps.isNotEmpty() && now-taps.peekLast()>2200) taps.clear()
        taps.addLast(now)
        while(taps.size>6) taps.removeFirst()
        if(taps.size>=2) {
            val interval=(now-taps.peekFirst()).toDouble()/(taps.size-1)
            if(interval>0) changeBpm(kotlin.math.round(60000.0/interval).toInt())
        } else status.text="Tap again to set tempo"
    }
    private fun obtainFocus():Boolean {
        @Suppress("DEPRECATION")
        val result=if(Build.VERSION.SDK_INT>=26) {
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener,main)
                .setWillPauseWhenDucked(true).build()
            focusRequest=request;audio.requestAudioFocus(request)
        } else audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        focusHeld=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusHeld
    }
    private fun abandonFocus() {
        if(!focusHeld) return
        focusHeld=false
        @Suppress("DEPRECATION")
        if(Build.VERSION.SDK_INT>=26) focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        else audio.abandonAudioFocus(focusListener)
        focusRequest=null
    }
    private fun startPlayback() {
        if(closed || session!=null) return
        val typed=bpmInput.text.toString().toIntOrNull()
        if(typed==null || typed !in 30..240) { bpmInput.error="Enter 30–240 BPM";return }
        bpm=typed
        if(!obtainFocus()) { status.text="Audio is busy. Try Play again.";return }
        val current=Session();session=current;play.text="Stop";status.text="Playing • first beat accented"
        current.worker=Thread({
            var failure:String?=null
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val minimum=AudioTrack.getMinBufferSize(MetronomePcm.SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0) { "This audio format is unavailable." }
                val track=AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(MetronomePcm.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum,4096)).build()
                current.track=track
                check(track.state==AudioTrack.STATE_INITIALIZED) { "Audio could not be initialized." }
                if(current.running) track.play()
                val clock=MetronomePcm();val buffer=ShortArray(480)
                while(current.running) {
                    val previous=clock.totalBeats
                    clock.fill(buffer,bpm,meter)
                    if(clock.totalBeats!=previous) {
                        val beat=clock.beat+1;val beats=meter
                        main.post { if(session===current && current.running) status.text="Beat $beat / $beats • $bpm BPM" }
                    }
                    var written=0
                    while(written<buffer.size && current.running) {
                        val count=track.write(buffer,written,buffer.size-written,AudioTrack.WRITE_BLOCKING)
                        check(count>0) { "Audio output stopped." }
                        written+=count
                    }
                }
            } catch(error:Exception) {
                if(current.running) failure=error.message ?: "Unable to play audio."
            } finally {
                try { current.track?.pause();current.track?.flush() } catch(_:Exception) {}
                current.track?.release();current.track=null
                val message=failure
                main.post {
                    if(session===current) {
                        session=null;abandonFocus();play.text="Play";status.text=message ?: "Stopped"
                        if(message!=null) Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },"SyncedN-Metronome").apply { start() }
    }
    fun stop() {
        val current=session;session=null
        current?.running=false
        try { current?.track?.pause();current?.track?.flush() } catch(_:Exception) {}
        current?.worker?.interrupt()
        abandonFocus()
        play.text="Play";status.text="BPM • stopped"
    }
    fun close() { closed=true;stop() }
    override fun onDetachedFromWindow() { close();super.onDetachedFromWindow() }
}
