package yyl.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import androidx.appcompat.widget.Toolbar
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*
import yyl.example.weatherapp.R.layout.dialog_custom_progress
import yyl.example.weatherapp.models.Weather
import yyl.example.weatherapp.Constants
import yyl.example.weatherapp.models.WeatherResponse
import yyl.example.weatherapp.network.WeatherService

class MainActivity : AppCompatActivity() {

    // A fused location client variable which is used to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mPorgressDislog : Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // redirect to settings to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            // Asking the location permission on runtime
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            // Call the location request function here
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    /**
     * A function which is used to verify that the location or GPS is enable or not of the user's device.
     */
    private fun isLocationEnabled(): Boolean {

        // access to the system location services
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // A location callback object of fused location provider client where we will get the current location details.
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                @SuppressLint("SetTextI18n")
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()
                        Log.i("Response Result", "$weatherList")

                        val weatherResponseString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseString)
                        editor.apply()

                        setupUI()
                    }else{
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    Log.e("Errrorrrrrr", t!!.message.toString())
                    hideProgressDialog()
                }

            })

        }else{
            Toast.makeText(
                this@MainActivity,
                "No internet connection.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //show the alert dialog when the permissions are denied and need to allow it from settings app info
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    // request the current location. Using the fused location provider client
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private fun showCustomProgressDialog() {
        mPorgressDislog = Dialog(this)
        //set the screen content from the layout resource
        mPorgressDislog!!.setContentView(R.layout.dialog_custom_progress)
        //start the dialog and display
        mPorgressDislog!!.show()
    }

    private fun hideProgressDialog() {
        if(mPorgressDislog != null){
            mPorgressDislog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI(){

        val weatherResponseString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        val tv_main = findViewById<TextView>(R.id.tv_main)
        val tv_main_description = findViewById<TextView>(R.id.tv_main_description)
        val tv_temp = findViewById<TextView>(R.id.tv_temp)
        val tv_humidity = findViewById<TextView>(R.id.tv_humidity)
        val tv_min = findViewById<TextView>(R.id.tv_min)
        val tv_max = findViewById<TextView>(R.id.tv_max)
        val tv_speed = findViewById<TextView>(R.id.tv_speed)
        val tv_name = findViewById<TextView>(R.id.tv_name)
        val tv_country = findViewById<TextView>(R.id.tv_country)
        val tv_sunrise_time = findViewById<TextView>(R.id.tv_sunrise_time)
        val tv_sunset_time = findViewById<TextView>(R.id.tv_sunset_time)

        val iv_main = findViewById<ImageView>(R.id.iv_main)

        if(!weatherResponseString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseString, WeatherResponse::class.java)
            for (i in weatherList.weather.indices){
                Log.i("Weather Name", weatherList.weather.toString())

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getUnit()
                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise.toLong())
                tv_sunset_time.text = unixTime(weatherList.sys.sunset.toLong())

                // we update the main icon
                // https://openweathermap.org/weather-conditions#Weather-Condition-Codes-2
                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }

    }

    private fun getUnit(): String? {
        val countryCode: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // For API 24 and above
            application.resources.configuration.locales[0].country
        } else {
            // For API below 24
            application.resources.configuration.locale.country
        }

        // Call the getUnit function to determine if it’s °C or °F
        return if (countryCode == "US" || countryCode == "LR" || countryCode == "MM") {
            "°F"
        } else {
            "°C"
        }
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}