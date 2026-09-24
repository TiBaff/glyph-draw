package com.nothing.glyphdraw

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

class GlyphController(private val context: Context) {
    private var isConnected = false
    private var glyphServiceBinder: IBinder? = null
    private var boundInterfaceToken: String = "com.nothing.ketchum.service.IGlyphService"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            glyphServiceBinder = service
            isConnected = true
            Log.d("GlyphController", "Connected to Nothing Glyph Service: $name")
            initSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            glyphServiceBinder = null
            isConnected = false
            Log.d("GlyphController", "Disconnected from Nothing Glyph Service")
        }
    }

    init {
        bindGlyphService()
    }

    private fun bindGlyphService() {
        val serviceIntents = listOf(
            Intent().apply {
                component = ComponentName("com.nothing.ketchum", "com.nothing.ketchum.service.GlyphService")
            } to "com.nothing.ketchum.service.IGlyphService",
            Intent("com.nothing.ketchum.GLYPH_SERVICE").apply {
                setPackage("com.nothing.ketchum")
            } to "com.nothing.ketchum.service.IGlyphService",
            Intent().apply {
                component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.GlyphService")
            } to "com.nothing.thirdparty.IGlyphService",
            Intent("com.nothing.thirdparty.bind_glyphservice").apply {
                setPackage("com.nothing.thirdparty")
            } to "com.nothing.thirdparty.IGlyphService"
        )

        for ((intent, token) in serviceIntents) {
            try {
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (bound) {
                    boundInterfaceToken = token
                    Log.d("GlyphController", "Successfully bound to $intent with token $token")
                    break
                }
            } catch (e: Exception) {
                Log.w("GlyphController", "Binding failed with $intent: ${e.message}")
            }
        }
    }

    private fun initSession() {
        val binder = glyphServiceBinder ?: return

        val possibleDeviceIds = listOf("24111", "DEVICE_24111", "test")
        val tokensToTry = listOf(
            boundInterfaceToken,
            "com.nothing.ketchum.service.IGlyphService",
            "com.nothing.thirdparty.IGlyphService"
        )

        for (token in tokensToTry) {
            for (deviceId in possibleDeviceIds) {
                try {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(token)
                        data.writeString(deviceId)
                        val success = binder.transact(4, data, reply, 0)
                        if (success) {
                            reply.readException()
                            boundInterfaceToken = token
                            break
                        }
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        try {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(boundInterfaceToken)
                binder.transact(2, data, reply, 0)
                reply.readException()
                Log.d("GlyphController", "openSession executed successfully")
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (e: Exception) {
            Log.w("GlyphController", "Error opening session: ${e.message}")
        }
    }

    fun sendSingleChannel(channelIndex: Int, brightness: Float) {
        val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
        val colors = IntArray(36) { 0 }
        if (channelIndex in 0 until 36) {
            colors[channelIndex] = maxLevel
        }
        sendColors(colors)
    }

    fun updateHardware(activeSegments: Set<Int>, brightness: Float) {
        // Direct 1-to-1 physical mapping for all 36 LEDs:
        // Zone C (Top-Right Arc): 0..19
        // Zone A (Bottom-Left Arc): 20..30
        // Zone B (Top-Left Accent): 31..35
        val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
        val colors = IntArray(36) { i ->
            if (i in activeSegments) maxLevel else 0
        }
        sendColors(colors)
    }

    private fun sendColors(colors: IntArray) {
        val binder = glyphServiceBinder ?: return
        val tokens = listOf(boundInterfaceToken, "com.nothing.ketchum.service.IGlyphService", "com.nothing.thirdparty.IGlyphService")
        for (token in tokens) {
            try {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(token)
                    data.writeIntArray(colors)
                    binder.transact(1, data, reply, 0)
                    reply.readException()
                    return
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } catch (e: Exception) {
                try {
                    val colors255 = IntArray(colors.size) { if (colors[it] > 0) 255 else 0 }
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(token)
                        data.writeIntArray(colors255)
                        binder.transact(1, data, reply, 0)
                        return
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun release() {
        val binder = glyphServiceBinder
        if (binder != null) {
            try {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                data.writeInterfaceToken(boundInterfaceToken)
                binder.transact(3, data, reply, 0)
                data.recycle()
                reply.recycle()
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (isConnected) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                // Ignore
            }
            isConnected = false
        }
    }
}
