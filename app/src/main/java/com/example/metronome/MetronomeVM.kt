package com.example.metronome

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.IOException
import kotlin.jvm.java

class MetronomeVM(application: Application) : AndroidViewModel(application) {

    private val appContext=application.applicationContext
    private val _currentBpm= MutableLiveData(90)
    val currentBpm: LiveData<Int> = _currentBpm

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _availableSounds = MutableLiveData<MutableList<MetronomeSound>>()
    val availableSounds: LiveData<MutableList<MetronomeSound>> = _availableSounds

    private lateinit var soundPool: SoundPool
    var currentTickSoundId: Int = 0
    private var currentStreamId: Int = 0

    val MIN_BPM = 40
    val MAX_BPM = 200

    private val handler=Handler(Looper.getMainLooper())
    private val tempoRunnable = object : Runnable {
        override fun run() {
            currentStreamId=soundPool.play(currentTickSoundId, 1f, 1f, 0, 0, 1f)
            val bpm = _currentBpm.value ?: 90 // Default to 90 if null
            val delayMillis = (60000L / bpm).toLong()
            handler.postDelayed(this, delayMillis)

        }

    }

    private val sharedPreferences=application.getSharedPreferences("MetronomePrefs", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()

    private val CUSTOM_SOUNDS_KEY = "custom_metronome_sounds"


    init {
        // Initialize SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(20) // Set a generous maxStreams
            .setAudioAttributes(audioAttributes)
            .build()

        // Initialize availableSounds with built-in sounds
        val initialSounds = mutableListOf(
            MetronomeSound("Default Tick", resourceId = R.raw.sound),
            MetronomeSound("Hi-Hat 1", resourceId = R.raw.hihat1),
            MetronomeSound("Snare 1", resourceId = R.raw.snare1),
            MetronomeSound("Snare 2", resourceId = R.raw.snare2),
            MetronomeSound("Snare 3", resourceId = R.raw.snare3),
            MetronomeSound("Snare 4", resourceId = R.raw.snare4),
            MetronomeSound("Clap 1", resourceId = R.raw.clap1)
        )
        _availableSounds.value = initialSounds // Set initial value for LiveData

        // Load built-in sounds into SoundPool
        _availableSounds.value?.forEach { sound ->
            sound.resourceId?.let { resId ->
                sound.soundPoolId = soundPool.load(appContext, resId, 1)
            }
        }

        // Load custom sounds from SharedPreferences
        loadCustomSounds()

        // Set initial selected sound (default to first available)
        _availableSounds.value?.firstOrNull()?.let {
            currentTickSoundId = it.soundPoolId
        }
    }

    fun togglePlayback() {
        if (_isPlaying.value == true) {
            pauseSound()
        } else {
            playSound()
        }
    }

    fun setBPM(bpm: Int) {
        val newBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
        if (_currentBpm.value != newBpm) {
            _currentBpm.value = newBpm
            if (_isPlaying.value == true) {
                // Restart playback immediately if BPM changes while playing
                handler.removeCallbacks(tempoRunnable)
                handler.post(tempoRunnable)
            }
        }
    }

    fun increaseBPM() {
        setBPM((_currentBpm.value ?: 90) + 1)
    }

    fun decreaseBPM() {
        setBPM((_currentBpm.value ?: 90) - 1)
    }

    fun setSelectedSound(position: Int) {
        _availableSounds.value?.getOrNull(position)?.let { selectedSound ->
            currentTickSoundId = selectedSound.soundPoolId
            // If playing, restart to use new sound immediately
            if (_isPlaying.value == true) {
                pauseSound()
                playSound()
            }
        }
    }

    fun addCustomSound(audioUri: Uri) {
        val displayName = getFileNameFromUri(audioUri) ?: "Custom Sound"

        // Prevent duplicates
        if (_availableSounds.value?.any { it.uri == audioUri } == true) {
            Toast.makeText(appContext, "Sound '$displayName' is already added.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Request persistent URI permission. Crucial for persistence across app restarts.
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(audioUri, takeFlags)

            // Load the sound into SoundPool from the URI
            appContext.contentResolver.openFileDescriptor(audioUri, "r")?.use { pfd ->
                val newSoundPoolId = soundPool.load(pfd.fileDescriptor, 0, pfd.statSize, 1)

                if (newSoundPoolId != 0) {
                    val newSound = MetronomeSound(displayName, uri = audioUri, soundPoolId = newSoundPoolId)
                    val currentList = _availableSounds.value ?: mutableListOf()
                    currentList.add(newSound)
                    _availableSounds.value = currentList // Update LiveData

                    // Save the updated list of custom sounds
                    saveCustomSounds()

                    Toast.makeText(appContext, "Added: $displayName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appContext, "Failed to load sound: $displayName", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(appContext, "Failed to open sound file: $displayName (Permission error or file not found)", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(appContext, "Error adding sound: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // --- Private Playback Control Methods ---
    private fun playSound() {
        _isPlaying.value = true
        val bpm = _currentBpm.value ?: 90

        currentStreamId = soundPool.play(currentTickSoundId, 1f, 1f, 0, 0, 1f)
        Toast.makeText(appContext, "Starting Metronome: ${bpm} BPM", Toast.LENGTH_SHORT).show()

        val delayMillis = (60000L / bpm).toLong()
        handler.postDelayed(tempoRunnable, delayMillis)
    }

    private fun pauseSound() {
        handler.removeCallbacks(tempoRunnable)
        if (currentStreamId != 0) {
            soundPool.stop(currentStreamId)
            currentStreamId = 0
        }
        _isPlaying.value = false
        Toast.makeText(appContext, "Metronome Stopped", Toast.LENGTH_SHORT).show()
    }

    // --- Private Persistence Methods ---
    private fun loadCustomSounds() {
        val json = sharedPreferences.getString(CUSTOM_SOUNDS_KEY, null)
        json?.let {
            val type = object : TypeToken<List<MetronomeSound>>() {}.type
            val customSounds: List<MetronomeSound> = gson.fromJson(it, type)

            val currentList = _availableSounds.value ?: mutableListOf()
            var needsSaveFilter = false // Flag to check if we need to filter and resave

            customSounds.forEach { sound ->
                // Check if this custom sound (by URI) is already in the list to prevent duplicates
                if (!currentList.any { existingSound -> existingSound.uri == sound.uri }) {
                    sound.uri?.let { uri ->
                        try {
                            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                val newSoundPoolId = soundPool.load(pfd.fileDescriptor, 0, pfd.statSize, 1)
                                if (newSoundPoolId != 0) {
                                    val loadedSound = sound.copy(soundPoolId = newSoundPoolId)
                                    currentList.add(loadedSound)
                                } else {
                                    Toast.makeText(appContext, "Failed to load saved sound (SoundPool error): ${sound.name}", Toast.LENGTH_SHORT).show()
                                    needsSaveFilter = true // Mark for filtering if loading fails
                                }
                            } ?: run {
                                Toast.makeText(appContext, "Failed to open sound file: ${sound.name} (File may be moved or missing)", Toast.LENGTH_LONG).show()
                                needsSaveFilter = true // Mark for filtering if opening fails
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(appContext, "Permission to access sound lost: ${sound.name}", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                            needsSaveFilter = true // Mark for filtering if permission lost
                        } catch (e: IOException) {
                            Toast.makeText(appContext, "Error reading sound file: ${sound.name}", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                            needsSaveFilter = true // Mark for filtering if reading fails
                        } catch (e: Exception) {
                            Toast.makeText(appContext, "Unknown error loading sound: ${sound.name}", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                            needsSaveFilter = true // Mark for filtering if unknown error
                        }
                    }
                }
            }
            _availableSounds.value = currentList // Update LiveData after processing all
            if (needsSaveFilter) {
                saveCustomSoundsFiltered() // Save filtered list if any failed to load
            }
        }
    }

    private fun saveCustomSounds() {
        // This is called when a NEW sound is added. It saves all current custom sounds.
        val customSoundsToSave = _availableSounds.value?.filter { it.uri != null } ?: emptyList()
        val json = gson.toJson(customSoundsToSave)
        sharedPreferences.edit().putString(CUSTOM_SOUNDS_KEY, json).apply()
    }

    private fun saveCustomSoundsFiltered() {
        // This is called after loading to remove any invalid URIs from persistence.
        val validCustomSounds = _availableSounds.value?.filter { it.uri != null && it.soundPoolId != 0 } ?: emptyList()
        val json = gson.toJson(validCustomSounds)
        sharedPreferences.edit().putString(CUSTOM_SOUNDS_KEY, json).apply()
    }

    // --- Helper Method ---
    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = appContext.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        result = it.getString(displayNameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    // --- Lifecycle Cleanup ---
    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(tempoRunnable)
        soundPool.release()
    }
}
