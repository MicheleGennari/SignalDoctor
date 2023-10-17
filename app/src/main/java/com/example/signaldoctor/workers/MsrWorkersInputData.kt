package com.example.signaldoctor.workers

import androidx.work.workDataOf
import com.example.signaldoctor.contracts.Measure


class MsrWorkersInputData(
     //msr : Double,
     val msrType : Measure,
     z : Int,
     x : Int,
     y : Int,
     zMax : Int,
     zMin : Int,
) {

    companion object MsrWorkersInputDataKeys {
        const val MSR_TYPE_KEY = "msrType"
        //const val MSR_KEY = "msr"
        const val Z_KEY = "z"
        const val X_KEY = "x"
        const val Y_KEY = "y"
        const val Z_MAX_KEY = "zMax"
        const val Z_MIN_KEY = "zMin"
    }

    val workData = workDataOf(
        //MSR_KEY to msr,
        MSR_TYPE_KEY to msrType.name,
        X_KEY to x,
        Y_KEY to y,
        Z_KEY to z,
        Z_MAX_KEY to zMax,
        Z_MIN_KEY to zMin
    )
}

