package com.bbirds.covidtracker.data

import androidx.room.*

@Entity
data class Offset(@PrimaryKey val id: Int, @ColumnInfo(name = "offset") val _offset: Int)

@Dao
interface OffsetDao {

    @Query("SELECT * FROM user")
    fun get(): Offset

    @Query("UPDATE `offset` SET _offset = :_offset WHERE id=0")
    fun set(_offset: Offset)

}