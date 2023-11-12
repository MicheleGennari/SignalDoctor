package com.example.signaldoctor.appComponents

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.signaldoctor.R
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.screens.MapScreen
import com.example.signaldoctor.appComponents.viewModels.MyViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.config.Configuration.getInstance

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContent {
            val viewModel : MyViewModel = viewModel()

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

}

