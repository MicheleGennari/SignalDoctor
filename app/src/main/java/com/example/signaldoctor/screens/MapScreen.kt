package com.example.signaldoctor.screens

import android.content.Context
import android.content.res.Resources.NotFoundException
import android.location.Location
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.viewModels.MyViewModel
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.mapUtils.GpsMarker
import com.example.signaldoctor.mapUtils.QueryAddressTranslator
import com.example.signaldoctor.mapUtils.SquaredZonesOverlay2
import com.example.signaldoctor.mapUtils.rememberMap
import com.example.signaldoctor.searchBarHint.ISearchBarHint
import com.example.signaldoctor.ui.theme.SignalDoctorTheme
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.CustomZoomButtonsDisplay
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    mapScreenVM: MyViewModel = hiltViewModel(),
    navigateToSettings : () -> Unit = {}
    ){
    SignalDoctorTheme {

        
        val isInternetAvailable by mapScreenVM.isNetworkAvailable.collectAsStateWithLifecycle()

        val runNoiseMeasurementsPermission = rememberNoiseMeasurementPermissionState()

        val runBaseMeasurementPermission = rememberBaseMeasurementsPermissionState()

        val sendNotificationsPermission = rememberSendNotificationsPermissionState { isGranted ->
            when(isGranted) {
                true -> consoledebug(" Notifications Permission GRANTED")
                false -> consoledebug(" Notifications Permission DENIED")
            }
        }

        val locationPermission = rememberLocationPermissionState { isGranted ->
            when(isGranted) {
                true -> {
                    consoledebug("Location Permission GRANTED")
                    mapScreenVM.locationUpdatesOn()
                }
                false -> consoledebug("Location Permission DENIED")
            }
        }

        val recordPermission = rememberRecordPermissionState { isGranted ->
            when(isGranted) {
                true -> consoledebug(" Recording Permission GRANTED")
                false -> consoledebug(" Recording Permission DENIED")
            }
        }

        DisposableEffect(locationPermission.status.isGranted){
            if(locationPermission.status.isGranted)
                mapScreenVM.locationUpdatesOn()
            onDispose {
                if(!mapScreenVM.areLocationUpdatesOn()){
                    mapScreenVM.locationUpdatesOff()
                }
            }
        }

        val currentNetworkMode by mapScreenVM.networkMode.collectAsStateWithLifecycle()
        val currentMsrType by mapScreenVM.mapScreenUiState.currentMsrType.collectAsStateWithLifecycle()


        val screenLocation by mapScreenVM.mapScreenUiState.screenLocation.collectAsStateWithLifecycle()
        val userLocation by mapScreenVM.userLocation.collectAsStateWithLifecycle()


        val arePhoneMsrsDated by mapScreenVM.arePhoneMsrsDated.collectAsStateWithLifecycle()

        val areNoiseMsrsDated by mapScreenVM.areNoiseMsrsDated.collectAsStateWithLifecycle()

        val areWifiMsrsDated by mapScreenVM.areWifiMsrsDated.collectAsStateWithLifecycle()


        val searchBarQuery by mapScreenVM.mapScreenUiState.searchBarQuery.collectAsStateWithLifecycle(context = Dispatchers.Default)
        val isSearchBarLoading by mapScreenVM.mapScreenUiState.isSearchBarLoading.collectAsStateWithLifecycle(context = Dispatchers.Default)

        val localSearchHints by mapScreenVM.localSearchBarHints.collectAsStateWithLifecycle()
        val searchHints by mapScreenVM.searchBarHints.collectAsStateWithLifecycle()
        val showHints by mapScreenVM.mapScreenUiState.searchbarShowHints.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val centerOnScreenLocation by mapScreenVM.mapScreenUiState.centerOnScreenLocation.collectAsStateWithLifecycle()

        val measurementProgress by  animateFloatAsState(targetValue = mapScreenVM.measurementProgress.collectAsStateWithLifecycle().value,
            label = "Measurement Progress Bar"
        )
        val lastNoiseMsr by mapScreenVM.lastNoiseMsr.collectAsStateWithLifecycle()
        val lastPhoneMsr by mapScreenVM.lastPhoneMsr.collectAsStateWithLifecycle()
        val lastWifiMsr by mapScreenVM.lastWifiMsr.collectAsStateWithLifecycle()

        val soundAvgs by mapScreenVM.soundAvgs.collectAsStateWithLifecycle()
        val phoneAvgs by mapScreenVM.phoneAvgs.collectAsStateWithLifecycle()
        val wifiAvgs by mapScreenVM.wifiAvgs.collectAsStateWithLifecycle()

        val currentMeasuringState by mapScreenVM.mapScreenUiState.measuringState.collectAsStateWithLifecycle()

        val snackbarHostState = remember{SnackbarHostState()}
        val snackbarLauncher = rememberCoroutineScope()


        Scaffold(
            topBar = {

                Column(
                    modifier = Modifier.padding(7.dp)
                ){

                    SearchBar(
                        modifier = Modifier
                            .zIndex(1f),
                        alignment = Alignment.Center,
                        text = searchBarQuery,
                        showHints = showHints,
                        onActiveChange = {
                                         mapScreenVM.mapScreenUiState.setShowHints(it)
                        },
                        onClickHint = { hint ->
                            mapScreenVM.mapScreenUiState.setScreenLocation(hint.latitude, hint.longitude)
                            mapScreenVM.mapScreenUiState.updateSearchBarQuery(hint.locationName)
                            mapScreenVM.mapScreenUiState.setShowHints(false)
                        },
                        localHints = localSearchHints,
                        onClickLocalHint = { hint ->
                            mapScreenVM.addLocalHint(hint)
                            mapScreenVM.mapScreenUiState.setScreenLocation(hint.latitude, hint.longitude)
                            mapScreenVM.mapScreenUiState.updateSearchBarQuery(hint.locationName)
                            mapScreenVM.mapScreenUiState.setShowHints(false)
                        },
                        hints = searchHints,
                        onQueryChange = { updatedQuery ->
                            mapScreenVM.mapScreenUiState.updateSearchBarQuery(updatedQuery)
                        },
                        onQueryCancel = {mapScreenVM.mapScreenUiState.updateSearchBarQuery("")},
                        onSearch = { query ->
                            QueryAddressTranslator.getCoordFromQuery(query)?.run {
                                    mapScreenVM.mapScreenUiState.setScreenLocation(
                                        latitude,
                                        longitude
                                    )
                                } ?: searchHints.firstOrNull()?.run{
                                    mapScreenVM.mapScreenUiState.setScreenLocation(
                                        latitude,
                                        longitude
                                    )
                            } ?: snackbarLauncher.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Can't find such location"
                                )
                            }
                            mapScreenVM.mapScreenUiState.setShowHints(false)
                        },
                        isSearching = isSearchBarLoading
                    )

                }
            },

            content = { paddingValues ->
                Map(
                    modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
                    soundAvgs = soundAvgs,
                    phoneAvgs = phoneAvgs,
                    wifiAvgs = wifiAvgs,
                    currentMsrMode = currentMsrType,
                    locationPermission = locationPermission,
                    userLocation = userLocation,
                    screenLocation= screenLocation,
                    centerOnUserLocation = centerOnScreenLocation,
                    onEndCenterOnUserLocation = {mapScreenVM.mapScreenUiState.setCenterOnScreenLocation(false)},
                )

                Column(
                  modifier = modifier
                      .fillMaxHeight()

                      .padding(paddingValues)
                      .padding(horizontal = 10.dp, vertical = 10.dp),
                  verticalArrangement = Arrangement.SpaceBetween
                ){
                    Row(
                        modifier = Modifier.weight(1/10f)
                    ){
                        NetworkModeToggleButton(
                            checked = currentNetworkMode == NetworkMode.ONLINE,
                            onCheckedChange = {
                                if (!(
                                            currentNetworkMode == NetworkMode.OFFLINE
                                                    && !isInternetAvailable
                                            )
                                ) {
                                    mapScreenVM.switchNetworkMode()
                                }
                            }
                        )
                    }
                    Spacer(modifier = modifier.weight(4/10f))
                    Row(
                        modifier = Modifier
                            .weight(2 / 10f)
                            .fillMaxWidth()
                            ,
                        // .padding(horizontal = 30.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val mainActivity = LocalContext.current as MainActivity

                        UserLocationButton(
                            modifier = Modifier.size(60.dp),
                            enabled = userLocation != null,
                            onClick = {
                                    mapScreenVM.mapScreenUiState.setScreenLocation(
                                        userLocation!!.latitude,
                                        userLocation!!.longitude
                                    )
                                    mapScreenVM.getLocationNameFromUserLocation()
                            },
                            onClickWhenDisabled = {
                                    locationPermission.apply {
                                        if(!status.isGranted)
                                            launchPermissionRequest()

                                        else if(userLocation == null)
                                                mapScreenVM.checkLocationSettings(mainActivity)
                                    }
                            }
                        )

                        MeasuringButton(
                            modifier = Modifier,
                            measuringState = currentMeasuringState,
                            measurementProgress = measurementProgress,
                            onClick = {

                                when (currentMeasuringState) {
                                    MeasuringState.STOP -> {
                                            mapScreenVM.runMeasurement(currentMsrType)
                                    }

                                    MeasuringState.RUNNING -> {
                                        mapScreenVM.cancelMeasurement(currentMsrType)
                                    }

                                    MeasuringState.BACKGROUND -> {
                                        mapScreenVM.cancelBackgroundMeasurement(currentMsrType)
                                    }
                                }
                            },
                            onClickWhenDisabled = {

                                val neededPermissions = runNoiseMeasurementsPermission.takeIf { currentMsrType == Measure.sound } ?: runBaseMeasurementPermission

                                    neededPermissions.checkPermissions{
                                        if(userLocation == null)
                                            mapScreenVM.checkLocationSettings(mainActivity)
                                    }

                              /*
                                neededPermissions.apply {
                                    if(!allPermissionsGranted )
                                        launchMultiplePermissionRequest()
                                    else if(userLocation == null)
                                        viewModel.checkLocationSettings(mainActivity)
                                } */
                            },
                            enabled = userLocation != null &&
                                    if(currentMsrType == Measure.sound) recordPermission.status.isGranted else true,
                        )

                        SettingsButton(
                            onClick = navigateToSettings
                        )
                    }
                }

            },
            floatingActionButton = {


            },
            bottomBar = {

                Column(
                    modifier = modifier.zIndex(2f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ){
                    MsrsBar(
                        currentMsrType = currentMsrType,
                        changeCurrentMsrMode = { newMode ->
                            mapScreenVM.cancelAllMeasurements()
                            mapScreenVM.mapScreenUiState.setCurrentMsrMode(newMode)
                        },
                        phoneMsr = lastPhoneMsr,
                        noiseMsr = lastNoiseMsr,
                        wifiMsr = lastWifiMsr
                    )
                }

            },
            snackbarHost = {

                SnackbarHost(hostState = snackbarHostState) {
                    Snackbar(snackbarData = it)
                }
            }
        )
    }
}



