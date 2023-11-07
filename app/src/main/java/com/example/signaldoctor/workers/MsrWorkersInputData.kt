package com.example.signaldoctor.workers

import androidx.work.Data
import androidx.work.workDataOf
import com.example.signaldoctor.contracts.Measure


class MsrWorkersInputData(
     val msrType : String,
     val lat : Double,
     val long : Double
) {

    companion object MsrWorkersInputDataKeys {

        const val MSR_TYPE_KEY = "msrType"
        const val MSR_KEY = "msr"
        const val Z_KEY = "z"
        const val X_KEY = "x"
        const val Y_KEY = "y"
        const val Z_MAX_KEY = "zMax"
        const val Z_MIN_KEY = "zMin"
        const val LAT_KEY = "lat"
        const val LONG_KEY = "long"

    }


    fun asWorkData() : Data = workDataOf(
        //msr is not needed since it will be collected by MsrWorker
        //MSR_KEY to msr,
        MSR_TYPE_KEY to msrType,
        LAT_KEY to lat,
        LONG_KEY to long
    )

}