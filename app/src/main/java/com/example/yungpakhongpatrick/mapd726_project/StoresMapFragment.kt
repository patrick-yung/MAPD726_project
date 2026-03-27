package com.example.yungpakhongpatrick.mapd726_project

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class StoresMapFragment : Fragment(R.layout.fragment_stores_map), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private var lastKnownLocation: Location? = null

    private lateinit var tvStore1: TextView
    private lateinit var tvStore2: TextView
    private lateinit var tvStore3: TextView

    private lateinit var btnFavorite1: Button
    private lateinit var btnFavorite2: Button
    private lateinit var btnFavorite3: Button

    private lateinit var btnDisable1: Button
    private lateinit var btnDisable2: Button
    private lateinit var btnDisable3: Button

    private lateinit var btnRefreshLocation: Button
    private lateinit var btnResetStores: Button

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineGranted || coarseGranted) {
                enableUserLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStore1 = view.findViewById(R.id.tvStore1)
        tvStore2 = view.findViewById(R.id.tvStore2)
        tvStore3 = view.findViewById(R.id.tvStore3)

        btnFavorite1 = view.findViewById(R.id.btnFavorite1)
        btnFavorite2 = view.findViewById(R.id.btnFavorite2)
        btnFavorite3 = view.findViewById(R.id.btnFavorite3)

        btnDisable1 = view.findViewById(R.id.btnDisable1)
        btnDisable2 = view.findViewById(R.id.btnDisable2)
        btnDisable3 = view.findViewById(R.id.btnDisable3)

        btnRefreshLocation = view.findViewById(R.id.btnRefreshLocation)
        btnResetStores = view.findViewById(R.id.btnResetStores)

        btnRefreshLocation.setOnClickListener {
            requestFreshLocation()
        }

        btnResetStores.setOnClickListener {
            StoreRepository.resetStores()
            Toast.makeText(requireContext(), "All stores reset", Toast.LENGTH_SHORT).show()
            lastKnownLocation?.let { showClosestStores(it) } ?: requestFreshLocation()
        }

        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val fallback = LatLng(43.6532, -79.3832)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, 10f))
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        map.setPadding(0, 0, 0, 190)

        checkLocationPermissionAndEnable()
    }

    private fun checkLocationPermissionAndEnable() {
        if (hasLocationPermission()) {
            enableUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarsePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return finePermission || coarsePermission
    }

    private fun enableUserLocation() {
        if (!hasLocationPermission()) return

        try {
            googleMap?.isMyLocationEnabled = true
        } catch (_: SecurityException) {
        }

        requestFreshLocation()
    }

    private fun requestFreshLocation() {
        if (!hasLocationPermission()) return

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        lastKnownLocation = location
                        showClosestStores(location)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Could not get current location. Use the emulator geo fix command, then tap Refresh.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        requireContext(),
                        "Could not refresh location.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (e: SecurityException) {
            Toast.makeText(
                requireContext(),
                "Location permission is required to get your current location.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showClosestStores(userLocation: Location) {
        val userLatLng = LatLng(userLocation.latitude, userLocation.longitude)

        val closestStores = StoreRepository.stores
            .filter { it.isEnabled }
            .sortedBy {
                distanceInMeters(
                    userLocation.latitude,
                    userLocation.longitude,
                    it.latitude,
                    it.longitude
                )
            }
            .take(3)

        googleMap?.clear()
        googleMap?.addMarker(
            MarkerOptions()
                .position(userLatLng)
                .title("You are here")
                .snippet("Current device location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        if (closestStores.isEmpty()) {
            tvStore1.text = "No enabled stores. Tap Reset."
            tvStore2.text = ""
            tvStore3.text = ""

            btnFavorite1.visibility = View.GONE
            btnFavorite2.visibility = View.GONE
            btnFavorite3.visibility = View.GONE
            btnDisable1.visibility = View.GONE
            btnDisable2.visibility = View.GONE
            btnDisable3.visibility = View.GONE
            return
        }

        val boundsBuilder = LatLngBounds.Builder()
        boundsBuilder.include(userLatLng)

        closestStores.forEachIndexed { index, store ->
            val storeLatLng = LatLng(store.latitude, store.longitude)
            val distanceKm = distanceInMeters(
                userLocation.latitude,
                userLocation.longitude,
                store.latitude,
                store.longitude
            ) / 1000.0

            val markerColor = if (store.isFavorite) {
                BitmapDescriptorFactory.HUE_YELLOW
            } else {
                BitmapDescriptorFactory.HUE_RED
            }

            googleMap?.addMarker(
                MarkerOptions()
                    .position(storeLatLng)
                    .title("${index + 1}. ${store.name}")
                    .snippet(
                        "Rating: ${String.format(Locale.getDefault(), "%.1f", store.rating)}★\n" +
                                "Distance: ${String.format(Locale.getDefault(), "%.2f km", distanceKm)}\n" +
                                store.address
                    )
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )

            boundsBuilder.include(storeLatLng)
        }

        googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 140))
        updateStoreControls(userLocation, closestStores)
    }

    private fun updateStoreControls(userLocation: Location, closestStores: List<StoreLocation>) {
        updateSingleStoreRow(0, userLocation, closestStores, tvStore1, btnFavorite1, btnDisable1)
        updateSingleStoreRow(1, userLocation, closestStores, tvStore2, btnFavorite2, btnDisable2)
        updateSingleStoreRow(2, userLocation, closestStores, tvStore3, btnFavorite3, btnDisable3)
    }

    private fun updateSingleStoreRow(
        rowIndex: Int,
        userLocation: Location,
        stores: List<StoreLocation>,
        tvStore: TextView,
        btnFavorite: Button,
        btnDisable: Button
    ) {
        if (rowIndex >= stores.size) {
            tvStore.text = ""
            btnFavorite.visibility = View.GONE
            btnDisable.visibility = View.GONE
            return
        }

        val store = stores[rowIndex]
        val distanceKm = distanceInMeters(
            userLocation.latitude,
            userLocation.longitude,
            store.latitude,
            store.longitude
        ) / 1000.0

        tvStore.text = buildString {
            if (store.isFavorite) append("★ ")
            append(store.name)
            append("\n")
            append(String.format(Locale.getDefault(), "%.1f★  •  %.2f km", store.rating, distanceKm))
        }

        btnFavorite.visibility = View.VISIBLE
        btnDisable.visibility = View.VISIBLE
        btnFavorite.text = if (store.isFavorite) "Unfavorite" else "Favorite"

        btnFavorite.setOnClickListener {
            store.isFavorite = !store.isFavorite
            lastKnownLocation?.let { showClosestStores(it) }
        }

        btnDisable.setOnClickListener {
            store.isEnabled = false
            Toast.makeText(requireContext(), "${store.name} disabled", Toast.LENGTH_SHORT).show()
            lastKnownLocation?.let { showClosestStores(it) }
        }
    }

    private fun distanceInMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }
}