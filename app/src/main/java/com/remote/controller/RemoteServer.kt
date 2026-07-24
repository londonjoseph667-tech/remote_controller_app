package com.remote.controller

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class RemoteServer(private val port: Int, private val onCommandReceived: (String) -> Unit) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(port)
                Log.d("RemoteServer", "Server started on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept()
                    client?.let { handleClient(it) }
                }
            } catch (e: Exception) {
                Log.e("RemoteServer", "Server error", e)
            } finally {
                stop()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isRunning) {
                    val line = reader.readLine() ?: break
                    onCommandReceived(line)
                }
            } catch (e: Exception) {
                Log.e("RemoteServer", "Client handler error", e)
            } finally {
                socket.close()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
    }
}
