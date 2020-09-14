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
    private var url = "https://raw.githubusercontent.com/Siedg/scholar.py/master/locations.json"
    private var markers = mutableListOf<CustomMarker>()
    private var markerList = mutableListOf<Marker>()
    private var hashMapMarker = HashMap<LatLng, Marker>()
    private var hashMapCluster = HashMap<LatLng, ClusterLocation>()
    private var currentTime = 0.toLong()
    private var lastTime = 0.toLong()
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
//        DownloadData().execute(url)



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

    // Load visible markers and delete not visible ones
    private fun addVisibleMarkers() {
        val bounds = gMap.projection.visibleRegion.latLngBounds
        for (index in 0 until markers.size) {
            val currentMarker = MarkerOptions().position(markers[index].latLng).title(markers[index].title)
            if (bounds.contains(markers[index].latLng) && !hashMapMarker.containsKey(currentMarker.position)) {
                hashMapMarker.put(currentMarker.position, gMap.addMarker(MarkerOptions().position(markers[index].latLng).title(markers[index].title)))
                Toast.makeText(ctx,"Marker $index added", Toast.LENGTH_SHORT).show()
            } else if (!bounds.contains(markers[index].latLng) && hashMapMarker.containsKey(currentMarker.position)) {
                hashMapMarker[currentMarker.position]?.remove()
                hashMapMarker.remove(currentMarker.position)
                Toast.makeText(ctx,"Marker $index removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Set visibility true only for the markers visible on the map
    private fun showVisibleMarkets() {
        val bounds = gMap.projection.visibleRegion.latLngBounds
        for (index in 0 until markerList.size) {
            val pos = LatLng(markerList[index].position.latitude, markerList[index].position.longitude)
            if (bounds.contains(pos)) {
                markerList[index].isVisible = true
                Toast.makeText(ctx,"Marker $index is now visible", Toast.LENGTH_SHORT).show()
            } else {
                markerList[index].isVisible = false
            }
        }
    }

    // Add a few more markers
    private fun addMarkers() {
        markers.add(CustomMarker(LatLng(-33.890542, 151.274856), "Bondi Beach"))
        markers.add(CustomMarker(LatLng(-33.923036, 151.259052), "Coogee Beach"))
        markers.add(CustomMarker(LatLng(-34.028249, 151.157507), "Cronulla Beach"))
        markers.add(CustomMarker(LatLng(-33.80010128657071, 151.28747820854187), "Manly Beach"))
        markers.add(CustomMarker(LatLng(-33.950198, 151.259302), "Maroubra Beach"))
    }

    // Get addresses from Google API with LatLng
    private fun getAddress(latLng: LatLng): String {
        val geocoder = Geocoder(this)
        val addresses: List<Address>?
        val address: Address?
        var addressText = ""

        try {
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (null != addresses && !addresses.isEmpty()) {
                address = addresses[0]
                for (index in 0 until address.maxAddressLineIndex) {
                    addressText += if (index == 0) address.getAddressLine(index) else "\n" + address.getAddressLine(
                        index
                    )
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("MainActivity", e.localizedMessage!!)
        }
        return addressText
    }

    private fun dbTest(delayMs: Long): Boolean {
        currentTime = Calendar.getInstance().timeInMillis
        val res = (currentTime - lastTime) > delayMs
        if ((currentTime - lastTime) > delayMs ) {
            lastTime = Calendar.getInstance().timeInMillis
            return true
        }
        return false
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

    // Adds a few locations near London for clustering
    private fun addItems() {
        // Set some lat/lng coordinates to start with.
        var lat = 51.5145160
        var lng = -0.1270060

        // Add ten cluster items in close proximity, for purposes of this example.
        for (i in 0..9) {
            val offTitle = "Location $i"
            val offSnippet = "Snippet $i"
            val offset = i / 60.0
            lat += offset
            lng += offset
            val offsetItem = ClusterLocation(lat, lng, offTitle, offSnippet)
            mClusterManager.addItem(offsetItem)
        }
    }

    override fun onMarkerClick(p0: Marker?) = false

    // Get location data from link
    inner class DownloadData : AsyncTask<String, String, String>() {
        override fun onPreExecute() {
        }
        // For build connection
        override fun doInBackground(vararg p0: String?): String {
            try {
                val url = URL(p0[0])
                val urlConnect = url.openConnection() as HttpURLConnection
                urlConnect.connectTimeout = 700
                val inputStream = urlConnect.inputStream
                val dataJsonAsStr = covertStreamToString(urlConnect.inputStream)
                publishProgress(dataJsonAsStr)

            }   catch (e: Exception){
            }
            return ""
        }

        // For get items from json api
        override fun onProgressUpdate(vararg values: String?) {
            val json = JSONObject(values[0]!!)
            val locations = json.getJSONArray("locations")

            for (index in 0 until locations.length()) {
                val locale = locations.getJSONObject(index)
                val name = locale.get("name")
                val latitude = locale.get("latitude")
                val longitude = locale.get("longitude")
                val description = locale.get("description")

                val latLng = LatLng(latitude.toString().toDouble(), longitude.toString().toDouble())

                val customMarker = CustomMarker(latLng, name.toString())
                if (!markers.contains(customMarker)) markers.add(customMarker)
                Toast.makeText(ctx,"Locations loaded: " + locations.length().toString(), Toast.LENGTH_SHORT).show()
//                setupMarkers() // Add markers on the map
            }
        }
        override fun onPostExecute(result: String?) {
        }
    }

    // For connection api
    fun covertStreamToString(inputStream: InputStream): String {
        val bufferReader = BufferedReader(InputStreamReader(inputStream))
        var line:String
        var  allString:String=""
        try {
            do{
                line=bufferReader.readLine()
                if (line!=null)
                    allString+=line
            }while (line!=null)

            bufferReader.close()
        }catch (ex: java.lang.Exception){}

        return allString;
    }

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

fun <T> debounce(
    waitMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
}