package com.example.signaldoctor.mapUtils

class RectD(
     val left : Double,
    val top : Double,
    val right : Double,
    val bottom : Double
) {
    fun contains(x: Double, y: Double): Boolean {
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
    }
}