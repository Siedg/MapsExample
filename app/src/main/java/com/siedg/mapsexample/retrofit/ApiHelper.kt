package com.siedg.mapsexample.retrofit

class ApiHelper(private val apiService: ApiService) {

    suspend fun getData() = apiService.getData()
}