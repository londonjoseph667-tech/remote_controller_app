package com.remote.controller

import android.util.Log
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class RemoteClient(private val ip: String, private val port: Int) {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    fun connect(onResult: (Boolean) -> Unit) {
        thread {
            try {
                socket = Socket(ip, port)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                onResult(true)
            } catch (e: Exception) {
                Log.e("RemoteClient", "Connection error", e)
                onResult(false)
            }
        }
    }

    fun sendCommand(command: String) {
        thread {
            try {
                writer?.println(command)
            } catch (e: Exception) {
                Log.e("RemoteClient", "Send error", e)
            }
        }
    }

    fun disconnect() {
        thread {
            try {
                writer?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e("RemoteClient", "Disconnect error", e)
            }
        }
    }
}