@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Map(
    modifier: Modifier,
    map : MapView = rememberMap(),
    currentMsrMode: Measure = Measure.phone,
    phoneAvgs : MsrsMap,
    soundAvgs : MsrsMap,
    wifiAvgs : MsrsMap,
    locationPermission : PermissionState,
    userLocation : Location?,
    screenLocation : Location?,
    centerOnUserLocation : Boolean = false,
    onEndCenterOnUserLocation : () -> Unit = {},
) {

    val density = LocalDensity.current

    var savedCenterLocation by rememberSaveable(stateSaver = Saver(
        save = {geoPointToSave ->
            mapOf("latitude" to geoPointToSave.latitude, "longitude" to geoPointToSave.longitude)
        },
        restore = { restoredCoord ->
            GeoPoint(restoredCoord["latitude"] ?: 42.3, restoredCoord["longitude"] ?: 47.9)
        }
    )) {
        mutableStateOf(map.mapCenter)
    }

    var savedZoom by rememberSaveable {
        mutableDoubleStateOf(map.zoomLevelDouble)
    }

    //this saves the current map center and zoom level when composable is disposed. Don't worry about centering to user location:
    // this latter behaviour will be achieved by AndroidView()'s update function using parameters 'centerOnUserLocation' and 'userLocation
    LifecycleStartEffect{
        consoledebug("on start effect")
        onStopOrDispose {
            consoledebug("on stop effect")
            savedCenterLocation = map.mapCenter
            savedZoom = map.zoomLevelDouble
        }
    }

    AndroidView(

        modifier = modifier,
        factory = {ctx ->
            map.apply {
                map.setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                map.setScrollableAreaLimitLongitude(MapView.getTileSystem().minLongitude, MapView.getTileSystem().maxLongitude, 0)
                setMultiTouchControls(true)

                minZoomLevel = 3.0

                //set MapView's Zoom Buttons such that they are always visible in the bottom left corner, just
                // on top of the user location button
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                zoomController.display.apply {
                    setPositions(
                        false,
                        CustomZoomButtonsDisplay.HorizontalPosition.LEFT,
                        CustomZoomButtonsDisplay.VerticalPosition.BOTTOM
                    )
                    setAdditionalPixelMargins(0f,0f,0f, density.run { 80.dp.toPx() })
                    setMarginPadding(1/2f, 1/3f)
                }

                overlays.run {

                    /////////////////////////////////////
                    //THE ADD OF THE MARKER HAS TO BE THE FIRST, BECAUSE IT WILL BE REFERRED LATELY BY ITS
                    // INDEX/POSITION IN THE LIST,
                    //////////////////////////////////
                    if(locationPermission.status.isGranted && userLocation!=null){
                        add(GpsMarker(map).apply {
                            position = GeoPoint(
                                userLocation.latitude,
                                userLocation.longitude
                            )
                        })
                    }
                    add(ScaleBarOverlay(map).apply {
                        setAlignBottom(true)
                        drawLongitudeScale(true)
                        drawLatitudeScale(false)
                        with(density){
                            setScaleBarOffset(20.dp.toPx().toInt(), 220.dp.toPx().toInt())
                        }
                        setBackgroundPaint(android.graphics.Paint().apply {
                            color = Color.Gray.hashCode()
                            alpha = 100
                        })
                    })

                    for (mode in Measure.values()) add(mode.ordinal, SquaredZonesOverlay2( ctx,
                        when(mode){
                            Measure.sound -> soundAvgs
                            Measure.phone -> phoneAvgs
                            Measure.wifi -> wifiAvgs
                        },
                        mode
                        ))
                }

                controller.run {
                    setZoom(savedZoom)
                    setCenter(savedCenterLocation)
                }

                //Older implementation, kept here for roll-back purposes
                /*controller.run {
                    screenLocation?.let{ screenLocation ->
                        setZoom(12.5)
                        setCenter(GeoPoint(screenLocation.latitude, screenLocation.longitude))
                    }
                }*/
            }
        },

        ) { map-> map.run {
                overlays.run {

                    if (locationPermission.status.isGranted && userLocation != null) {
                        //consoledebug("add Marker")
                        forEachIndexed { index, overlay ->
                            if (overlay is GpsMarker) removeAt(index)
                        }
                        add(GpsMarker(map).apply {
                            position.setCoords(
                                userLocation.latitude,
                                userLocation.longitude
                            )
                        })
                    } else {
                        consoledebug("Delete Marker")
                        forEachIndexed { index, overlay ->
                            if (overlay is GpsMarker) removeAt(index)
                        }
                    }
                    filterIsInstance<SquaredZonesOverlay2>().forEachIndexed { index, overlay ->
                        //set the HashMap used to draw signal level squares
                        when (index) {
                            Measure.phone.ordinal -> overlay.setAvgsMap(phoneAvgs)
                            Measure.sound.ordinal -> overlay.setAvgsMap(soundAvgs)
                            Measure.wifi.ordinal -> overlay.setAvgsMap(wifiAvgs)
                        }

                        //Take all measures overlays and disable them if they're not of the current selected measure type
                        overlay.isEnabled = if (index == currentMsrMode.ordinal) true else false
                    }
                }

                controller.run {
                    if (centerOnUserLocation) {
                        consoledebug("map will now be centered")
                        screenLocation?.run{
                            if (zoomLevelDouble < 15.5)
                                zoomTo(16.8)

                            animateTo(
                                GeoPoint(latitude, longitude),
                                16.6,
                                500
                            )
                        }
                        onEndCenterOnUserLocation()
                    }
                }
                invalidate()
            }
    }
}

