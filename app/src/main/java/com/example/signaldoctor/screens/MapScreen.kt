package com.example.signaldoctor.screens

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.viewModels.MyViewModel
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.mapUtils.GpsMarker
import com.example.signaldoctor.mapUtils.SquaredZonesOverlay
import com.example.signaldoctor.mapUtils.SquaredZonesOverlay2
import com.example.signaldoctor.ui.theme.SignalDoctorTheme
import com.example.signaldoctor.utils.Loggers.consoledebug
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MyViewModel,
    ){
    SignalDoctorTheme {


        val locationPermission = rememberPermissionState(permission = android.Manifest.permission.ACCESS_FINE_LOCATION){ isGranted ->
            when(isGranted) {
                true -> {
                    consoledebug("Permission GRANTED")
                    viewModel.locationUpdatesOn()
                }
                false -> consoledebug("Permission DENIED")
            }
        }
        val recordPermission = rememberPermissionState(permission = android.Manifest.permission.RECORD_AUDIO){
                isGranted -> when(isGranted) {
            true -> consoledebug("Permission GRANTED")
            false -> consoledebug("Permission DENIED")
            }
        }
        val screenLocation by viewModel.mapScreenUiState.screenLocation.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val userLocation by viewModel.userLocation.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED )

        val searchBarText by viewModel.mapScreenUiState.searchBarText.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val centerOnScreenLocation by viewModel.mapScreenUiState.centerOnScreenLocation.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

        val lastNoiseMsr by viewModel.lastNoiseMsr.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val lastPhoneMsr by viewModel.lastPhoneMsr.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val lastWifiMsr by viewModel.lastWifiMsr.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

        val soundAvgs by viewModel.soundAvgs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val phoneAvgs by viewModel.phoneAvgs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val wifiAvgs by viewModel.wifiAvgs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

        val currentMsrType by viewModel.mapScreenUiState.currentMsrType.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
        val currentMeasuringState by viewModel.mapScreenUiState.measuringState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

        Scaffold(
            topBar = {
                SearchBar(
                    modifier = Modifier,
                    alignment= Alignment.Center,
                    text = searchBarText,
                    onQueryChange = {updatedText->
                                        viewModel.mapScreenUiState.updateSearchBarText(updatedText)
                                    },
                    onSearch = {query -> viewModel.setScreenLocationFromQueryString(query)}
                )
            },

            content = { paddingValues ->
                Map(
                    modifier = Modifier.padding(paddingValues),
                    map = viewModel.map,
                    soundAvgs = soundAvgs,
                    phoneAvgs = phoneAvgs,
                    wifiAvgs = wifiAvgs,
                    currentMsrMode = currentMsrType,
                    locationPermission = locationPermission,
                    userLocation = userLocation,
                    screenLocation= screenLocation,
                    centerOnUserLocation = centerOnScreenLocation,
                    disableCenterOnUserLocation = {viewModel.mapScreenUiState.disableCenterOnScreenLocation()},
                    updateSearchBarText = {viewModel.mapScreenUiState.updateSearchBarText(it) }
                )

            },

            floatingActionButton = {
                Row(
                    modifier= Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    verticalAlignment = Alignment.Bottom,
                ){

                    UserLocationButton(
                        modifier= Modifier.size(40.dp),
                        enabled =  userLocation!=null,
                        onClick = {
                            consoledebug("wtf")
                            if (!locationPermission.status.isGranted)
                                locationPermission.launchPermissionRequest()
                            else
                                if(userLocation!=null)
                                viewModel.setUserLocationAsScreenLocation()
                        }
                    )

                    MeasuringButton(
                        modifier = Modifier
                            .size(100.dp)
                            .offset(75.dp),
                        measuringState = currentMeasuringState,
                        onClick = {
                            when(currentMeasuringState){
                                MeasuringState.STOP -> {

                                    if(currentMsrType == Measure.sound && !recordPermission.status.isGranted)
                                        recordPermission.launchPermissionRequest()
                                    else {
                                        //viewModel.runNoiseMeasurementDebug()
                                        viewModel.runMeasurement(currentMsrType)
                                    }
                                }
                                MeasuringState.RUNNING -> {
                                    viewModel.cancelMeasurement(currentMsrType)
                                    //viewModel.changeMeasuringState(MeasuringState.STOP)
                                }
                                MeasuringState.BACKGROUND -> viewModel.changeMeasuringState(MeasuringState.STOP)
                            }
                        }
                    )
                }
            },

            bottomBar = {
                MsrsBar(
                  currentMsrType = currentMsrType,
                  changeCurrentMsrMode = { newMode ->
                      viewModel.cancelAllMeasurements()
                      viewModel.mapScreenUiState.setCurrentMsrMode(newMode)
                  },
                    phoneMsr = lastPhoneMsr,
                    noiseMsr = lastNoiseMsr,
                    wifiMsr = lastWifiMsr
                )
            }
        )
    }
}



