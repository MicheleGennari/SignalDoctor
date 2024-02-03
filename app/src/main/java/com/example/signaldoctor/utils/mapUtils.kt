package com.example.signaldoctor.utils

import com.google.firebase.database.DataSnapshot

fun <K,V>  Iterable<DataSnapshot>.toMap(intoMap : Iterable<DataSnapshot>.(m : MutableMap<K,V>) -> Unit) : MutableMap<K,V> {

    val map = mutableMapOf<K, V>()

    this.intoMap(map)

    return map
}