/*@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(name="searchBarPreview")
fun SearchBar(
    modifier: Modifier = Modifier,
    locationText : TextFieldValue = TextFieldValue("54, 23"),
    onValueChange : (TextFieldValue) -> Unit = {},
    buttonEvent : () -> Unit = {}
){
        Row() {
            TextField(value = locationText, onValueChange = onValueChange )
            Button(
                shape = CircleShape,
                modifier = Modifier.wrapContentSize(),
                onClick = buttonEvent
            ) {
                Icon(
                    modifier= Modifier
                        .alignByBaseline()
                        .clip(CircleShape)
                        .size(20.dp),
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "set Location"
                )
            }
    }
}*/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview("Search Bar")
fun SearchBar(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    text : String = " ",
    showHints : Boolean = true,
    onActiveChange : (Boolean) -> Unit = {},
    onClickHint: (ISearchBarHint) -> Unit = { _ ->},
    onClickLocalHint : (ISearchBarHint) -> Unit = { _ ->},
    isSearching : Boolean = false,
    localHints : List<ISearchBarHint> = emptyList(),
    hints : List<ISearchBarHint> = emptyList(),
    onQueryChange : (String) -> Unit = {},
    onQueryCancel : () -> Unit = {},
    onSearch: (String) -> Unit = {}
){

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 7.dp),
        verticalAlignment= Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        DockedSearchBar(
            query = text,
            placeholder = {
                          Text(text = "Cerca qui", fontWeight = FontWeight.Light)
            },
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            active = showHints,
            onActiveChange = onActiveChange,
            leadingIcon = {
                AnimatedContent(showHints, label = "search bar leading icon") {showHints ->
                    IconButton(
                        enabled = showHints,
                        onClick = {
                        if(showHints) onActiveChange(false)
                    }) {
                        Icon(
                            modifier = modifier.padding(7.dp),
                            painter = painterResource(id = if(showHints) R.drawable.back_button else R.drawable.search_icon2),
                            contentDescription = "Exit search bar input mode"
                        )
                    }
                }
            },
            trailingIcon = {
                AnimatedVisibility(showHints) {
                        IconButton(onClick = {
                            onQueryCancel()
                        }) {
                            Icon(
                                modifier = Modifier
                                    .padding(13.dp),
                                painter = painterResource(id = R.drawable.x_icon),
                                contentDescription = "Search button"
                            )
                        }
                }
            }
        ) {
            if (!isSearching){

                SearchBarHints(
                    hints = localHints,
                    hintsColor = MaterialTheme.colorScheme.secondary,
                    onClickHint = { hint ->
                            onClickHint(hint)
                    },
                    onEmpty = null
                )

                SearchBarHints(
                    hints = hints,
                    hintsColor = MaterialTheme.colorScheme.primary,
                    onClickHint = { hint ->
                        onClickLocalHint(hint)
                    }
                ){
                    if (localHints.isEmpty()){
                        ListItem(headlineContent = {
                            Text(
                                text = "no results found",
                                color = MaterialTheme.colorScheme.error
                            )
                        })
                    }
                }

            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 25.dp),
                    contentAlignment = Alignment.Center
                ){ CircularProgressIndicator() }
            }
        }
    }
}

