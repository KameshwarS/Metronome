package com.example.metronome

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {


    // Reference to the ViewModel
    private lateinit var metronomeViewModel: MetronomeVM

    // --- UI Elements (no longer lateinit for some, as ViewModel manages state) ---
    private lateinit var playPauseButton: android.widget.Button
    private lateinit var currentBpmDisplay: android.widget.TextView
    private lateinit var seekBar: android.widget.SeekBar
    private lateinit var plusButton: android.widget.ImageButton
    private lateinit var minusButton: android.widget.ImageButton
    private lateinit var soundSpinner: android.widget.Spinner
    private lateinit var addSoundButton: android.widget.Button

    // ActivityResultLauncher for picking audio files
    private lateinit var mediaPickerLauncher: ActivityResultLauncher<Array<String>>




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        // Initialize ViewModel
        metronomeViewModel = ViewModelProvider(this).get(MetronomeVM::class.java)

        // --- Initialize UI elements ---
        currentBpmDisplay = findViewById(R.id.currentBpmDisplay)
        playPauseButton = findViewById(R.id.button)
        seekBar = findViewById(R.id.bpmSeekBar)
        plusButton = findViewById(R.id.plus)
        minusButton = findViewById(R.id.minus)
        soundSpinner = findViewById(R.id.soundSpinner)
        addSoundButton = findViewById(R.id.addSoundButton)

        // --- Set SeekBar min/max programmatically ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBar.min = metronomeViewModel.MIN_BPM // Use ViewModel's constants
            seekBar.max = metronomeViewModel.MAX_BPM // Use ViewModel's constants
        }
        // Set initial progress from ViewModel
        seekBar.progress = metronomeViewModel.currentBpm.value ?: 90


        // --- Observe LiveData from ViewModel to update UI ---
        metronomeViewModel.currentBpm.observe(this, Observer { bpm ->
            currentBpmDisplay.text = "$bpm BPM"
            seekBar.progress = bpm // Update seekbar if BPM changes programmatically
        })

        metronomeViewModel.isPlaying.observe(this, Observer { isPlaying ->
            playPauseButton.text = if (isPlaying) "Stop Metronome" else "Play Metronome"
        })

        metronomeViewModel.availableSounds.observe(this, Observer { sounds ->
            // Update the Spinner's adapter whenever the list of available sounds changes
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                sounds.map { it.name }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            soundSpinner.adapter = adapter

            // Re-select the currently playing sound if it's still in the list
            // This is important after adding new sounds or orientation changes
            val currentSoundId = metronomeViewModel.currentTickSoundId // Get ID from ViewModel
            val currentSoundIndex = sounds.indexOfFirst { it.soundPoolId == currentSoundId }
            if (currentSoundIndex != -1) {
                soundSpinner.setSelection(currentSoundIndex)
            } else {
                // If the previously selected sound is no longer available, default to the first
                soundSpinner.setSelection(0)
                metronomeViewModel.setSelectedSound(0) // Update ViewModel's selected sound
            }
        })


        // --- UI Listeners (Call ViewModel methods) ---
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    metronomeViewModel.setBPM(progress) // Update ViewModel's BPM
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) { }
        })

        minusButton.setOnClickListener {
            metronomeViewModel.decreaseBPM()
        }

        plusButton.setOnClickListener {
            metronomeViewModel.increaseBPM()
        }

        addSoundButton.setOnClickListener {
            openMediaPicker()
        }

        playPauseButton.setOnClickListener {
            metronomeViewModel.togglePlayback()
        }

        soundSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                metronomeViewModel.setSelectedSound(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        // Register the ActivityResultLauncher for picking audio files
        mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { selectedUri ->
                metronomeViewModel.addCustomSound(selectedUri)
            } ?: run {
                Toast.makeText(this, "No audio file selected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens the system's media picker to allow the user to select an audio file.
     */
    private fun openMediaPicker() {
        mediaPickerLauncher.launch(arrayOf("audio/*"))
    }
}
