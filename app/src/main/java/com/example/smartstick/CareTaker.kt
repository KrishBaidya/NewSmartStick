package com.example.smartstick

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.util.logging.Logger

class CareTaker : AppCompatActivity() {
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