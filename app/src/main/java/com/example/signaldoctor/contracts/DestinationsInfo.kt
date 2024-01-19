package com.example.signaldoctor.contracts

import android.content.Intent
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink

object DestinationsInfo {

    const val DEEP_LINK_URI_PREFIX = "navigation://signal_doctor/"

    object DestinationGraphMain{
        const val route="maingraph"
    }

     object MapScreen{

         const val routePath = "mapscreen"

         const val CENTER_LOCATION_ARGUMENT_NAME = "centerMapOnSavedLocation"

         const val routeQuery = "?$CENTER_LOCATION_ARGUMENT_NAME={$CENTER_LOCATION_ARGUMENT_NAME}"

        const val route = routePath + routeQuery


         val navArguments = listOf(
             navArgument(name = CENTER_LOCATION_ARGUMENT_NAME){
                 type = NavType.BoolType
                 defaultValue = true
             }
         )
    }

    object SettingsScreen{

        const val route= "settingsscreen"

        const val LAUNCH_FROM_SERVICE_URI = DEEP_LINK_URI_PREFIX + route



        val navDeepLinks = listOf(
            navDeepLink {
                action = Intent.ACTION_VIEW
                uriPattern = LAUNCH_FROM_SERVICE_URI
            }
        )

    }
}