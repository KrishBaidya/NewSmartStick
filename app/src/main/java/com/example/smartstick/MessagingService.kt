package com.example.smartstick

import android.R
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class MessagingService : FirebaseMessagingService() {

    val database = Firebase.database
    override fun onCreate() {
        super.onCreate()

        Log.d("my app" , "HISHIHSI")

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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d("TAG", "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("TAG", "Message data payload: ${remoteMessage.data}")
        }

        // Check if message contains a notification payload.
        if(remoteMessage.data.isNotEmpty()){
            var notificationBuilder = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("My notification title")
                .setContentText("This is my notification")
                .setSmallIcon(R.drawable.sym_def_app_icon)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            var notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            var longitude = remoteMessage.data["longitude"]
            var latitude = remoteMessage.data["latitude"]
            val gmmIntentUri = Uri.parse("geo:${longitude},${latitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.resolveActivity(packageManager)?.let {
                startActivity(mapIntent)
            }

            val pendingIntent = PendingIntent.getActivity(this, 0, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notificationBuilder.addAction(R.drawable.sym_def_app_icon, "Open app", pendingIntent)
            notificationManager.notify(0, notificationBuilder.build());
        }
        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
}

