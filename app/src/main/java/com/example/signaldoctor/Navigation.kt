package com.example.signaldoctor

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.screens.MapScreen
import com.example.signaldoctor.viewModels.MyViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.config.Configuration.getInstance

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val viewModel: MyViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContent {
            NavHost(
                navController = rememberNavController() ,
                startDestination = DestinationsInfo.MapScreen.route,
            ){

                //MapScreen route
                composable(route = DestinationsInfo.MapScreen.route){
                    MapScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.map.onPause()
    }
}

