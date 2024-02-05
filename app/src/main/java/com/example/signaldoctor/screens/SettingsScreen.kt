package com.example.signaldoctor.screens

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.viewModels.SettingsScreenVM
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.services.BackgroundMeasurementsService
import com.example.signaldoctor.services.DURATION_KEY
import com.example.signaldoctor.ui.theme.SignalDoctorTheme
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.MSRS_TO_TAKE_MAX
import com.example.signaldoctor.utils.MSRS_TO_TAKE_MIN
import com.example.signaldoctor.utils.OptionalSliderDefaults
import com.example.signaldoctor.utils.PERIODICITY_MAX
import com.example.signaldoctor.utils.PERIODICITY_MIN
import com.example.signaldoctor.utils.toEpochMillis
import com.example.signaldoctor.utils.toZoneDateTime
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.roundToInt


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsScreenVM: SettingsScreenVM = hiltViewModel(),
    onNavigationBack : () -> Unit = {}
){

    val app = LocalContext.current.applicationContext
    val hasDeviceMic = remember{ app.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) }


    val mainActivity = LocalContext.current as MainActivity



    val isUserLocationAvailable by settingsScreenVM.isUserLocationAvailable.collectAsStateWithLifecycle()

    val runBaseMeasurementPermission = rememberBaseMeasurementsPermissionState()

    val runNoiseMeasurementPermission = rememberNoiseMeasurementPermissionState()

    val sendNotificationsPermission = rememberSendNotificationsPermissionState()

    val recordPermission = rememberRecordPermissionState()

    val canRunBaseMeasurement by
        remember{ derivedStateOf { runBaseMeasurementPermission.allPermissionsGranted && isUserLocationAvailable } }


    val canRunNoiseMeasurement by remember{ derivedStateOf { runNoiseMeasurementPermission.allPermissionsGranted && isUserLocationAvailable }}

    val locationPermission = rememberLocationPermissionState{ isGranted ->
        if(isGranted) {
            consoleDebug("location permissions granted")
            settingsScreenVM.locationUpdatesOn()
        }
        else consoleDebug("locations permissions denied")
    }
    DisposableEffect(locationPermission.status.isGranted){
        if(locationPermission.status.isGranted)
            settingsScreenVM.locationUpdatesOn()
        onDispose {
            settingsScreenVM.locationUpdatesOff()
        }
    }

    val phoneSettings by settingsScreenVM.phoneSettings.collectAsStateWithLifecycle()

    val noiseSettings by settingsScreenVM.noiseSettings.collectAsStateWithLifecycle()

    val wifiSettings by settingsScreenVM.wifiSettings.collectAsStateWithLifecycle()

    val currentSettingsList by settingsScreenVM.currentSettingsList.collectAsStateWithLifecycle()



    SignalDoctorTheme{

        val snackbareHostState = remember{SnackbarHostState()}
        val snackbarLauncher = rememberCoroutineScope()

        Scaffold(
            modifier = modifier ,
            topBar = {
                SettingsTopBar(
                    currentSettingsList= currentSettingsList,
                    onTabChange = { msrType : Measure ->
                        settingsScreenVM.changeCurrentSettingsList(msrType)
                    },
                    onNavigationBack = onNavigationBack
                )
            },
            snackbarHost = {
                SnackbarHost(snackbareHostState) {
                    Snackbar(snackbarData = it)
                }
            }
        ) { contentPadding ->

            val deviceHasNoMicSnackbarMessage = stringResource(id = R.string.device_with_no_microphone_snackbar_message)

            MeasurementSettingsList(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(15.dp),
                msrType = currentSettingsList,
                title = "${currentSettingsList.name} Settings".replaceFirstChar { it.uppercase() },
                measurementSettings = when(currentSettingsList){
                    Measure.wifi -> wifiSettings
                    Measure.sound -> noiseSettings
                    Measure.phone -> phoneSettings
                },
                divider = {
                    Divider(
                        modifier = Modifier
                            .padding(vertical = 10.dp),
                        thickness = Dp.Hairline
                    )
                },
                onUseMsrsToTakeChange = {
                    settingsScreenVM.updateMeasureSettings(currentSettingsList){
                        useMsrsToTake = it
                    }
                },
                onMsrsToTakeChange = {
                    settingsScreenVM.updateMeasureSettings(currentSettingsList){
                        msrsToTake = it
                    }
                },
                isBackgroundSwitchEnabled = if(currentSettingsList == Measure.sound) canRunNoiseMeasurement && hasDeviceMic
                        else canRunBaseMeasurement,
                onIsBackgroundOnChange = {
                            settingsScreenVM.updateMeasureSettings(currentSettingsList) {
                                isBackgroundMsrOn = it
                            }
                },
                onIsBackgroundOnChangeWhenDisabled = { _ ->
                    if (!hasDeviceMic) snackbarLauncher.launch{
                        snackbareHostState.showSnackbar(
                            message = deviceHasNoMicSnackbarMessage,
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                    }else{
                        val neededPermissions = runNoiseMeasurementPermission
                            .takeIf { currentSettingsList == Measure.sound }
                            ?: runBaseMeasurementPermission

                        neededPermissions.checkPermissions {
                            settingsScreenVM.checkLocationSettings(mainActivity)
                        }
                    }
                },
                onPeriodicityChange = {
                    settingsScreenVM.updateMeasureSettings(currentSettingsList){
                        periodicity = it
                    }
                },
                onFreshnessChange = {
                    settingsScreenVM.updateMeasureSettings(currentSettingsList){
                        consoleDebug("Updating freshness...")
                        consoleDebug("freshness was ${freshness.toZoneDateTime()}")
                        freshness = it
                        consoleDebug("the user selected freshness is ${it.toZoneDateTime()}")
                    }
                },
                onOldnessChange = {
                    settingsScreenVM.updateMeasureSettings(currentSettingsList){
                        oldness = it
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(name = "SettingsScreen Top Bar")
fun SettingsTopBar(
    modifier: Modifier = Modifier,
    currentSettingsList : Measure = Measure.sound,
    onTabChange : (measure : Measure) -> Unit = {},
    onNavigationBack: () -> Unit = {}
){
    Column(
        modifier = modifier
    ){
        TopAppBar(
            navigationIcon = {
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            IconButton(onClick = onNavigationBack) {
                                Icon(
                                    modifier = Modifier.size(25.dp),
                                    painter = painterResource(id = R.drawable.back_button),
                                    contentDescription = stringResource(R.string.settings_screen_navigate_back_button) )
                            }
                            Icon(
                                modifier = Modifier.size(35.dp),
                                tint = MaterialTheme.colorScheme.primary,
                                painter = painterResource(R.drawable.settings_icon),
                                contentDescription = stringResource(R.string.settings_screen_logo_label)
                            )
                        }
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_screen_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        )
        SettingsTabRow(
            currentSettingsList = currentSettingsList,
            onTabChange = onTabChange
        )
    }
}

@Composable
@Preview("Settings Tabs")
fun SettingsTabRow(
    modifier: Modifier = Modifier,
    currentSettingsList: Measure = Measure.sound,
    onTabChange : (measureType : Measure) -> Unit = {}
){
    TabRow(
        modifier = modifier,
        selectedTabIndex = currentSettingsList.ordinal
    ) {

        for(measure in Measure.values()){
            SettingsTab(
                modifier = Modifier.padding(10.dp),
                title = measure.name,
                iconId = when(measure){
                    Measure.phone -> R.drawable.phone
                    Measure.sound -> R.drawable.ear
                    Measure.wifi -> R.drawable.wifi
                },
                selected = measure == currentSettingsList,
                onclick = { onTabChange(measure) }
            )
        }
    }
}

@Composable
@Preview(name = "Settings Tab")
fun SettingsTab(
    modifier: Modifier = Modifier,
    iconId: Int = R.drawable.ic_launcher_foreground ,
    title: String = "Placeholder",
    description: String = "this is a placeholder",
    onclick: () -> Unit = {},
    selected : Boolean = false,
    ){
    Tab(
        modifier  = modifier,
        selected = selected,
        onClick = onclick
    ){

        Row {
            Icon(
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp),
                painter = painterResource(id = iconId),
                contentDescription = description
            )
            Text(
                softWrap = false,
                minLines = 1,
                text = title
            )
        }
    }
}

@Composable
fun MeasurementSettingsList(
    modifier: Modifier = Modifier,
    msrType: Measure = Measure.phone,
    title : String = "Placeholder title",
    measurementSettings: MeasurementSettings = MeasurementSettings.getDefaultInstance(),
    titleFontSize : TextUnit = 6.em,
    optionDescriptionFontSize : TextUnit = 4.em,
    divider : @Composable ColumnScope.() -> Unit = {},
    onIsBackgroundOnChange : (Boolean) -> Unit,
    onIsBackgroundOnChangeWhenDisabled : (Boolean) -> Unit = {},
    isBackgroundSwitchEnabled : Boolean = true,
    onPeriodicityChange : (Int) -> Unit,
    onUseMsrsToTakeChange : (Boolean) -> Unit,
    onMsrsToTakeChange : (Int) -> Unit,
    onFreshnessChange: (Long) -> Unit,
    onOldnessChange: (Long) -> Unit,

    ){


    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
    ){
            Text(
                modifier = Modifier.padding( bottom = 40.dp),
                text = title,
                fontSize = titleFontSize,
            )

        OptionalSliderSetting(
            checkMode = OptionalSliderDefaults.CheckMethod.SWITCH,
            value = measurementSettings.periodicity,
            checked = measurementSettings.isBackgroundMsrOn && isBackgroundSwitchEnabled,
            onChecked = if(isBackgroundSwitchEnabled) onIsBackgroundOnChange else onIsBackgroundOnChangeWhenDisabled,
            description = stringResource(R.string.background_measurement_setting_description),
            valueText ="every "+ when (measurementSettings.periodicity) {
                1 -> " 1 minute"
                else -> "${measurementSettings.periodicity} minutes "
            },
            onValueChange = onPeriodicityChange,
            valueRange = PERIODICITY_MIN.toFloat().rangeTo(PERIODICITY_MAX.toFloat()),
            steps = PERIODICITY_MAX / PERIODICITY_MIN -2
        )

        divider()

        OptionalSliderSetting(
            checked = measurementSettings.useMsrsToTake,
            onChecked = onUseMsrsToTakeChange,
            value = measurementSettings.msrsToTake,
            valueRange = MSRS_TO_TAKE_MIN.toFloat().rangeTo(MSRS_TO_TAKE_MAX.toFloat()),
            steps = MSRS_TO_TAKE_MAX / MSRS_TO_TAKE_MIN -2,
            onValueChange = onMsrsToTakeChange,
            description = stringResource(R.string.msrs_to_take_setting_description),
            valueText = "over the last ${measurementSettings.msrsToTake} measurements"
        )

        divider()
        key(msrType.ordinal){
            RangeDatePickerSetting(
                description = stringResource(R.string.msrs_average_time_range_setting_description),
                freshness = measurementSettings.freshness,
                oldness = measurementSettings.oldness,
                onFreshnessChange = onFreshnessChange,
                onOldnessChange = onOldnessChange
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(name = "Slider Setting")
fun SliderSetting(
    modifier : Modifier = Modifier,
    description: String = "This is a slider option setting",
    valueText : String = "current selected value",
    value : Float = 0f,
    onValueChange : (Int) -> Unit = {},
    optionDescriptionFontSize : TextUnit = 4.em,
){

        Text(
            fontSize = optionDescriptionFontSize,
            text = description
        )
        Divider(
            color = Color.Transparent,
            thickness = 12.dp
        )
        Text(
            text = valueText,
            color = MaterialTheme.colorScheme.onBackground
        )
        Slider(
            value = value,
            steps = 15,
            valueRange = 1f.rangeTo(15f),
            onValueChange = { onValueChange(it.roundToInt()) },
            thumb = {
                SliderDefaults.Thumb(interactionSource = remember { MutableInteractionSource() })

            },
        )
}

@Composable
@Preview(name = " Optional Number input field setting")
fun OptionalSliderSetting(
    modifier : Modifier = Modifier,
    checkMode : OptionalSliderDefaults.CheckMethod = OptionalSliderDefaults.CheckMethod.CHECKBOX,
    checked : Boolean = true,
    enabled : Boolean = true,
    onChecked : (Boolean) -> Unit = {},
    onCheckedWhenDisabled : (Boolean) -> Unit = {},
    description: String = "This is a slider option setting",
    valueText : String = "current selected value",
    value : Number = 0f,
    steps : Int = 5,
    valueRange : ClosedFloatingPointRange<Float> = 1f.rangeTo(30f),
    onValueChange : (Int) -> Unit = {},
    optionDescriptionFontSize : TextUnit = 4.em,
){

        Text(
            fontSize = optionDescriptionFontSize,
            text = description
        )

        when(checkMode){
            OptionalSliderDefaults.CheckMethod.CHECKBOX -> Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if(enabled) onChecked else onCheckedWhenDisabled
            )
            OptionalSliderDefaults.CheckMethod.SWITCH -> Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if (enabled) onChecked else onCheckedWhenDisabled
            )
        }
        AnimatedVisibility(visible = checked) {
            Text(
                text = valueText,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        AnimatedVisibility(visible = checked) {

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = valueRange,
                steps = steps
            )
        }
}

@Composable
fun RangeDatePickerSetting(
    modifier: Modifier = Modifier,
    freshness : Long = ZonedDateTime.now().toEpochMillis(),
    oldness : Long = ZonedDateTime.now().minusDays(1).toEpochMillis(),
    optionDescriptionFontSize : TextUnit = 4.em,
    description: String = "This is a range date picker setting",
    headlineFontSize : TextUnit = 4.em,
    onFreshnessChange : (Long) -> Unit,
    onOldnessChange : (Long) -> Unit,
    dateValidator: (Long) -> Boolean = {true},

){

    Column(
        modifier = modifier
    ){
        Text(
            fontSize = optionDescriptionFontSize,
            text = description
        )
        Divider(
            color = Color.Transparent,
            thickness = 12.dp
        )
        Row{

            PickerToggableDialog(
                modifier = modifier.weight(5 / 10f),
                date = freshness,
                title = stringResource(R.string.freshness_date_picker_title),
                onDateChange = onFreshnessChange,
                dateValidator = {
                    it >= oldness && dateValidator(it)
                }
            )

            PickerToggableDialog(
                modifier= modifier.weight(5 / 10f),
                date = oldness,
                title = stringResource(R.string.oldness_date_picker_dialog),
                onDateChange = onOldnessChange,
                dateValidator = {
                    it <= freshness && dateValidator(it)
                }
            )

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerToggableDialog(
    modifier: Modifier = Modifier,
    title: String = "date picker",
    dateValidator : (Long) -> Boolean = {true},
    date : Long? = Instant.now().toEpochMilli(),
    onDateChange : (Long) -> Unit = {},
    showModeToggle : Boolean = false
){


    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date,
        initialDisplayMode = DisplayMode.Input,
    )


    Box(
        modifier = modifier
    ){
        if (pickerState.displayMode == DisplayMode.Picker) {
            DatePickerDialog(
                onDismissRequest = {
                    pickerState.displayMode = DisplayMode.Input
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pickerState.displayMode = DisplayMode.Input
                            onDateChange(pickerState.selectedDateMillis!!)
                        }
                    ) {
                        Text(text = "Close Dialog")
                    }
                }) {
                DatePicker(
                    title = null,
                    state = pickerState,
                    headline =  {
                        Text(
                            modifier= Modifier.padding(start = 20.dp),
                            text = title
                        )
                    },
                    dateValidator = dateValidator
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.Top
            ){
                DatePicker(
                    modifier = Modifier,
                    state = pickerState,
                    title = null,
                    headline = { Text(text = title) },
                    showModeToggle = false,
                    dateValidator = { newDate: Long ->
                        consoleDebug("inside date validator")
                        onDateChange(newDate)
                        true
                    }
                )
                if(showModeToggle){
                    IconButton(
                        modifier = Modifier,
                        onClick = {
                            pickerState.displayMode = DisplayMode.Picker
                        }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "open date picker"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RangeDatePickerSetting2(
    modifier: Modifier = Modifier,
    description : String = "This is a Range Date Picker",
    startDateTitle : String = "Start Date",
    startDate : Long = Instant.now().toEpochMilli(),
    onStartDateChange : (Long) -> Unit,
    endDateTitle : String = "End Date",
    endDate : Long = Instant.now().minus(Duration.ofDays(7)).toEpochMilli(),
    onEndDateChange : (Long) -> Unit
){

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ){
        OutlinedDateBox(
            modifier = modifier.weight(4/10f),
            labelText = startDateTitle,
            date = startDate,
            onDateChange = onStartDateChange,
        )

        Spacer(modifier = modifier.weight(1/10f))

        OutlinedDateBox(
            modifier = modifier.weight(4/10f),
            labelText = endDateTitle,
            date = endDate,
            onDateChange = onEndDateChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlinedDateBox(
    modifier: Modifier = Modifier,
    labelText : String = "date",
    date : Long? = Instant.now().toEpochMilli(),
    trailingIcon : @Composable ( (()->Unit)-> Unit) = { onClick ->
                  IconButton(onClick = onClick) {
                      Icon(
                          imageVector = Icons.Default.DateRange,
                          contentDescription = "d"
                      )
                  }
    },
    dateValidator: (Long) -> Boolean = {true},
    onDateChange: (Long) -> Unit
){

    var toggleDatePicker by remember{ mutableStateOf(false) }

    OutlinedTextField(
        modifier = modifier,
        value = "${date?.toLocalDate()}",
        readOnly = true,
        onValueChange = {},
        label = {
            Text(text = labelText)
        },
        trailingIcon = { trailingIcon{
            toggleDatePicker = true
        }}
    )
    if(toggleDatePicker){

        val datePickerState = rememberDatePickerState(
            initialDisplayMode = DisplayMode.Picker,
            initialSelectedDateMillis = date
        )

        DatePickerDialog(
            onDismissRequest = {
                toggleDatePicker = false
                },
            confirmButton = {
                TextButton(onClick = {
                    toggleDatePicker = false
                }) {
                    Text(text = "Confirm")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                dateValidator = {
                    if(dateValidator(it)){
                        onDateChange(it)
                        true
                    } else false
                }
            )
        }
    }
}

@Composable
fun startBackgroundMeasurement(msrType : Measure, minutes : Long = 15L) : ComponentName?{

    val intent = Intent(LocalContext.current.applicationContext, BackgroundMeasurementsService::class.java).apply {
        action = BackgroundMeasurementsService.START_BACKGROUND_ACTION
        putExtra(msrType.name, msrType.ordinal)
        putExtra(DURATION_KEY, minutes)
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        LocalContext.current.applicationContext.startForegroundService(intent)
    else
        LocalContext.current.applicationContext.startService(intent)

}

@Composable
fun stopBackgroundMeasurement(msrType: Measure) : Boolean {

    val intent = Intent(LocalContext.current.applicationContext, BackgroundMeasurementsService::class.java).apply {
        action = BackgroundMeasurementsService.STOP_BACKGROUND_ACTION
        putExtra(msrType.name, msrType.ordinal)
    }

    return LocalContext.current.applicationContext.stopService(intent)

}

@Composable
fun <T> rememberedDerivedStateOf(block : () -> T) = remember{ derivedStateOf(block)}


fun Long.toLocalDate(): LocalDate = toZoneDateTime().toLocalDate()