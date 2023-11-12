package com.example.signaldoctor.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.example.signaldoctor.appComponents.CHANGE_LOCATION_SETTINGS
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status

abstract class ChangeLocationSettingsContract : ActivityResultContract<ResolvableApiException, Boolean>(){


}