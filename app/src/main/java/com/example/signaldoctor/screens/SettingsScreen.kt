package com.example.signaldoctor.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signaldoctor.MeasurementSettings
import com.example.signaldoctor.R
import com.example.signaldoctor.appComponents.MainActivity
import com.example.signaldoctor.appComponents.viewModels.LOCATION_INTERVAL
import com.example.signaldoctor.appComponents.viewModels.LOCATION_PRIORITY
import com.example.signaldoctor.appComponents.viewModels.SettingsScreenVM
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.ui.theme.SignalDoctorTheme
import com.example.signaldoctor.utils.OptionalSliderDefaults
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationRequest
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenVM = hiltViewModel(),
    onNavigationBack : () -> Unit = {}
){

    val recordPermission = rememberPermissionState(permission = android.Manifest.permission.RECORD_AUDIO)

    val locationPermission = rememberPermissionState(permission = android.Manifest.permission.ACCESS_FINE_LOCATION)

    val phoneSettings by viewModel.phoneSettings.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    val noiseSettings by viewModel.noiseSettings.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    val wifiSettings by viewModel.wifiSettings.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    var currentSettingsList by remember{ mutableStateOf(Measure.sound) }

    SignalDoctorTheme{
        Scaffold(
            modifier = modifier ,
            topBar = {
                SettingsTopBar(
                    currentSettingsList= currentSettingsList,
                    onTabChange = { measureType : Measure ->
                        currentSettingsList = measureType
                    },
                    onNavigationBack = onNavigationBack
                )
            }
        ) { contentPadding ->

            MeasurementSettingsList(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(15.dp),
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
                    viewModel.updateMeasureSettings(currentSettingsList){
                        useMsrsToTake = it
                    }
                },
                onMsrsToTakeChange = {
                    viewModel.updateMeasureSettings(currentSettingsList){
                        msrsToTake = it
                    }
                },
                isBackgroundSwitchEnabled = (currentSettingsList != Measure.sound || recordPermission.status.isGranted)
                        && locationPermission.status.isGranted,
                onIsBackgroundOnChange = {
                    if((currentSettingsList != Measure.sound || recordPermission.status.isGranted) && locationPermission.status.isGranted){
                        viewModel.updateMeasureSettings(currentSettingsList) {
                            isBackgroundMsrOn = it
                        }
                    }
                },
                onPeriodicityChange = {
                    viewModel.updateMeasureSettings(currentSettingsList){
                        periodicity = it
                    }
                },
                onFreshnessChange = {
                    viewModel.updateMeasureSettings(currentSettingsList){
                        freshness = it
                    }
                },
                onOldnessChange = {
                    viewModel.updateMeasureSettings(currentSettingsList){
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
                                    contentDescription = "go to previous screen" )
                            }
                            Icon(
                                modifier = Modifier.size(35.dp),
                                tint = MaterialTheme.colorScheme.primary,
                                painter = painterResource(R.drawable.settings_icon),
                                contentDescription = "Settings Screen Logo"
                            )
                        }
            },
            title = {
                Text(
                    text = "Settings",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementSettingsList(
    modifier: Modifier = Modifier,
    title : String = "Placeholder title",
    measurementSettings: MeasurementSettings = MeasurementSettings.getDefaultInstance(),
    titleFontSize : TextUnit = 6.em,
    optionDescriptionFontSize : TextUnit = 4.em,
    divider : @Composable ColumnScope.() -> Unit = {},
    onIsBackgroundOnChange : (Boolean) -> Unit,
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
            checked = isBackgroundSwitchEnabled && measurementSettings.isBackgroundMsrOn,
            onChecked = onIsBackgroundOnChange,
            description = "Background measurement",
            valueText ="every "+if (measurementSettings.periodicity == 1)
                    " 1 minute"
                else
                    "${measurementSettings.periodicity} minutes"
            ,
            onValueChange = onPeriodicityChange,
        )

        divider()

        OptionalSliderSetting(
            checked = measurementSettings.useMsrsToTake,
            onChecked = onUseMsrsToTakeChange,
            value = measurementSettings.msrsToTake,
            steps = 5,
            valueRange = (1f.rangeTo(30f)),
            onValueChange = onMsrsToTakeChange,
            description = "Compute average upon the most recent measurements",
            valueText = "over the last ${measurementSettings.msrsToTake} measurements"
        )

        divider()

        RangeDatePickerSetting(
            description = "Select the time range for evaluating averages",
            freshness = measurementSettings.freshness,
            oldness =measurementSettings.oldness,
            onFreshnessChange = onFreshnessChange,
            onOldnessChange = onOldnessChange
        )


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
                onCheckedChange = onChecked
            )
            OptionalSliderDefaults.CheckMethod.SWITCH -> Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChecked
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
    freshness : Long = LocalDateTime.now().getLong(ChronoField.MILLI_OF_SECOND),
    oldness : Long = LocalDateTime.now().minusDays(1).getLong(ChronoField.MILLI_OF_SECOND),
    optionDescriptionFontSize : TextUnit = 4.em,
    description: String = "This is a range date picker setting",
    headlineFontSize : TextUnit = 4.em,
    onFreshnessChange : (Long) -> Unit,
    onOldnessChange : (Long) -> Unit,
    dateValidator: (Long) -> Boolean = {true}

){

    Text(
        fontSize = optionDescriptionFontSize,
        text = description
    )
    Divider(
        color = Color.Transparent,
        thickness = 12.dp
    )
    PickerToggableDialog(
        date = freshness,
        onDateChange = onFreshnessChange,
        dateValidator = dateValidator
    )
    PickerToggableDialog(
        date = oldness,
        onDateChange = onOldnessChange,
        dateValidator = dateValidator
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerToggableDialog(
    modifier: Modifier = Modifier,
    dateValidator : (Long) -> Boolean = {true},
    date : Long? = Instant.now().toEpochMilli(),
    onDateChange : (Long) -> Unit = {}
){
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date,
        initialDisplayMode = DisplayMode.Input
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
                    state = pickerState,
                    dateValidator = dateValidator
                )
            }
        } else {
            DatePicker(
                modifier = Modifier.fillMaxWidth(9 / 10f),
                state = pickerState,
                title = null,
                headline = null,
                dateValidator = { newDate : Long ->
                    onDateChange(newDate)
                    true
                }
            )
        }
    }
}



fun <T>msrTypeWhen(
    currentMsrType : Measure,
    phone : T,
    sound : T,
    wifi : T
) : T {
   return when(currentMsrType){
        Measure.sound -> phone
        Measure.wifi -> sound
        Measure.phone -> wifi
    }
}

fun msrTypeWHen(
    currentMsrType: Measure,
    phone : () -> Unit,
    sound : () -> Unit,
    wifi : () -> Unit
){
    when(currentMsrType){
        Measure.phone -> phone()
        Measure.wifi -> wifi()
        Measure.sound -> sound()
    }
}