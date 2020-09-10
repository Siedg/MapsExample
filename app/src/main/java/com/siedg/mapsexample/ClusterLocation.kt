package com.siedg.mapsexample

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

class ClusterLocation(lat: Double, lng: Double, title: String, snippet: String) : ClusterItem {
    private var mPosition: LatLng
    private var mTitle: String = title
    private var mSnippet: String = snippet

    init {
        mPosition = LatLng(lat, lng)
    }

    override fun getPosition(): LatLng {
        return mPosition
    }

    override fun getTitle(): String? {
        return mTitle
    }

    override fun getSnippet(): String? {
        return mSnippet
    }
}