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
        // Test all possible Nothing OS service entry points
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
        // For Nothing Phone (3a) / (3a) Pro the device ID is "24111"
        val possibleDeviceIds = listOf("24111", "DEVICE_24111", "23111", "23113", "test")
        val tokensToTry = listOf(
            boundInterfaceToken,
            "com.nothing.ketchum.service.IGlyphService",
            "com.nothing.ketchum.IGlyphService",
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
                            Log.d("GlyphController", "register($deviceId) executed on $token")
                            boundInterfaceToken = token
                            break
                        }
                    } finally {
                        data.recycle()
                        reply.recycle()
                    }
                } catch (e: Exception) {
                    // Ignore and try next
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

        // Nothing Phone (3a) Pro mapping:
        // Indices 0..23 = Zone C (24 segments on lower arc)
        // Index 24 = Zone A (Top-Left Arc)
        // Index 25 = Zone B (Top-Right Strip)
        val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
        val colors = IntArray(34) { i ->
            if (i in activeSegments) maxLevel else 0
        }

        // Also map Zone A & B to extended indices just in case device uses alternative offsets
        if (24 in activeSegments) {
            if (colors.size > 24) colors[24] = maxLevel
            if (colors.size > 26) colors[26] = maxLevel
        }
        if (25 in activeSegments) {
            if (colors.size > 25) colors[25] = maxLevel
            if (colors.size > 27) colors[27] = maxLevel
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
                // Retry with 255 scaling
                try {
                    val colors255 = IntArray(34) { if (it in activeSegments) (brightness * 255).toInt() else 0 }
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
                binder.transact(3, data, reply, 0) // closeSession()
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
