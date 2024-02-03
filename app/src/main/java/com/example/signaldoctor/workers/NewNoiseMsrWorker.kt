package com.example.signaldoctor.workers

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.signaldoctor.AppSettings
import com.example.signaldoctor.NetworkMode
import com.example.signaldoctor.appComponents.AppNotificationManager
import com.example.signaldoctor.appComponents.FlowLocationProvider
import com.example.signaldoctor.appComponents.PermissionsChecker
import com.example.signaldoctor.contracts.Measure
import com.example.signaldoctor.repositories.MsrsRepo
import com.example.signaldoctor.room.MeasurementBase
import com.example.signaldoctor.room.SoundMeasurement
import com.example.signaldoctor.room.WiFIMeasurement
import com.example.signaldoctor.utils.Loggers
import com.example.signaldoctor.utils.Loggers.consoleDebug
import com.example.signaldoctor.utils.getServiceType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.regex.Pattern
import kotlin.jvm.Throws

@HiltWorker
class NewNoiseMsrWorker @AssistedInject constructor(
    @Assisted private val ctx : Context,
    @Assisted private val params : WorkerParameters,
    appSettings: DataStore<AppSettings>,
    private val msrsRepo: MsrsRepo,
    flowLocationProvider : FlowLocationProvider,
    private val permissionsChecker: PermissionsChecker,
    private val appNotificationManager: AppNotificationManager
) : BaseMsrWorker<SoundMeasurement>(ctx, params, appSettings, msrsRepo,appNotificationManager, flowLocationProvider) {

    override val displayName = "Noise Measurement"
    override val msrType = Measure.sound
    override val foregroundServiceTypes = getServiceType(msrType)
    companion object{
        const val REC_FILE_NAME = "temp_msr_recording.3gp"
    }

    private lateinit var micRecorder : MediaRecorder
    private var isRecordingOn = false
    private val filePath = applicationContext.filesDir.absolutePath + "/" + REC_FILE_NAME
    override fun arePermissionsGranted() = permissionsChecker.isNoiseMeasurementPermitted()

    @Throws(MeasurementException::class)
    override suspend fun prepare() {
        kotlin.runCatching{
            micRecorder =
                if (Build.VERSION.SDK_INT >= 31) MediaRecorder(applicationContext) else MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setAudioEncodingBitRate(256)
                    setAudioChannels(1)
                    setOutputFile(filePath)
                }

                micRecorder.prepare()
            }.onFailure {e ->
                if(e is CancellationException) throw  e
                else throw MeasurementException("Error initializing recorder")
            }
    }
    @Throws(MeasurementException::class)
    override suspend fun measure(): Int {

        isRecordingOn = true
        micRecorder.start()

        repeat(5){
            delay(INTERVAL_BETWEEN_SINGLE_MEASUREMENTS)
            updateProgress()
        }

        micRecorder.stop()
        isRecordingOn = false

        val ffmpegSession = FFmpegKit.execute(
            "-nostats -i $filePath -af ebur128=framelog=verbose -f null -"
        )

        if(!ReturnCode.isSuccess(ffmpegSession.returnCode)) throw MeasurementException("Error during noise measurement")

        val msrLog = ffmpegSession.allLogs[ffmpegSession.allLogs.size - 2].message
        val matcher = Pattern.compile("I:\\s+(-?\\d+(.\\d+)?)").matcher(msrLog)

        if(!matcher.find()) throw MeasurementException("Error during noise measurement")
        return matcher.group(1)?.toDoubleOrNull()?.toInt()?.also { msr ->
            consoleDebug("noise msr = $msr")
            msr.sendAsResultNotification()
            updateProgress()


        } ?: throw MeasurementException("Error during noise measurement")
    }

    override suspend fun getSingleMeasurement() = 0

    override suspend fun finish() {
        if(isRecordingOn) {
            consoleDebug("Stopping background measurement")
            micRecorder.stop()
        }
        micRecorder.release()
    }

    override fun buildMeasurementEntity(baseInfos: MeasurementBase) = SoundMeasurement(
            baseInfo = baseInfos
    )

    override suspend fun localUpload(m: SoundMeasurement) = msrsRepo.postSoundMsr(m, NetworkMode.OFFLINE)
    override suspend fun onlineUpload(m: SoundMeasurement) = msrsRepo.postSoundMsr(m, NetworkMode.ONLINE)


}