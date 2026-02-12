package com.example.tvscreendsp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for the TV Screen DSP application.
 * 
 * Uses the singleton pattern to ensure only one database instance
 * exists throughout the app lifecycle.
 * 
 * ## Version History
 * - Version 1: Initial schema with MeasurementEntity
 * 
 * ## Migration Notes
 * When modifying the schema:
 * 1. Increment the version number
 * 2. Add a Migration object defining the schema change
 * 3. Add the migration to the builder with .addMigrations()
 * 
 * Example:
 * ```kotlin
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("ALTER TABLE measurements ADD COLUMN newField TEXT")
 *     }
 * }
 * ```
 */
@Database(
    entities = [MeasurementEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Returns the MeasurementDao for database operations.
     */
    abstract fun measurementDao(): MeasurementDao
    
    companion object {
        private const val DATABASE_NAME = "tv_screen_dsp.db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Returns the singleton database instance.
         * Creates the database if it doesn't exist.
         * 
         * Thread-safe using double-checked locking pattern.
         * 
         * @param context Application context
         * @return The singleton AppDatabase instance
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        
        /**
         * Migration from version 1 to 2: Add customName column.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE measurements ADD COLUMN customName TEXT DEFAULT NULL"
                )
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                // Fallback if migration fails (dev only)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
        
        /**
         * Clears the singleton instance.
         * Only use in tests or when deliberately closing the database.
         */
        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
