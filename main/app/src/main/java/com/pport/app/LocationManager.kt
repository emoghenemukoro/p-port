package com.pport.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class LocationManager(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getLocation(onResult: (Location?) -> Unit) {
        // First, try the cached last known location instantly
        fusedClient.lastLocation
            .addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    onResult(lastLoc)
                }
            }
            .addOnFailureListener {
                // ignore, we'll request fresh
            }

        // Now request a fresh location (this will also trigger the emulator to use the mock point)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdates(1)          // only need one fix
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    onResult(loc)
                    // stop updates after first good fix
                    fusedClient.removeLocationUpdates(this)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // Not used
            }
        }

        fusedClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )
    }
}