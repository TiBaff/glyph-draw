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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            glyphServiceBinder = service
            isConnected = true
            Log.d("GlyphController", "Connected to Nothing Glyph Service Binder")
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
        val possibleIntents = listOf(
            Intent().apply {
                component = ComponentName("com.nothing.ketchum", "com.nothing.ketchum.service.GlyphService")
            },
            Intent("com.nothing.ketchum.GLYPH_SERVICE").apply {
                setPackage("com.nothing.ketchum")
            }
        )

        for (intent in possibleIntents) {
            try {
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (bound) {
                    Log.d("GlyphController", "Successfully bound using intent: $intent")
                    break
                }
            } catch (e: Exception) {
                Log.w("GlyphController", "Binding failed with $intent: ${e.message}")
            }
        }
    }

    private fun initSession() {
        val binder = glyphServiceBinder ?: return
        try {
            // Transaction 2: openSession()
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.nothing.ketchum.service.IGlyphService")
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
        try {
            // Nothing Phone (2a / 3a) array has 33/34 brightness channels
            // 0..23 = Upper-right Arc segments (C zone)
            // 24..29 = Left Arc (A zone)
            // 30..33 = Bottom Strip (B zone)
            val maxLevel = (brightness * 4095).toInt().coerceIn(0, 4095)
            val colors = IntArray(34) { i ->
                if (i in activeSegments) maxLevel else 0
            }

            // Transaction 1: setFrameColors(int[] colors)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.nothing.ketchum.service.IGlyphService")
                data.writeIntArray(colors)
                binder.transact(1, data, reply, 0)
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (e: Exception) {
            // Fallback transaction without descriptor check
            try {
                val maxLevel = (brightness * 255).toInt().coerceIn(0, 255)
                val colors = IntArray(34) { i ->
                    if (i in activeSegments) maxLevel else 0
                }
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken("com.nothing.ketchum.IGlyphService")
                    data.writeIntArray(colors)
                    binder.transact(1, data, reply, 0)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } catch (ex: Exception) {
                Log.w("GlyphController", "Error updating hardware: ${ex.message}")
            }
        }
    }

    fun release() {
        val binder = glyphServiceBinder
        if (binder != null) {
            try {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                data.writeInterfaceToken("com.nothing.ketchum.service.IGlyphService")
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