@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Map(
    modifier: Modifier,
    map : MapView,
    currentMsrMode: Measure = Measure.phone,
    phoneAvgs : MsrsMap,
    soundAvgs : MsrsMap,
    wifiAvgs : MsrsMap,
    locationPermission : PermissionState,
    userLocation : Location?,
    screenLocation : Location,
    centerOnUserLocation : Boolean = false,
    disableCenterOnUserLocation : () -> Unit = {},
    updateSearchBarText : (String) -> Unit = {},
) {

    AndroidView(
        modifier = modifier,
        factory = {
            map.apply {
                setMultiTouchControls(true)
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
                    for (mode in Measure.values()) add(mode.ordinal, SquaredZonesOverlay2(MapTileProviderBasic(it, TileSourceFactory.MAPNIK), it, phoneAvgs))
                }
                controller.run {
                    setZoom(12.5)
                    setCenter(GeoPoint(screenLocation.latitude, screenLocation.longitude))
                }
            }
        },

        ) {map-> map.run {
                overlays.run {

                    if (locationPermission.status.isGranted && userLocation != null) {
                        consoledebug("add Marker")
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
                        if (zoomLevelDouble < 15.5)
                            zoomTo(16.8)

                        screenLocation.run{
                            animateTo(
                                GeoPoint(latitude, longitude),
                                16.6,
                                500
                            )
                            updateSearchBarText("${latitude}, ${longitude}")
                        }
                        disableCenterOnUserLocation()
                    }
                }
                invalidate()
            }
        consoledebug("CRASH TEST")
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
    text : String = "",
    onQueryChange: (String) -> Unit = {},
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
            modifier = modifier,
            query = text,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            active = false,
            onActiveChange = {},
            trailingIcon = {
                Icon(
                    modifier = Modifier
                        .padding(15.dp)
                        .clickable {
                        },
                    painter = painterResource(id = R.drawable.search_icon2),
                    contentDescription = "Search button"
                )
            })
        {

        }
    }
}

////////////////////////////////////////////
// Composables for Buttons
///////////////////////////////////////

@Composable
@Preview(name = "get user location button")
fun UserLocationButton(
    modifier: Modifier = Modifier,
    shape : Shape = CircleShape,
    onClick: () -> Unit = {},
    enabled : Boolean = false
    ){
    FloatingActionButton(
        modifier = Modifier,
        onClick = onClick,
        shape = CircleShape,
        containerColor = if(enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        contentColor = if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    ) {
        Icon(
            modifier = modifier,
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
    measuringState: MeasuringState = MeasuringState.STOP
){
    ExtendedFloatingActionButton(
        modifier = modifier,
        text = {},
        containerColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
               /*Crossfade(
                   targetState = measuringState,
                   label = "Measuring Button"
                   ) {measuringState ->
                   when (measuringState) {
                       MeasuringState.STOP -> {
                           Icon(
                               modifier = Modifier
                                   .padding(top = 20.dp, bottom = 20.dp, start = 6.dp, end = 6.dp)
                                   .offset(10.dp),
                               painter = painterResource(
                                   id = R.drawable.play_icon
                               ), contentDescription = "Measuring Button"
                           )
                       }
                       MeasuringState.BACKGROUND -> CircularProgressIndicator()
                       MeasuringState.RUNNING -> {
                           Icon(
                               modifier = Modifier
                                   .padding(4.dp)
                                   .offset(x = 1.dp),
                               painter = painterResource(
                                   id = R.drawable.stop_icon
                               ), contentDescription = "Measuring Button"
                           )
                       }
                   }
               }*/
               when (measuringState) {
            MeasuringState.STOP -> {
                Icon(
                    modifier = Modifier
                        .padding(top = 20.dp, bottom = 20.dp, start = 6.dp, end = 6.dp)
                        .offset(10.dp),
                    painter = painterResource(
                        id = R.drawable.play_icon
                    ), contentDescription = "Measuring Button"
                )
            }
                MeasuringState.BACKGROUND -> CircularProgressIndicator()
                MeasuringState.RUNNING -> {
                    Icon(
                        modifier = Modifier
                            .offset(2.dp)
                            .padding(4.dp),
                        painter = painterResource(
                            id = R.drawable.stop_icon
                        ), contentDescription = "Measuring Button"
                    )
                }
        }

               },
        onClick = onClick,
        shape = CircleShape
    )
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
        selectedTabIndex = currentMsrType.ordinal
    ) {
        for(TabMsrType in Measure.values()) {
            MsrTab(
                selected = if (TabMsrType == currentMsrType) true else false,
                msrName = TabMsrType.name,
                msr = when(TabMsrType) {
                                       Measure.sound -> noiseMsr
                                        Measure.phone -> phoneMsr
                                        Measure.wifi -> wifiMsr
                                       },
                onClick = { changeCurrentMsrMode(TabMsrType) }
            )
        }
    }
}

@Composable
@Preview("singe Measure Tab")
fun MsrTab(
    selected : Boolean = true,
    msrName : String = "Phone",
    msr : Int? = 0,
    onClick : () -> Unit = {}
){
    Tab(
        selected = selected ,
        unselectedContentColor = MaterialTheme.colorScheme.primary,
        text = {
                    Column {
                        Text(text = msrName)
                        Text(text = msr?.let { "${msr}dBm" } ?: "--")
                    }
               },

        icon = {
                Icon(
                    modifier= Modifier.height(54.dp),
                    painter =  when(msrName) {
                                            Measure.sound.name -> painterResource(id = R.drawable.ear)
                                            Measure.phone.name -> painterResource(id = R.drawable.phone)
                                            Measure.wifi.name -> painterResource(id = R.drawable.wifi)
                                            else -> painterResource(id = R.drawable.ic_launcher_foreground)}
                                            ,
                    contentDescription = "$msrName Tab"
                    )
               },

        onClick = onClick
    )
}