@Composable
fun SearchBarHints(
    modifier: Modifier = Modifier,
    hints: List<ISearchBarHint>,
    hintsColor : Color = MaterialTheme.colorScheme.onPrimary,
    onClickHint : (ISearchBarHint) -> Unit,
    onEmpty : ( @Composable () -> Unit)? = null
){

    Column(
        modifier = modifier
    ){
        for (hint in hints) {
            ListItem(
                modifier = Modifier.clickable {
                    onClickHint(hint)
                },
                headlineContent = {
                    Text(
                        color = hintsColor,
                        text = hint.locationName
                    )
                })
            Divider(
                thickness = Dp.Hairline
            )
        }
        if (hints.isEmpty() && onEmpty != null) onEmpty()
    }

}


////////////////////////////////////////////
// Composables for Buttons
///////////////////////////////////////

@Composable
@Preview(name = "Network Mode Toggle Button")
fun NetworkModeToggleButton(
    modifier: Modifier = Modifier,
    checked : Boolean = false,
    enabled: Boolean = true,
    onCheckedChange : (Boolean) -> Unit = {},

){
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = true,
            thumbContent = {
                Icon(
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    painter = painterResource(id = R.drawable.nework_mode),
                    contentDescription = "Network Mode Switch"
                )
            }
        )
}

