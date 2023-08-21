package com.example.smartstick

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "IsPatientOrCareTaker")

val IsPatientOrCareTaker = intPreferencesKey("IsPatientOrCareTaker")

class Login : AppCompatActivity() {
    lateinit var PatientButton: Button
    lateinit var CareTakerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        PatientButton = findViewById(R.id.PatientButton)
        CareTakerButton = findViewById(R.id.CaretakerButton)


        val Credit: TextView = findViewById(R.id.Credit)
        Credit.movementMethod = LinkMovementMethod.getInstance()


        PatientButton.setOnClickListener {
            PatientLogin()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        CareTakerButton.setOnClickListener {
            CareTakerLogin()
        }

        getLogin()

        Log.d("TEST" , flowData.toString())

        if (flowData == 1){
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
    var flowData = 0
    private fun getLogin(){
        val exampleCounterFlow: Flow<Int> = this.dataStore.data
            .map { preferences ->
                // No type safety.
                preferences[IsPatientOrCareTaker] ?: 0
            }
        /*suspend fun getlogin(){
            exampleCounterFlow.collect {
                flowData = it
            }
        }
        */

        Log.d("MY APP NEW TEST" , "WORKING HERE")
        var restlt = kotlinx.coroutines.runBlocking {
            suspend {
                exampleCounterFlow.collect {
                    flowData = it

                    Log.d("MY APP NEW TEST" , "WORKING HERE")
                }
                Log.d("MY APP TEST" , "WORKING HERE")
            }
            Log.d("MYAPPP " , "WORKING HERE")
        }
    }

    private fun CareTakerLogin() {
        suspend {
            this.dataStore.edit { settings ->
                settings[IsPatientOrCareTaker] = 2
            }
            finish()
        }
    }
    private fun PatientLogin() {
        suspend {
            this.dataStore.edit { settings ->
                settings[IsPatientOrCareTaker] = 1
            }
            finish()
        }
    }
}