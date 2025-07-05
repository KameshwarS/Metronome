package com.example.metronome

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {


    private lateinit var soundPool: SoundPool
    private lateinit var playPauseButton: Button
    private lateinit var currentBpmDisplay: TextView
    private lateinit var seekBar: SeekBar
    private var tickSoundId: Int = 0
    private var isPlaying = false
    private var currentStreamId: Int = 0
    private lateinit var plusButton: ImageButton
    private lateinit var minusButton:ImageButton
    private lateinit var spinner: Spinner


    private val availableSounds = mutableListOf(
        MetronomeSound("kick", R.raw.sound), // Example: Make sure 'sound.mp3' exists
        MetronomeSound("hihat1", R.raw.hihat1), // Example: Make sure 'click_sound.mp3' exists
        MetronomeSound("snare1", R.raw.snare1),
        MetronomeSound("snare2", R.raw.snare2) ,
        MetronomeSound("snare3", R.raw.snare3),
        MetronomeSound("snare4", R.raw.snare4),
        MetronomeSound("clap1", R.raw.clap1)

        // Example: Make sure 'kick_sound.mp3' exists
        // Add more MetronomeSound objects here for other sounds you have in res/raw/
        // MetronomeSound("Cowbell", R.raw.cowbell_sound),
    )


    // Handler for scheduling the periodic sound playback
    private val handler = Handler(Looper.getMainLooper())

    private val tempoRunnable = object : Runnable {


        override fun run() {
            currentStreamId=soundPool.play(tickSoundId, 1f, 1f, 0, 0, 1f)
            val bpm = getBPM()

            val delayMillis = (60000L / bpm).toLong()

            handler.postDelayed(this, delayMillis)
            currentBpmDisplay.text = "$bpm BPM"
        }

    }

    private fun getBPM(): Int {
        val bpm = seekBar.progress
        // Coerce for extra safety, though SeekBar min/max typically handles range
        return bpm.coerceIn(40, 200)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        spinner=findViewById(R.id.soundSpinner)

        currentBpmDisplay=findViewById(R.id.currentBpmDisplay)
        playPauseButton=findViewById(R.id.button)
        seekBar=findViewById(R.id.bpmSeekBar)
        plusButton=findViewById(R.id.plus)
        minusButton=findViewById(R.id.minus)


        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA) // Indicate purpose of sound
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // Type of content (short notification/effect)
            .build()
        // Build SoundPool instance
        soundPool = SoundPool.Builder()
            .setMaxStreams(availableSounds.size) // Max concurrent streams (only need 1 for a single tick)
            .setAudioAttributes(audioAttributes)
            .build()


        // Load all available sounds into SoundPool
        for (i in availableSounds.indices) {
            availableSounds[i].soundPoolId = soundPool.load(this, availableSounds[i].resourceId, 1)
        }

        // Set initial selected sound to the first one in the list (after loading)
        if (availableSounds.isNotEmpty()) {
            tickSoundId = availableSounds[0].soundPoolId
        }


        // --- Setup Spinner with sound names ---
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item, // Default layout for spinner item
            availableSounds.map { it.name } // List of sound names for the spinner
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) // Layout for dropdown list
        spinner.adapter = adapter


        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Update the currentTickSoundId to the SoundPool ID of the selected sound
                tickSoundId = availableSounds[position].soundPoolId
                Toast.makeText(this@MainActivity, "Sound changed to: ${availableSounds[position].name}", Toast.LENGTH_SHORT).show()

                // If metronome is playing, restart it to use the new sound immediately
                if (isPlaying) {
                    pauseSound() // Pause current playback
                    playSound()  // Start new playback with the selected sound
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing if nothing is selected
            }
        }



        seekBar.setOnSeekBarChangeListener(object :SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBpmDisplay.text = "$progress BPM"

                // If metronome is playing, re-schedule to immediately reflect the new BPM

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (isPlaying) {
                    handler.removeCallbacks(tempoRunnable) // Cancel current beat
                    handler.post(tempoRunnable) // Post new beat using the updated BPM
                }
            }

        })

        minusButton.setOnClickListener {
            // Decrease BPM, ensuring it doesn't go below the SeekBar's min
            seekBar.progress = (seekBar.progress - 1).coerceAtLeast(40)
            // The onProgressChanged listener will handle updating display and potentially rescheduling
        }

        plusButton.setOnClickListener {
            // Increase BPM, ensuring it doesn't go above the SeekBar's max
            seekBar.progress = (seekBar.progress + 1).coerceAtMost(seekBar.max)
            // The onProgressChanged listener will handle updating display and potentially rescheduling
        }


        // Load the sound into SoundPool. R.raw.my_sound refers to your MP3 file.
        // The load method returns a sound ID once loaded. This happens asynchronously.

        playPauseButton.setOnClickListener{
            if(isPlaying){
                pauseSound()
            }else{
                playSound()
            }
        }



    }

    private fun playSound() {
        val bpm = getBPM() // Get the current BPM from the SeekBar

        isPlaying = true
        playPauseButton.text = "Stop Metronome" // Update button text

        // Play the very first tick immediately to reduce perceived startup delay
        // Store the stream ID so we can stop it if needed
        currentStreamId = soundPool.play(tickSoundId, 1f, 1f, 0, 0, 1f)
        currentBpmDisplay.text = "$bpm BPM" // Update display for this first beat

        Toast.makeText(this, "Starting Metronome: ${bpm} BPM", Toast.LENGTH_SHORT).show()

        // Calculate delay for the *next* beat (after the initial one)
        val delayMillis = (60000L / bpm).toLong()

        // Schedule the tempoRunnable to run after the initial beat's delay
        handler.postDelayed(tempoRunnable, delayMillis)
    }

    private fun pauseSound() {
        handler.removeCallbacks(tempoRunnable) // Crucial: Stop any pending scheduled beats

        // NEW: Explicitly stop the last played sound stream for immediate cut-off
        if (currentStreamId != 0) { // Only try to stop if a stream ID was recorded
            soundPool.stop(currentStreamId)
            currentStreamId = 0 // Reset stream ID
        }

        isPlaying = false
        playPauseButton.text = "Start Metronome" // Update button text
        Toast.makeText(this, "Metronome Stopped", Toast.LENGTH_SHORT).show()
    }



    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tempoRunnable)
        soundPool.release()
    }
}