package com.siedg.mapsexample.retrofit

class MainRepository (private val apiHelper: ApiHelper) {

    suspend fun getData() = apiHelper.getData()
}