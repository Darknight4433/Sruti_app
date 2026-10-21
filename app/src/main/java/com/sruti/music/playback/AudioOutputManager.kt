package com.sruti.music.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AudioRouteType {
    SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH,
    USB,
    OTHER
}

data class AudioRoute(
    val id: Int,
    val name: String,
    val type: AudioRouteType,
    val isCurrent: Boolean,
    val isSystemRoute: Boolean = false,
    val audioDeviceInfo: AudioDeviceInfo? = null
)

data class AudioOutputState(
    val currentRoute: AudioRoute = AudioRoute(
        id = -1,
        name = "Phone Speaker",
        type = AudioRouteType.SPEAKER,
        isCurrent = true
    ),
    val availableRoutes: List<AudioRoute> = emptyList()
)

class AudioOutputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateAvailableRoutes()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateAvailableRoutes()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        updateAvailableRoutes()
    }

    fun refresh() {
        updateAvailableRoutes()
    }

    private fun updateAvailableRoutes() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val routeList = mutableListOf<AudioRoute>()

        var activeRouteType = AudioRouteType.SPEAKER
        if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
            activeRouteType = AudioRouteType.BLUETOOTH
        } else if (audioManager.isWiredHeadsetOn) {
            activeRouteType = AudioRouteType.WIRED_HEADPHONES
        }

        for (device in devices) {
            val (name, type) = mapDeviceInfo(device)
            val isCurrent = when (type) {
                AudioRouteType.BLUETOOTH -> activeRouteType == AudioRouteType.BLUETOOTH
                AudioRouteType.WIRED_HEADPHONES -> activeRouteType == AudioRouteType.WIRED_HEADPHONES
                AudioRouteType.SPEAKER -> activeRouteType == AudioRouteType.SPEAKER
                else -> false
            }

            routeList.add(
                AudioRoute(
                    id = device.id,
                    name = name,
                    type = type,
                    isCurrent = isCurrent,
                    audioDeviceInfo = device
                )
            )
        }

        // If no speaker device was explicitly enumerated (some devices/emulators only show generic sink)
        if (routeList.none { it.type == AudioRouteType.SPEAKER }) {
            routeList.add(
                0,
                AudioRoute(
                    id = 0,
                    name = "Phone Speaker",
                    type = AudioRouteType.SPEAKER,
                    isCurrent = activeRouteType == AudioRouteType.SPEAKER
                )
            )
        }

        val current = routeList.firstOrNull { it.isCurrent } ?: routeList.firstOrNull() ?: AudioRoute(
            id = -1,
            name = "Phone Speaker",
            type = AudioRouteType.SPEAKER,
            isCurrent = true
        )

        _state.update {
            it.copy(
                currentRoute = current,
                availableRoutes = routeList
            )
        }
    }

    fun selectRoute(route: AudioRoute): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (route.type == AudioRouteType.SPEAKER) {
                audioManager.clearCommunicationDevice()
                updateAvailableRoutes()
                return true
            } else if (route.audioDeviceInfo != null) {
                val success = audioManager.setCommunicationDevice(route.audioDeviceInfo)
                updateAvailableRoutes()
                return success
            }
        }
        updateAvailableRoutes()
        return true
    }

    fun openSystemOutputSwitcher(): Boolean {
        // Attempts to open the Android System Output Switcher panel
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_VOLUME).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(panelIntent)
                true
            } else {
                val settingsIntent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun mapDeviceInfo(device: AudioDeviceInfo): Pair<String, AudioRouteType> {
        val productName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.productName?.toString()?.takeIf { it.isNotBlank() }
        } else null

        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                Pair("Phone Speaker", AudioRouteType.SPEAKER)
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                Pair(productName ?: "Wired Headphones", AudioRouteType.WIRED_HEADPHONES)
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                Pair(productName ?: "Bluetooth Audio", AudioRouteType.BLUETOOTH)
            }
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                Pair(productName ?: "USB Audio", AudioRouteType.USB)
            }
            else -> {
                Pair(productName ?: "Audio Output", AudioRouteType.OTHER)
            }
        }
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
    }
}
