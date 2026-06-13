package com.example.projectammu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

object CallController {

    fun hasCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun answerCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !hasCallPermission(context)) {
            return false
        }

        val telecomManager = context.getSystemService(TelecomManager::class.java)

        return runCatching {
            telecomManager.acceptRingingCall()
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun endCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasCallPermission(context)) {
            return false
        }

        val telecomManager = context.getSystemService(TelecomManager::class.java)

        return runCatching {
            telecomManager.endCall()
        }.getOrDefault(false)
    }
}