@Composable
@Preview(name = "get user location button")
fun UserLocationButton(
    modifier: Modifier = Modifier,
    shape : Shape = CircleShape,
    onClick: () -> Unit = {},
    onClickWhenDisabled: () -> Unit = {},
    enabled : Boolean = false
    ){
    FloatingActionButton(
        modifier = modifier,
        onClick = if(enabled) onClick else onClickWhenDisabled,
        shape = shape,
        containerColor = if(enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        contentColor = if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    ) {
        Icon(
            modifier = modifier.padding(10.dp),
            painter = painterResource(id = R.drawable.location_button),
            contentDescription = "get location button",
        )
    }
}

@Composable
@Preview(name = "start measuring button ")
fun MeasuringButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onClickWhenDisabled : () -> Unit = {},
    enabled: Boolean = true,
    fabInteractions : MutableInteractionSource = remember{MutableInteractionSource()} ,
    measuringState: MeasuringState = MeasuringState.STOP,
    measurementProgress : Float = 5/10f,


){

    val isPressed by fabInteractions.collectIsPressedAsState()
    val pressedAnimation by animateFloatAsState(targetValue = if(isPressed) 0.9f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy),
        label = "pressedMeasuringButton Animation"
    )


    ExtendedFloatingActionButton(
        modifier = modifier
            .size(100.dp)
            .graphicsLayer {
                this.scaleX = pressedAnimation
                this.scaleY = pressedAnimation
            },
        interactionSource = fabInteractions,
        text = {},
        expanded = false,
        containerColor = if(enabled) MaterialTheme.colorScheme.onPrimary else  Color.DarkGray,
        onClick = if(enabled) onClick else onClickWhenDisabled,
        shape = CircleShape,
        icon = {

                Box(modifier = Modifier){
                    if(measuringState == MeasuringState.RUNNING)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            progress = measurementProgress
                        )
                    AnimatedContent(
                        targetState = measuringState,
                        contentAlignment = Alignment.Center,
                        label = "measuring state button animation",
                        transitionSpec = {
                                expandIn(
                                    expandFrom = Alignment.Center,
                                    animationSpec = tween(
                                        durationMillis= 100,
                                    )
                                ) {
                                    IntSize.Zero
                                }.togetherWith( shrinkOut(
                                    animationSpec= tween(
                                        durationMillis = 100,
                                    ),
                                    shrinkTowards = Alignment.Center
                                ) {
                                    IntSize.Zero
                                })
                        },
                    ) { measuringState ->
                        Icon(
                            modifier = Modifier.padding(if (measuringState == MeasuringState.STOP) 20.dp else 25.dp),
                            //.padding(top = 20.dp, bottom = 20.dp, start = 6.dp, end = 6.dp)
                            //.offset(10.dp),
                            painter = painterResource(
                                id = when (measuringState) {
                                    MeasuringState.STOP -> R.drawable.play_icon
                                    MeasuringState.RUNNING -> R.drawable.stop_icon
                                    MeasuringState.BACKGROUND -> R.drawable.stop_icon
                                }
                            ), contentDescription = "Measuring Button"
                        )
                    }
                }
           }
    )
}

