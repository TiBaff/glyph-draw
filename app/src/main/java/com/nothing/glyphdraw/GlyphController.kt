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

    // Direct Single-Channel test for diagnostic mode
    fun sendSingleChannel(channelIndex: Int, brightness: Float) {
        val binder = glyphServiceBinder ?: return
        val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
        val colors = IntArray(36) { 0 }
        if (channelIndex in 0 until 36) {
            colors[channelIndex] = maxLevel
        }
        sendColors(colors)
    }

    fun updateHardware(activeSegments: Set<Int>, brightness: Float) {
        val binder = glyphServiceBinder ?: return
        val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
        val colors = IntArray(36) { 0 }

        // Empirical findings from user testing on Nothing Phone (3a) Pro:
        // Channel 0 = Right strip
        // Channel 2 = Left strip
        // Channel 3 = Slash (Bottom-left)
        //
        // On screen:
        // Segment IDs 0..9: Right Arc
        // Segment IDs 10..15: Left Arc
        // Segment IDs 16..19: Slash (Bottom-Left)
        val rightActive = (0..9).any { it in activeSegments }
        val leftActive = (10..15).any { it in activeSegments }
        val slashActive = (16..19).any { it in activeSegments }

        if (rightActive) {
            colors[0] = maxLevel
            // If right strip has progressive LEDs:
            (0..9).forEach { segId ->
                if (segId in activeSegments) colors[segId] = maxLevel
            }
        }

        if (leftActive) {
            colors[2] = maxLevel
            // Also map sub-LEDs if individual
            (10..15).forEach { segId ->
                if (segId in activeSegments) {
                    val offset = segId - 10 + 20
                    if (offset < 36) colors[offset] = maxLevel
                }
            }
        }

        if (slashActive) {
            colors[3] = maxLevel
            colors[4] = maxLevel // Fallback for slash
            (16..19).forEach { segId ->
                if (segId in activeSegments) {
                    val offset = segId - 16 + 31
                    if (offset < 36) colors[offset] = maxLevel
                }
            }
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
                // Fallback 255 scaling
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
