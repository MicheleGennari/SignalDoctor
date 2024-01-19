package com.example.signaldoctor.appComponents

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavActionBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.example.signaldoctor.contracts.DestinationsInfo
import com.example.signaldoctor.screens.MapScreen
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
                ){//MapScreen route
                    composable(
                        route = DestinationsInfo.MapScreen.route,
                        arguments = DestinationsInfo.MapScreen.navArguments,
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(easing = EaseIn)
                            )
                        }
                    ) { backStackEntry ->


                        backStackEntry.savedStateRegistry

                        val parent = remember(backStackEntry){
                            navController.getBackStackEntry(DestinationsInfo.DestinationGraphMain.route)
                        }


                        MapScreen(
                            settingsScreenVM = hiltViewModel(parent),
                            navigateToSettings = {
                                navController.navigate(DestinationsInfo.SettingsScreen.route)
                            }
                        )
                    }


                    composable(
                        route = DestinationsInfo.SettingsScreen.route,
                        deepLinks = DestinationsInfo.SettingsScreen.navDeepLinks,
                        enterTransition = {
                            slideIntoContainer(
                                animationSpec = tween(easing = EaseIn),
                                towards = AnimatedContentTransitionScope.SlideDirection.Start
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                animationSpec = tween(easing = EaseOut),
                                towards = AnimatedContentTransitionScope.SlideDirection.End
                            )
                        }
                    ) { backStackEntry ->

                        val parent = remember(backStackEntry){
                            navController.getBackStackEntry(DestinationsInfo.DestinationGraphMain.route)
                        }

                        SettingsScreen(
                            settingsScreenVM = hiltViewModel(parent),
                            onNavigationBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                }
            }
        }

    }


    override fun onDestroy() {
        super.onDestroy()
    }
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.SettingsScreenEnterTransition() : EnterTransition? {
    return slideInHorizontally { 0 }
}