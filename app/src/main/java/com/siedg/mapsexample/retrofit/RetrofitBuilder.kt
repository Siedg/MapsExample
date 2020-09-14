package com.siedg.mapsexample.retrofit

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitBuilder {

    private const val BASE_URL = "https://raw.githubusercontent.com/Siedg/scholar.py/master/"

    private fun getRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build() //Doesn't require the adapter
    }
    val apiService: ApiService = getRetrofit().create(ApiService::class.java)
}