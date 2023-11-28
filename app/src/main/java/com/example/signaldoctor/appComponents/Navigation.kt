package com.example.signaldoctor.appComponents

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.screens.MapScreen
import com.example.signaldoctor.appComponents.viewModels.MyViewModel
import com.example.signaldoctor.screens.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration.getInstance

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController ,
                startDestination = DestinationsInfo.DestinationGraphMain.route,
            ){
                navigation(
                    startDestination = DestinationsInfo.MapScreen.route,
                    route = DestinationsInfo.DestinationGraphMain.route
                ){
                    //MapScreen route
                    composable(
                        route = DestinationsInfo.MapScreen.route,
                        exitTransition = {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(easing = EaseIn)
                                )
                        }
                    ) {backStackEntry->

                        val mainGraph = remember(backStackEntry){
                            navController.getBackStackEntry(DestinationsInfo.DestinationGraphMain.route)
                        }

                        val viewModel : MyViewModel = hiltViewModel(mainGraph)

                        MapScreen(
                            viewModel = viewModel,
                            navigateToSettings = {
                                navController.navigate(DestinationsInfo.SettingsScreen.route)
                            }
                        )
                    }

                    composable(
                        route = DestinationsInfo.SettingsScreen.route,
                        enterTransition = {
                            slideIntoContainer(
                                animationSpec = tween( easing = EaseIn),
                                towards = AnimatedContentTransitionScope.SlideDirection.Start
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                animationSpec = tween( easing = EaseOut),
                                towards = AnimatedContentTransitionScope.SlideDirection.End
                            )
                        }
                    ) {backStackEntry ->

                        val mainGraph = remember(backStackEntry){
                            navController.getBackStackEntry(DestinationsInfo.DestinationGraphMain.route)
                        }

                        val viewModel : MyViewModel = hiltViewModel(mainGraph)

                        SettingsScreen(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

    }

}

fun AnimatedContentTransitionScope<NavBackStackEntry>.SettingsScreenEnterTransition() : EnterTransition? {
    return slideInHorizontally { 0 }
}