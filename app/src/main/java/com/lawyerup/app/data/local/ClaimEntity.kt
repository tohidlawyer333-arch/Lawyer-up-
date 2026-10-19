package com.lawyerup.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "claims")
data class ClaimEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val forum: String, // حقوقی, کیفری, کار, مالیاتی, شهرداری, ثبتی, تجاری
    val authority: String,
    val description: String,
    val articles: String,
    val deadline: String,
    val feeRate: String
)
