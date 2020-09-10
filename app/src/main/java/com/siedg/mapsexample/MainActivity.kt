package com.siedg.mapsexample

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.icu.text.CaseMap
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    lateinit var mapFragment : SupportMapFragment
    lateinit var gMap : GoogleMap
    lateinit var currentLocation: Location
    lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var ctx : Context
    lateinit var mClusterManager: ClusterManager<ClusterLocation>
    private lateinit var lastLocation: android.location.Location
    private var url = "https://raw.githubusercontent.com/Siedg/scholar.py/master/locations.json"
    private var markers = mutableListOf<CustomMarker>()
    private var markerList = mutableListOf<Marker>()
    private var addedMarkers = mutableListOf<MarkerOptions>()
    private var hashMapMarker = HashMap<LatLng, Marker>()

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ctx = this

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        DownloadData().execute(url)

        print("=========================\n")
        //print(gMap.cameraPosition.target.latitude.toString() + " " + gMap.cameraPosition.target.longitude.toString())
        print("\n=========================\n")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        addMarkers()

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


        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location != null) {
                lastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                gMap.addMarker(
                    MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.mipmap.ic_user_location)))
                        .position(currentLatLng)
                        .title("You are here")
                )
                setUpCluster()
                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                gMap.setOnCameraIdleListener {
//                    showVisibleMarkets()
                    addVisibleMarkers()
                }

            }
        }
    }

    private fun addVisibleMarkers() {
        var bounds = gMap.projection.visibleRegion.latLngBounds
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

    private fun showVisibleMarkets() {
        var bounds = gMap.projection.visibleRegion.latLngBounds

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

    private fun addMarkers() {
        markers.add(CustomMarker(LatLng(-33.890542, 151.274856), "Bondi Beach", false))
        markers.add(CustomMarker(LatLng(-33.923036, 151.259052), "Coogee Beach", false))
        markers.add(CustomMarker(LatLng(-34.028249, 151.157507), "Cronulla Beach", false))
        markers.add(CustomMarker(LatLng(-33.80010128657071, 151.28747820854187), "Manly Beach", false))
        markers.add(CustomMarker(LatLng(-33.950198, 151.259302), "Maroubra Beach", false))
        markerList.add(gMap.addMarker(MarkerOptions().position(LatLng(-33.890542, 151.274856)).title("Bondi Beach")))
        markerList.add(gMap.addMarker(MarkerOptions().position(LatLng(-33.923036, 151.259052)).title("Coogee Beach")))
        markerList.add(gMap.addMarker(MarkerOptions().position(LatLng(-34.028249, 151.157507)).title("Cronulla Beach")))
        markerList.add(gMap.addMarker(MarkerOptions().position(LatLng(-33.80010128657071, 151.28747820854187)).title("Manly Beach")))
        markerList.add(gMap.addMarker(MarkerOptions().position(LatLng(-33.950198, 151.259302)).title("Maroubra Beach")))
    }

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
            Log.e("MainActivity", e.localizedMessage)
        }
        return addressText
    }

    private fun calculateNearbyMarkers(radius: Double) {
        // Get current location -> Get search radius -> Return nearby merkers list? / Add on map
        // Distance Matrix Service API
    }

    private fun loadMarkersDynamically() {
//        gMap.setClustering(
//            ClusteringSettings()
//                .enabled(false)
//                .addMarkersDynamically(true)
//        )

        // Android Maps Extensions
        // Clusterkraf
        // Androis Maps Utils -> Clustering branch
    }

    private fun setUpCluster() {
        mClusterManager = ClusterManager(this, gMap)
        gMap.setOnCameraIdleListener(mClusterManager)
        gMap.setOnMarkerClickListener(mClusterManager)
        addItems()
    }

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

    inner class DownloadData : AsyncTask<String, String, String>() {
        override fun onPreExecute() {
        }
        // for build connection
        override fun doInBackground(vararg p0: String?): String{
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

        // for get items from json api
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

                gMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
//                        .title(getAddress(latLng))
                        .title(name.toString())
                )
                Toast.makeText(ctx,"Locations loaded: " + locations.length().toString(), Toast.LENGTH_SHORT).show()
            }


        }
        override fun onPostExecute(result: String?) {
        }
    }

    // for connection api
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
}