package yyl.example.weatherapp.network

import android.text.BoringLayout.Metrics
import retrofit.Call
import retrofit.http.GET
import retrofit.http.Query
import yyl.example.weatherapp.models.WeatherResponse

interface WeatherService {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?
    ): Call<WeatherResponse>
}