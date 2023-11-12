package com.example.signaldoctor.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy


@Dao
abstract class BaseMsrsDAO<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE )
    abstract fun postMsr(measurement : T)

}