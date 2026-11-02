package app.feltbook.core.transfer

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Что нужно телефону для передачи по воздуху. Интерфейс — чтобы ViewModel проверялись без Android. */
interface TransferEnvironment {
    /** Google Play Services на месте и годятся для Nearby. Нет — сразу файл (ТЗ §6). */
    fun isNearbyAvailable(): Boolean

    /** Runtime-разрешения, которых ещё нет (запрашиваются только в момент передачи, с пояснением). */
    fun missingPermissions(): List<String>

    /** Android ≤ 11: поиск по Bluetooth не работает при выключенной геолокации. */
    fun isLocationServiceRequiredButOff(): Boolean

    /** Android 12: пользователь выбрал «Приблизительное местоположение» — Nearby нужна точная. */
    fun isApproximateLocationOnly(): Boolean
}

/** Разрешения Nearby по версиям Android (манифест `:core:transfer`). */
object TransferPermissions {
    private const val S = 31
    private const val TIRAMISU = 33

    // Строки, а не Manifest.permission.*: константы новее minSdk 26 дают lint InlinedApi.
    const val BLUETOOTH_SCAN: String = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_ADVERTISE: String = "android.permission.BLUETOOTH_ADVERTISE"
    const val BLUETOOTH_CONNECT: String = "android.permission.BLUETOOTH_CONNECT"
    const val NEARBY_WIFI_DEVICES: String = "android.permission.NEARBY_WIFI_DEVICES"
    const val ACCESS_FINE_LOCATION: String = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION: String = "android.permission.ACCESS_COARSE_LOCATION"

    /**
     * Android 13+ — Bluetooth рядом + Wi-Fi рядом; 12 — Bluetooth рядом + геолокация (Wi-Fi без неё не ищется);
     * до 12 — геолокация. FINE и COARSE — одним запросом (Android 12 иначе даёт только приблизительную).
     */
    fun runtimeFor(sdkInt: Int): List<String> = when {
        sdkInt >= TIRAMISU -> listOf(BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT, NEARBY_WIFI_DEVICES)

        sdkInt >= S -> listOf(
            BLUETOOTH_SCAN,
            BLUETOOTH_ADVERTISE,
            BLUETOOTH_CONNECT,
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION,
        )

        else -> listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
    }

    /** Android 12 (API 31–32): точная геолокация нужна, а пользователь может дать только приблизительную. */
    fun asksPreciseLocation(sdkInt: Int): Boolean = sdkInt in S until TIRAMISU

    /** Нужна ли включённая геолокация (служба), а не только разрешение. */
    fun needsLocationService(sdkInt: Int): Boolean = sdkInt < S
}

class AndroidTransferEnvironment @Inject constructor(@ApplicationContext private val context: Context) :
    TransferEnvironment {
    override fun isNearbyAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override fun missingPermissions(): List<String> = TransferPermissions.runtimeFor(Build.VERSION.SDK_INT)
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    override fun isApproximateLocationOnly(): Boolean {
        if (!TransferPermissions.asksPreciseLocation(Build.VERSION.SDK_INT)) return false
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return granted(TransferPermissions.ACCESS_COARSE_LOCATION) && !granted(TransferPermissions.ACCESS_FINE_LOCATION)
    }

    override fun isLocationServiceRequiredButOff(): Boolean {
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
        return TransferPermissions.needsLocationService(Build.VERSION.SDK_INT) &&
            manager != null &&
            !LocationManagerCompat.isLocationEnabled(manager)
    }
}
