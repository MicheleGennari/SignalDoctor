package com.example.signaldoctor.workers

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.PermissionsChecker
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.PhoneMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.getServiceType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.jvm.Throws

@HiltWorker
class NewPhoneMsrWorker @AssistedInject constructor(
    @Assisted private val ctx : Context,
    @Assisted private val params : WorkerParameters,
    appSettings: DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    flowLocationProvider : FlowLocationProvider,
    private val permissionsChecker: PermissionsChecker,
    private val appNotificationManager: AppNotificationManager
) : BaseMsrWorker<PhoneMeasurement>(ctx, params, appSettings, msrsRepo,appNotificationManager, flowLocationProvider){

    override val msrType = Measure.phone

    override val displayName = "Phone Measurement"

    override val foregroundServiceTypes = getServiceType(msrType)

    private lateinit var telephonyManager: TelephonyManager

    override fun arePermissionsGranted() = permissionsChecker.isBaseMeasurementPermitted()
            && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    override suspend fun prepare() {
        telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    @Throws(MeasurementException::class)
    override suspend fun getSingleMeasurement(): Int {
        return telephonyManager.signalStrength?.run {
            cellSignalStrengths.maxOf {
                    cellSignalStrength ->
                cellSignalStrength.dbm
            }
        } ?: throw MeasurementException("Can't read cells' Signal Strengths")
    }

    override fun buildMeasurementEntity(baseInfos: MeasurementBase) = PhoneMeasurement(
            baseInfo = baseInfos
    )


    override suspend fun localUpload(m: PhoneMeasurement) = msrsRepo.postPhoneMsr(m, NetworkMode.OFFLINE)


    override suspend fun onlineUpload(m: PhoneMeasurement) = msrsRepo.postPhoneMsr(m, NetworkMode.ONLINE)



}