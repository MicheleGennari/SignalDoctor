package com.example.signaldoctor.screens

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaldoctor.R
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.contracts.MeasuringState
import com.example.signaldoctor.contracts.MsrsMap
import com.example.signaldoctor.mapUtils.GpsMarker
import com.example.signaldoctor.mapUtils.SquaredZonesOverlay
import com.example.signaldoctor.onlineDatabase.consoledebug
import com.example.signaldoctor.ui.theme.SignalDoctorTheme
import com.example.signaldoctor.viewModels.MyViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MyViewModel,
    ){
    SignalDoctorTheme {


        val networkMode = viewModel.networkMode.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)

        val locationPermission = rememberPermissionState(permission = android.Manifest.permission.ACCESS_FINE_LOCATION){
            isGranted -> when(isGranted) {
                true -> consoledebug("Permission GRANTED")
            false -> consoledebug("Permission DENIED")
            }
        }
        val recordPermission = rememberPermissionState(permission = android.Manifest.permission.RECORD_AUDIO){
                isGranted -> when(isGranted) {
            true -> consoledebug("Permission GRANTED")
            false -> consoledebug("Permission DENIED")
            }
        }
        val screenLocation by viewModel.mapScreenUiState.screenLocation.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)

        val userLocation by viewModel.userLocation.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED )
        val searchBarText by viewModel.mapScreenUiState.searchBarText.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
        val centerOnScreenLocation by viewModel.mapScreenUiState.centerOnScreenLocation.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

        val soundAvgs by viewModel.soundAvgs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
        val phoneAvgs by viewModel.phoneAvgs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
        val wifiAvgs by viewModel.wifiAvgs.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)

        val currentMsrMode by viewModel.mapScreenUiState.currentMsrMode.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
        val currentMeasuringState by viewModel.mapScreenUiState.measuringState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)

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
                    currentMsrMode = currentMsrMode,
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
                    modifier= Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ){

                    UserLocationButton(
                        modifier= Modifier,
                        onClick = {
                            if (!locationPermission.status.isGranted)
                                locationPermission.launchPermissionRequest()
                            else
                                viewModel.setUserLocationAsScreenLocation()
                        }
                    )

                    MeasuringButton(
                        modifier = Modifier
                            .size(100.dp)
                            .offset(80.dp),
                        measuringState = currentMeasuringState,
                        onClick = {
                            when(currentMeasuringState){
                                MeasuringState.STOP -> {

                                    when(currentMsrMode){
                                        Measure.wifi -> {
                                            viewModel.changeMeasuringState(MeasuringState.RUNNING)
                                        }
                                        Measure.sound -> {
                                            if(recordPermission.status.isGranted) {
                                                viewModel.sendMeasures(currentMsrMode, 1L, MeasuringState.RUNNING)
                                                viewModel.changeMeasuringState(MeasuringState.RUNNING)
                                            }
                                            else
                                                recordPermission.launchPermissionRequest()
                                        }
                                        Measure.phone -> {
                                            viewModel.changeMeasuringState(MeasuringState.RUNNING)
                                        }
                                    }
                                }
                                MeasuringState.RUNNING -> {
                                    viewModel.stopMeasuring()
                                    viewModel.changeMeasuringState(MeasuringState.STOP)
                                }
                                MeasuringState.BACKGROUND -> viewModel.changeMeasuringState(MeasuringState.STOP)
                            }
                        }
                    )
                }
            },

            bottomBar = {
                MsrsBar(
                  currentMsrMode = currentMsrMode,
                  changeCurrentMsrMode = {newMode ->
                      viewModel.mapScreenUiState.setCurrentMsrMode(newMode)
                      viewModel.stopMeasuring()
                  }
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
    userLocation : android.location.Location?,
    screenLocation : Location,
    centerOnUserLocation : Boolean = false,
    disableCenterOnUserLocation : () -> Unit = {},
    updateSearchBarText : (String) -> Unit = {},
) {
    val composableScope = rememberCoroutineScope()

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
                    for (mode in Measure.values()) add(mode.ordinal, SquaredZonesOverlay(it))
                }
                controller.run {
                    setZoom(12.5)
                    setCenter(GeoPoint(screenLocation.latitude, screenLocation.longitude))
                }
            }
        },

        ) {map->

        composableScope.launch{
            map.run {
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
                    filterIsInstance<SquaredZonesOverlay>().forEachIndexed { index, overlay ->
                        //set the HashMap used to draw signal level squares
                        when (index) {
                            Measure.phone.ordinal -> overlay.setMsrsMap(phoneAvgs)
                            Measure.sound.ordinal -> overlay.setMsrsMap(soundAvgs)
                            Measure.wifi.ordinal -> overlay.setMsrsMap(wifiAvgs)
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
    onClick: () -> Unit = {}
    ){
    FloatingActionButton(
        modifier = modifier
            .size(80.dp)
            .padding(start = 30.dp, top = 10.dp),
        onClick = onClick,
        shape = CircleShape
    ) {
        Icon(
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
    currentMsrMode : Measure = Measure.phone,
    soundAvg : Int = 0,
    phoneAvg : Int = 0,
    wifiAvg : Int = 0,
    changeCurrentMsrMode : (Measure) -> Unit = {},
){
    TabRow(
        selectedTabIndex = currentMsrMode.ordinal
    ) {
        for(mode in Measure.values()) {
            MsrTab(
                selected = if (mode == currentMsrMode) true else false,
                msrName = mode.name,
                onClick = { changeCurrentMsrMode(mode) }
            )
        }
    }
}

@Composable
@Preview("singe Measure Tab")
fun MsrTab(
    selected : Boolean = true,
    msrName : String = "Phone",
    avg : Int = 0,
    onClick : () -> Unit = {}
){
    Tab(
        selected = selected ,
        unselectedContentColor = MaterialTheme.colorScheme.primary,
        text = {
                    Column {
                        Text(text = msrName)
                        //Text(text = avg.toString())
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
                    contentDescription = "${msrName} Tab"
                    )
               },

        onClick = onClick
    )
}

