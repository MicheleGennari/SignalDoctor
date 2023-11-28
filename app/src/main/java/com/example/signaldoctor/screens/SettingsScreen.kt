package com.example.signaldoctor.screens

import android.service.autofill.OnClickAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.signaldoctor.appComponents.viewModels.MyViewModel
import com.example.signaldoctor.ui.theme.SignalDoctorTheme
import com.example.signaldoctor.R
import com.example.signaldoctor.contracts.Measure

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MyViewModel
){
    SignalDoctorTheme{
        Scaffold(
            modifier = modifier ,
            topBar = {
                SettingsTopBar()
            },
            content = {
                SettingsList(
                    modifier = Modifier.padding(it)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(name = "SettingsScreen Top Bar")
fun SettingsTopBar(
    modifier: Modifier = Modifier
){
    Column(
        modifier = modifier
    ){
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        )
        SettingsTabRow()
    }
}

@Composable
@Preview("Settings Tabs")
fun SettingsTabRow(
    modifier: Modifier = Modifier,
    phoneOnClick : () -> Unit = {},
    noiseOnClick : () -> Unit = {},
    wifiOnClick : () -> Unit = {}
){
    TabRow(selectedTabIndex = Measure.phone.ordinal ) {

        for(type in Measure.values()){
            MsrTab(
                msrName = when(type){
                    Measure.phone -> Measure.phone.name
                    Measure.sound -> Measure.sound.name
                    Measure.wifi -> Measure.wifi.name
                }
            )
        }
    }
}

@Composable
@Preview(name = "Settings Tab")
fun SettingsTab(
    modifier: Modifier = Modifier,
    iconId: Int = R.drawable.ic_launcher_foreground ,
    text: String = "Placeholder",
    description: String = "this is a placeholder",
    onclick: () -> Unit = {},
    selected : Boolean = false,
    ){
    Tab(
        selected = selected,
        onClick = onclick
    ){

        Row {
            Icon(
                painter = painterResource(id = iconId),
                contentDescription = description
            )
            Text(
                text = text
            )
        }
    }
}

@Composable
@Preview("settings list")
fun SettingsList(
    modifier: Modifier = Modifier
){
    Column(
        modifier = modifier
    ) {
        Text(text = "ciao")
    }
}

