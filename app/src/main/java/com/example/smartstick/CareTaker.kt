package com.example.smartstick

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions


class CareTaker : AppCompatActivity(){


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_care_taker)

        //var a = CloudMessagingClient()
        //a.getToken()

        //startService(Intent(this@CareTaker , CloudMessagingClient::class.java))
        startService(Intent(this@CareTaker , MessagingService::class.java))

        Log.d("my app", "working here")
    }

    override fun onDestroy() {
        super.onDestroy()

        //stopService(Intent(this@CareTaker , CloudMessagingClient::class.java))
        stopService(Intent(this@CareTaker , MessagingService::class.java))
    }
}