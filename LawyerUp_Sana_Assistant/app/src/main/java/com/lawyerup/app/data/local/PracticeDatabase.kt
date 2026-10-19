package com.lawyerup.app.data.local
import android.content.Context
import androidx.room.*
@Database(entities=[PracticeCase::class,Notice::class,Deadline::class,Draft::class],version=1,exportSchema=false)
abstract class PracticeDatabase:RoomDatabase(){ abstract fun dao():PracticeDao
 companion object { @Volatile private var i:PracticeDatabase?=null; fun get(c:Context)=i?: synchronized(this){i?:Room.databaseBuilder(c.applicationContext,PracticeDatabase::class.java,"lawyerup_practice.db").build().also{i=it}}}}
