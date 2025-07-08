package com.example.metronome

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {



    private lateinit var metronomeViewModel: MetronomeVM

    // UI Elements
    private lateinit var playPauseButton:Button
    private lateinit var currentBpmDisplay:TextView
    private lateinit var seekBar: SeekBar
    private lateinit var plusButton:ImageButton
    private lateinit var minusButton:ImageButton
    private lateinit var soundSpinner:Spinner
    private lateinit var addSoundButton:Button

    // ActivityResultLauncher for picking audio files
    private lateinit var mediaPickerLauncher: ActivityResultLauncher<Array<String>>




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        // Initialize UI elements
        metronomeViewModel = ViewModelProvider(this).get(MetronomeVM::class.java)
        currentBpmDisplay = findViewById(R.id.currentBpmDisplay)
        playPauseButton = findViewById(R.id.button)
        seekBar = findViewById(R.id.bpmSeekBar)
        plusButton = findViewById(R.id.plus)
        minusButton = findViewById(R.id.minus)
        soundSpinner = findViewById(R.id.soundSpinner)
        addSoundButton = findViewById(R.id.addSoundButton)


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBar.min = metronomeViewModel.MIN_BPM
            seekBar.max = metronomeViewModel.MAX_BPM
        }

        seekBar.progress = metronomeViewModel.currentBpm.value ?: 90



        metronomeViewModel.currentBpm.observe(this, Observer { bpm ->
            currentBpmDisplay.text = "$bpm BPM"
            seekBar.progress = bpm
        })

        metronomeViewModel.isPlaying.observe(this, Observer { isPlaying ->
            playPauseButton.text = if (isPlaying) "Pause" else "Play"
        })

        metronomeViewModel.availableSounds.observe(this, Observer { sounds ->
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                sounds.map { it.name }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            soundSpinner.adapter = adapter


            val currentSoundId = metronomeViewModel.currentTickSoundId
            val currentSoundIndex = sounds.indexOfFirst { it.soundPoolId == currentSoundId }
            if (currentSoundIndex != -1) {
                soundSpinner.setSelection(currentSoundIndex)
            } else {

                soundSpinner.setSelection(0)
                metronomeViewModel.setSelectedSound(0)
            }
        })



        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentBpmDisplay.text = "$progress BPM"
                }
            }
            override fun onStartTrackingTouch(seekBar:SeekBar?) { }
            override fun onStopTrackingTouch(seekBar:SeekBar?) {
                seekBar?.progress?.let { finalBpm ->
                    metronomeViewModel.setBPM(finalBpm)
                }
            }
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

    // open file for users to pick sound from
    private fun openMediaPicker() {
        mediaPickerLauncher.launch(arrayOf("audio/*"))
    }
}
