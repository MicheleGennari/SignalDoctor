package com.example.signaldoctor.workers

object NoiseResult {

    fun failure() : Long = -1

    fun success(value : Long) = value
}