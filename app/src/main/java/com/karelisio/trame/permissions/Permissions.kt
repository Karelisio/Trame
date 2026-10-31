package com.karelisio.trame.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

enum class AppPermission(val title: String, val rationale: String, val required: Boolean) {
    NOTIFICATIONS(
        "Notifications",
        "Pour te prévenir dès qu'un nouveau chapitre sort, avec la couverture et un bouton « Lire ».",
        false,
    ),
    BATTERY(
        "Arrière-plan",
        "Trame vérifie les sorties toutes les quelques heures. Sans cette exclusion, le système (surtout MagicOS, MIUI, One UI…) coupe la vérification et tu rates les chapitres.",
        true,
    ),
    INSTALL(
        "Mises à jour",
        "Pour installer les nouvelles versions de Trame directement depuis l'appli, sans passer par un store.",
        false,
    ),
}

object Permissions {
    fun isGranted(context: Context, p: AppPermission): Boolean = when (p) {
        AppPermission.NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        AppPermission.BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        AppPermission.INSTALL -> context.packageManager.canRequestPackageInstalls()
    }

    fun allRequiredGranted(context: Context) = AppPermission.entries.filter { it.required }.all { isGranted(context, it) }

    /** Écran système correspondant (vérification au retour via ON_RESUME). */
    @SuppressLint("BatteryLife")
    fun settingsIntent(context: Context, p: AppPermission): Intent {
        val pkg = "package:${context.packageName}".toUri()
        return when (p) {
            AppPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            AppPermission.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            AppPermission.INSTALL -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Repli si l'écran direct n'existe pas sur la surcouche. */
    fun fallbackIntent(context: Context, p: AppPermission): Intent = when (p) {
        AppPermission.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context, p: AppPermission) {
        runCatching { context.startActivity(settingsIntent(context, p)) }
            .recoverCatching { context.startActivity(fallbackIntent(context, p)) }
    }

    /** Page dontkillmyapp.com adaptée au fabricant. */
    fun dontKillMyAppUrl(): String {
        val slug = when (val m = Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> "xiaomi"
            "honor" -> "honor"
            "huawei" -> "huawei"
            "samsung", "oneplus", "oppo", "vivo", "realme", "meizu", "asus", "sony", "nokia", "google",
            "lenovo", "motorola", "wiko", "blackview", "unihertz", "tecno" -> m
            else -> ""
        }
        return "https://dontkillmyapp.com/$slug"
    }

    val manufacturer: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
}
