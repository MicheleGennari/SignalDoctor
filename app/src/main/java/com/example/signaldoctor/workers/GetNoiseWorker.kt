package com.example.signaldoctor.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.provider.MediaStore.Audio
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.signaldoctor.onlineDatabase.consoledebug
import org.jtransforms.fft.FloatFFT_1D

class GetNoiseWorker(context : Context, params : WorkerParameters) : CoroutineWorker(context, params) {


    override suspend fun doWork(): Result {
        val msr = work(applicationContext)
        if (msr != NoiseResult.failure())
            return Result.success(workDataOf("msr" to msr))
            else return Result.failure()
    }
}


fun work(ctx : Context) : Long {
    if (ActivityCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        consoledebug("Record permission not granted")
        return NoiseResult.failure()
    }

    try{
        val internalBufferSize = 2048 //it's important that this value is a power of 2 (see variable myBuffer below in the code)
        var micRecorder : AudioRecord? = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            internalBufferSize
        )
        if(micRecorder?.state == AudioRecord.STATE_UNINITIALIZED) {
            consoledebug("AudioRecorder object's state has value 'UNINITIALIZED. Probably there is a problem with creation" +
                    "parameters not compliant with device's hardware capabilities ")
            micRecorder?.release()
            micRecorder = null
            consoledebug("recorde uninitialized")
            return NoiseResult.failure()
        }


        micRecorder?.apply {
            //the number of samples requested has to be an even number->
            //-> in order to guarantee that FFT can correctly split between the odd and even samples to work correctly
            val shortBuffer = ShortArray(internalBufferSize)
            val fftBuffer : FloatArray

            startRecording()

            val readResult = read(shortBuffer,0, internalBufferSize)
            when {
                /*AudioRecord.ERROR,
                AudioRecord.ERROR_BAD_VALUE,
                AudioRecord.ERROR_DEAD_OBJECT,
                AudioRecord.ERROR_INVALID_OPERATION*/ readResult<0 -> {
                    stop()
                    release()
                    consoledebug("ERROR during AudioRecorder's read() function")
                    return NoiseResult.failure()
                }
                else -> {


                    //transfer data in floatArray for FFT's computation
                    consoledebug("INITIAL VALUES IN BUFFER")
                    shortBuffer.forEachIndexed{index, item->
                        consoledebug("INDEX:${index}, ${item}")
                    }


                    //since FFT can compute only floating point numbers, we'll ->
                    //tranform the shortArray buffer into a floatArray buffer
                    fftBuffer =  shortBuffer.castToFloatArray()
                    FloatFFT_1D(internalBufferSize.toLong()).realForward(fftBuffer)

                    //debug line
                    fftBuffer.forEachIndexed() {index, value ->
                        consoledebug("At Index:${index}: " +value.toString())
                    }
                }
            }

            stop()
            release()
            micRecorder = null
            return NoiseResult.success(fftBuffer.size.toLong())
        }

    }catch (e : IllegalArgumentException){
        consoledebug("creation of object 'AudioRecord' throwed IllegalArgumentException. Maybe creation parameters are not" +
                "correct for the device's recording hardware")
        e.printStackTrace()
        return NoiseResult.failure()
    }


    return NoiseResult.failure()

}

fun ShortArray.castToFloatArray() : FloatArray{
    val floatArray = FloatArray(this.size)
    forEachIndexed{index, sh ->
        floatArray[index] = sh.toFloat()
    }
    return floatArray
}