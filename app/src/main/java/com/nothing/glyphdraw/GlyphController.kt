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

        // 1. Transaction 4: register(targetDevice)
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

        // 2. Transaction 2: openSession()
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

    fun updateHardware(activeSegments: Set<Int>, brightness: Float) {
        val binder = glyphServiceBinder ?: return

        // Nothing Phone (3a) Pro has 3 physical strips:
        // ID 0: Left Arc (Левая дуга)
        // ID 1: Bottom-Left Slash (Нижний слэш)
        // ID 2: Right Arc (Правая дуга)
        //
        // On Nothing OS setFrameColors accepts brightness per strip:
        // Index 0 = Left Arc, Index 1 = Slash, Index 2 = Right Arc
        // In case driver expects the 36-channel array:
        // Left: 20..30, Slash: 31..35, Right: 0..19
        val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
        val colors = IntArray(36) { 0 }

        // 1. Set direct 3-strip channels (0, 1, 2)
        if (0 in activeSegments) colors[0] = maxLevel // Left
        if (1 in activeSegments) colors[1] = maxLevel // Slash
        if (2 in activeSegments) colors[2] = maxLevel // Right

        // 2. Set multi-LED sub-channels for each zone
        if (2 in activeSegments) {
            // Right Arc: 0..19 (only if channel 0-2 isn't exclusive)
            for (i in 3 until 20) colors[i] = maxLevel
        }
        if (0 in activeSegments) {
            // Left Arc: 20..30
            for (i in 20..30) colors[i] = maxLevel
        }
        if (1 in activeSegments) {
            // Slash: 31..35
            for (i in 31..35) colors[i] = maxLevel
        }

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
                // Fallback with 255 scaling
                try {
                    val colors255 = IntArray(36) { if (colors[it] > 0) (brightness * 255).toInt() else 0 }
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
                    // Try next
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
