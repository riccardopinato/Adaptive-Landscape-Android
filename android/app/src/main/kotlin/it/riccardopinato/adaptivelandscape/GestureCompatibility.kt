package it.riccardopinato.adaptivelandscape

import android.os.Build

/**
 * Xiaomi/HyperOS may disable its privileged native system-gesture engine when
 * a third-party launcher takes the HOME role. This normal APK cannot restore
 * that private engine or change system navigation overlays.
 *
 * Gesture-compatible mode keeps the user's system Home selected and shows
 * Deskland only as an ordinary live wallpaper (manual icon calibration).
 * No AccessibilityService, screen capture, overlay, root or ADB is required.
 */
internal object GestureCompatibility {
    const val XIAOMI_NAVIGATION_SETTINGS = "com.android.settings.NAVIGATION_MODE_SETTINGS"

    fun isXiaomiFamily(manufacturer: String): Boolean {
        val vendor = manufacturer.trim().lowercase(java.util.Locale.ROOT)
        return vendor == "xiaomi" || vendor == "redmi" ||
            vendor == "poco" || vendor == "blackshark"
    }

    fun hasXiaomiGestureRisk(): Boolean = isXiaomiFamily(Build.MANUFACTURER)

    fun syntheticSelfTest(): Boolean =
        isXiaomiFamily("Xiaomi") && isXiaomiFamily("redmi") &&
            isXiaomiFamily(" POCO ") && isXiaomiFamily("BlackShark") &&
            !isXiaomiFamily("Google") && !isXiaomiFamily("Samsung") &&
            XIAOMI_NAVIGATION_SETTINGS.startsWith("com.android.settings.")
}
