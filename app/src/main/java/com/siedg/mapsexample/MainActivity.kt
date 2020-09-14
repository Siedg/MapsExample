package com.siedg.mapsexample

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import com.siedg.mapsexample.retrofit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Time
import java.time.LocalTime
import java.util.*
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.log

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    private val TAG = "MainActivity"
    lateinit var mapFragment : SupportMapFragment
    lateinit var gMap : GoogleMap
    lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var ctx : Context
    lateinit var mClusterManager: ClusterManager<ClusterLocation>
    private lateinit var lastLocation: android.location.Location
    private var markers = mutableListOf<CustomMarker>()
    private var hashMapCluster = HashMap<LatLng, ClusterLocation>()
    private var debounceJob: Job? = null

    //retrofit
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ctx = this

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //download with retrofit and coroutines
        setupViewModel()
        DownloadLocations()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap

        // Checks if the apps has the ACCESS_FINE_LOCATION permission, if not, request it from the user
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
        gMap.isMyLocationEnabled = true
        gMap.uiSettings.isZoomControlsEnabled = true
        gMap.setOnMarkerClickListener(this)
        gMap.setOnCameraMoveListener {
            debounceJob?.cancel()
        }

        // Get user location
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) {
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                gMap.addMarker(
                    MarkerOptions()
                        //.icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.mipmap.ic_user_location)))
                        .position(currentLatLng)
                        .title("You are here")
                )
                setUpCluster()
                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
            }
        }
    }

    private fun setUpCluster() {
        mClusterManager = ClusterManager(this, gMap)
        gMap.setOnCameraIdleListener {
            debounceJob?.cancel()
            debounceJob = CoroutineScope(GlobalScope.coroutineContext).launch {
                delay(1000)
                withContext(Dispatchers.Main) {
                    setupMarkers()
                    mClusterManager.onCameraIdle()
                }
            }
        }
        gMap.setOnMarkerClickListener(mClusterManager)
    }

    private fun updateCluster() {
        mClusterManager.clearItems()
        for (value in hashMapCluster.values) {
            mClusterManager.addItem(value)
        }
    }

    @Synchronized private fun setupMarkers() {
        val bounds = gMap.projection.visibleRegion.latLngBounds
        for (index in 0 until markers.size) {
            val marker = markers[index]
            val clusterLocation = ClusterLocation(
                marker.latLng.latitude,
                marker.latLng.longitude,
                marker.title,
                marker.latLng.toString()
            )
            // Checks if the current marker is in the visible area and is not on the hashMapCluster
            if (bounds.contains(marker.latLng) && !hashMapCluster.containsKey(marker.latLng)) {
                hashMapCluster.put(marker.latLng, clusterLocation)
                Log.d(TAG, "Marker added, size: ${hashMapCluster.size}")
                Toast.makeText(ctx, "Marker ${marker.title} added", Toast.LENGTH_SHORT).show()
            }
            // Checks if the current marker is not in the visible area and is in the hashMapCluster
            else if (!bounds.contains(marker.latLng) && hashMapCluster.containsKey(marker.latLng)) {
                Log.d(TAG, "Marker removed, size: ${hashMapCluster.size}")
                hashMapCluster.remove(marker.latLng)
                Toast.makeText(ctx, "Marker ${marker.title} removed", Toast.LENGTH_SHORT).show()
            }
            updateCluster()
        }
    }

    override fun onMarkerClick(p0: Marker?) = false

    //download data with retrofit and coroutines
    private fun setupViewModel() {
        viewModel = ViewModelProviders.of(
            this,
            ViewModelFactory(ApiHelper(RetrofitBuilder.apiService))
        ).get(MainViewModel::class.java)
    }

    private fun DownloadLocations() {
        viewModel.getData().observe(this, Observer {
            it?.let { resource ->
                when (resource.status) {
                    Status.SUCCESS -> {
                        resource.data?.let { data ->
                            data.locations.map {
                                var latLng = LatLng(it.latitude,it.longitude)
                                val customMarker = CustomMarker(latLng, it.name)
                                if (!markers.contains(customMarker)) markers.add(customMarker)
                            }
                        }
                        setupMarkers()
                    }
                    Status.ERROR -> {
                        Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                    }
                    Status.LOADING -> {
                    }
                }
            }
        })
    }
}