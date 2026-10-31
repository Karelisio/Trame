package com.karelisio.trame.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SeriesEntity::class, ChapterEntity::class, HistoryEntity::class, LocalBookEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class TrameDatabase : RoomDatabase() {
    abstract fun series(): SeriesDao
    abstract fun chapters(): ChapterDao
    abstract fun history(): HistoryDao
    abstract fun localBooks(): LocalBookDao

    companion object {
        fun build(context: Context): TrameDatabase =
            Room.databaseBuilder(context, TrameDatabase::class.java, "trame.db").build()
    }
}
