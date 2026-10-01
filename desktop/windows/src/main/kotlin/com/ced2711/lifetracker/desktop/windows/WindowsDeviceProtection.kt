package com.ced2711.lifetracker.desktop.windows

import com.ced2711.lifetracker.desktop.DeviceProtection
import com.sun.jna.platform.win32.Crypt32Util

/** Windows DPAPI: data protected for the signed-in Windows account on this PC. */
object WindowsDeviceProtection : DeviceProtection {
    override fun protect(bytes: ByteArray): ByteArray = Crypt32Util.cryptProtectData(bytes)

    override fun unprotect(bytes: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(bytes)
}
