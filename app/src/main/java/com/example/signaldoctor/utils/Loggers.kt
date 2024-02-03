package com.example.signaldoctor.utils

import android.util.Log

object Loggers {
    fun consoleDebug(msg : String){
        Log.i("DEBUG:", msg)
    }
}

fun <T> printAndReturn(tag : String, t: T) : T{
    Log.i(tag, "$t")
    return t
}