@Composable
@Preview("Settings Button")
fun SettingsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
){
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(60.dp),

    ) {
        Icon(
            modifier = Modifier.padding(4.dp),
            painter = painterResource(id = R.drawable.settings_icon) ,
            contentDescription = "Settings Button"
        )
    }
}


//////////////////////////////////////////
// Composables for BottomTab Bar
////////////////////////////////////////


@Composable
@Preview("msrsTab")
fun MsrsBar(
    modifier: Modifier = Modifier,
    currentMsrType : Measure = Measure.phone,
    noiseMsr : Int? = 0,
    phoneMsr : Int? = 0,
    wifiMsr : Int? = 0,
    changeCurrentMsrMode : (Measure) -> Unit = {},
){
    TabRow(
        modifier = modifier.animateContentSize(),
        selectedTabIndex = currentMsrType.ordinal,
    ) {
        for(tabMsrType in Measure.values()) {
            MsrTab(
                modifier = Modifier.padding(top = 10.dp),
                selected = if (tabMsrType == currentMsrType) true else false,
                msrName = tabMsrType.name,
                lastMsr = when(tabMsrType) {
                                       Measure.sound -> noiseMsr
                                        Measure.phone -> phoneMsr
                                        Measure.wifi -> wifiMsr
                                       },
                onClick = { changeCurrentMsrMode(tabMsrType) }
            )
        }
    }
}

