package com.example.smartstick

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class MessagingService : FirebaseMessagingService() {
    private var mMap: GoogleMap? = null
    val database = Firebase.database

    override fun onCreate() {
        super.onCreate()

        Log.d("my app" , "HISHIHSI")

        // Write a message to the database
        val database = Firebase.database
        val myRef = database.getReference("coord")

        myRef.addValueEventListener(object: ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                val value = snapshot.getValue<HashMap<String, Any?>>()
                Log.d("TAG", "Value is: $value")

                MessageRecievedFromDB(value?.get("lat") as Double?, value?.get("long") as Double?)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("TAG", "Failed to read value.", error.toException())
            }

        })

        val firebaseMessaging = FirebaseMessaging.getInstance()
        firebaseMessaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Get the new token.
                val token = task.result
                Log.d("Registered" , token)
                val myRef = database.getReference("token")
                myRef.setValue(token)
            } else {
                // Handle the error.
            }
        }
    }
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("my app" , token)
    }

    private fun openMaps(context: Context, longitude : String, latitude : String): Intent {
        val mapIntent: Intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("geo:${latitude},${longitude}?q=${latitude},${longitude}")
        )
        return mapIntent
    }

    fun MessageRecievedFromDB(lat: Double?, long: Double?){
        val name = "R.string.channel_name"
        val descriptionText = "R.string.channel_description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel("CHANNEL_ID", name, importance)
        mChannel.description = descriptionText
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)

        // Check if message contains a data payload.
        if ((lat != null) && (long != null)) {
            Log.d("TAG", "Message data payload: ${long}, $lat")

            // Create a PendingIntent
            val pendingIntent = PendingIntent.getActivity(this, 0, openMaps(this , long.toString(), lat.toString()),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

            val notificationBuilder = NotificationCompat.Builder(this, "CHANNEL_ID")
                .setContentTitle("Patient needs help")
                .setContentText("Patient needs help")
                .setSmallIcon(com.example.smartstick.R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            // Send the notification
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(0, notificationBuilder.build())
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // ...

        Log.d("Hello" , remoteMessage.notification.toString())

        val name = "R.string.channel_name"
        val descriptionText = "R.string.channel_description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel("CHANNEL_ID", name, importance)
        mChannel.description = descriptionText
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d("TAG", "From: ${remoteMessage.from}")
        Log.d("TAG", "Message data payload: ${remoteMessage.data}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("TAG", "Message data payload: ${remoteMessage.data}")

            // Create a PendingIntent
            val pendingIntent = PendingIntent.getActivity(this, 0, openMaps(this , remoteMessage.data["longitude"]!! , remoteMessage.data["latitude"]!!),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

            val notificationBuilder = NotificationCompat.Builder(this, "CHANNEL_ID")
                .setContentTitle("Patient needs help")
                .setContentText("Patient needs help")
                .setSmallIcon(com.example.smartstick.R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            // Send the notification
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(0, notificationBuilder.build())
        }
        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
}

