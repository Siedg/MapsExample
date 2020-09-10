package com.siedg.mapsexample

import com.google.android.gms.maps.model.LatLng

class CustomMarker(var latLng: LatLng, var title: String, var isAdded: Boolean) {
    constructor() : this(LatLng(-1.0, -1.0), "", false)
}