@Composable
@Preview("singe Measure Tab")
fun MsrTab(
    modifier: Modifier = Modifier,
    selected : Boolean = true,
    msrName : String = "Phone",
    lastMsr : Int? = 0,
    onColorMsr : (Int) -> Color = {
                 when{
                     it<=-60 -> Color.Red
                     it>-60 && it<=-40 -> Color.Yellow
                     else -> Color.Green
                 }
    },
    onClick : () -> Unit = {}
){

    val tabMsrColor by animateColorAsState(
        targetValue = lastMsr?.let { onColorMsr(lastMsr) } ?: LocalContentColor.current,
        label = "msr color scale animation"
    )


    Tab(
        modifier = modifier,
        selected = selected ,
        text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = msrName)
                        AnimatedVisibility(visible = lastMsr != null) {
                        Text(text = "$lastMsr dBm")
                        //Text(text = msr?.let { "${msr}dBm" } ?: "--")
                            }
                        }
               },

        icon = {

                Icon(
                    modifier= Modifier.height(54.dp),
                    painter =  when(msrName) {
                                            Measure.sound.name -> painterResource(id = R.drawable.ear)
                                            Measure.phone.name -> painterResource(id = R.drawable.phone)
                                            Measure.wifi.name -> painterResource(id = R.drawable.wifi)
                                            else -> painterResource(id = R.drawable.ic_launcher_foreground)
                    },
                    contentDescription = "$msrName Tab"
                    )
               },

        onClick = onClick
    )
}

@Composable
fun StatusSnackBarsLauncher(
    snackBarHostState : SnackbarHostState,
    vararg snackBars : SnackbarVisuals
){
    LaunchedEffect(snackBars){
        for (snackBar in snackBars){
            snackBarHostState.showSnackbar(snackBar)
        }
    }
}

@Composable
fun LaunchToast(
    resId : Int? = null,
    message : String,
    duration : Int = Toast.LENGTH_SHORT
){

    if(resId != null)
        Toast.makeText(LocalContext.current, resId, duration).show()
    else
        Toast.makeText(LocalContext.current, message, duration).show()
}

fun Context.launchToast(resId: Int? = null, message: String, duration: Int = Toast.LENGTH_SHORT){
    if(resId != null) try{
        Toast.makeText(this, resId, duration).show()
    } catch (e : NotFoundException){
        e.printStackTrace()
    }
    else
        Toast.makeText(this, message, duration).show()

}

