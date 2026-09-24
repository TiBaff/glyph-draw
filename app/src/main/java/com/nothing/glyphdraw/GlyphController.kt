package com.nothing.glyphdraw

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

class GlyphController(private val context: Context) {
    private var isConnected = false
    private var glyphServiceBinder: IBinder? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            glyphServiceBinder = service
            isConnected = true
            Log.d("GlyphController", "Connected to Nothing Glyph Service")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            glyphServiceBinder = null
            isConnected = false
            Log.d("GlyphController", "Disconnected from Nothing Glyph Service")
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        try {
            val intent = Intent().apply {
                component = ComponentName("com.nothing.ketchum", "com.nothing.ketchum.service.GlyphService")
            }
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.w("GlyphController", "Nothing Glyph Service not available on this device: ${e.message}")
        }
    }

    fun updateHardware(activeSegments: Set<Int>, brightness: Float) {
        // Send state to Nothing Glyph framework
        try {
            val intent = Intent("com.nothing.glyph.ACTION_UPDATE_FRAME").apply {
                setPackage("com.nothing.ketchum")
                putExtra("brightness", (brightness * 255).toInt())
                putExtra("active_indices", activeSegments.toIntArray())
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w("GlyphController", "Broadcast failed: ${e.message}")
        }
    }

    fun release() {
        if (isConnected) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                // Ignore unbind error
            }
        }
    }
}
