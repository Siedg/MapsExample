package com.siedg.mapsexample.retrofit

import com.siedg.mapsexample.retrofit.model.ResultLocations
import retrofit2.http.GET

interface ApiService {

    @GET("locations.json")
    suspend fun getData() : ResultLocations
}