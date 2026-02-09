package com.example.tvscreendsp.data

/**
 * # Common Room Database Pitfalls - Developer Reference
 * 
 * This file documents critical issues when using Room in this app.
 * Read this before modifying any database code.
 * 
 * ## 1. MAIN THREAD ACCESS
 * 
 * **Pitfall**: Calling DAO methods on the main thread
 * **Symptom**: `IllegalStateException: Cannot access database on the main thread`
 * **Solution**: 
 * - All DAO methods should be `suspend` functions
 * - Call from `viewModelScope.launch { }` or other coroutine scope
 * - Use `Flow<>` for reactive queries (automatically offloads to background)
 * 
 * ```kotlin
 * // ❌ WRONG - crashes on main thread
 * val data = dao.getMeasurementById(id)
 * 
 * // ✅ CORRECT - suspend function in coroutine
 * viewModelScope.launch {
 *     val data = dao.getMeasurementById(id)  // runs on IO thread
 * }
 * ```
 * 
 * ---
 * 
 * ## 2. ENTITY UPDATE ISSUES
 * 
 * **Pitfall**: Forgetting to include @PrimaryKey in update
 * **Symptom**: Update does nothing or throws exception
 * **Solution**: Always use `copy()` on the fetched entity
 * 
 * ```kotlin
 * // ❌ WRONG - creating new entity without ID
 * val updated = MeasurementEntity(wavFilePath = path, ...)
 * dao.update(updated)  // ID is 0, won't match existing record
 * 
 * // ✅ CORRECT - copy existing entity
 * val existing = dao.getMeasurementById(id)!!
 * val updated = existing.copy(frequency = 1234.5)
 * dao.update(updated)  // ID preserved, updates correct row
 * ```
 * 
 * **Pitfall**: Partial updates with null fields
 * **Symptom**: Fields unexpectedly become null after update
 * **Solution**: Always copy ALL fields, not just changed ones
 * 
 * ---
 * 
 * ## 3. MIGRATION PLANNING
 * 
 * **Pitfall**: Adding fields without a migration
 * **Symptom**: App crashes on update with "no known migration path"
 * **Solution**: Plan migrations from day 1
 * 
 * ```kotlin
 * // For adding a new column:
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("ALTER TABLE measurements ADD COLUMN newField TEXT")
 *     }
 * }
 * 
 * // Add to database builder:
 * Room.databaseBuilder(...)
 *     .addMigrations(MIGRATION_1_2)
 *     .build()
 * ```
 * 
 * **Pitfall**: Using `fallbackToDestructiveMigration()` in production
 * **Symptom**: User data is lost on app update
 * **Solution**: 
 * - Only use for development
 * - Write proper migrations for production
 * - Test migrations with Room's MigrationTestHelper
 * 
 * ---
 * 
 * ## 4. FLOW COLLECTION ISSUES
 * 
 * **Pitfall**: Collecting Flow multiple times
 * **Symptom**: Multiple database observers, duplicate queries
 * **Solution**: Use `stateIn()` to share a single subscription
 * 
 * ```kotlin
 * // In ViewModel:
 * val measurements = repository.getAllMeasurements()
 *     .stateIn(
 *         scope = viewModelScope,
 *         started = SharingStarted.WhileSubscribed(5000),
 *         initialValue = emptyList()
 *     )
 * ```
 * 
 * **Pitfall**: Not using lifecycle-aware collection in Compose
 * **Symptom**: Database queries continue when app is backgrounded
 * **Solution**: Use `collectAsStateWithLifecycle()` in Compose
 * 
 * ```kotlin
 * val measurements by viewModel.measurements.collectAsStateWithLifecycle()
 * ```
 * 
 * ---
 * 
 * ## 5. SINGLETON DATABASE
 * 
 * **Pitfall**: Creating multiple database instances
 * **Symptom**: Inconsistent data, database locks
 * **Solution**: Use singleton pattern with `@Volatile` and `synchronized`
 * 
 * ```kotlin
 * companion object {
 *     @Volatile
 *     private var INSTANCE: AppDatabase? = null
 *     
 *     fun getInstance(context: Context): AppDatabase {
 *         return INSTANCE ?: synchronized(this) {
 *             INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
 *         }
 *     }
 * }
 * ```
 * 
 * ---
 * 
 * ## 6. FILE PATH STORAGE
 * 
 * **Pitfall**: Storing WAV data as BLOB in Room
 * **Symptom**: Database becomes huge, slow queries
 * **Solution**: Store only the file path as String
 * 
 * **Pitfall**: Deleting database record without deleting file
 * **Symptom**: Orphaned WAV files wasting storage
 * **Solution**: Delete file in repository before deleting record
 * 
 * ```kotlin
 * suspend fun deleteMeasurement(id: Long): Boolean {
 *     val measurement = dao.getMeasurementById(id) ?: return false
 *     File(measurement.wavFilePath).delete()  // Delete file first
 *     return dao.deleteById(id) > 0           // Then delete record
 * }
 * ```
 * 
 * ---
 * 
 * ## 7. SCHEMA EXPORT
 * 
 * **Pitfall**: Not exporting schema for version tracking
 * **Symptom**: Hard to verify migrations, no schema history
 * **Solution**: Set `exportSchema = true` and configure export dir
 * 
 * ```kotlin
 * @Database(
 *     entities = [MeasurementEntity::class],
 *     version = 1,
 *     exportSchema = true  // Export JSON schema
 * )
 * ```
 * 
 * In `build.gradle.kts`:
 * ```kotlin
 * ksp {
 *     arg("room.schemaLocation", "$projectDir/schemas")
 * }
 * ```
 * 
 * ---
 * 
 * ## QUICK REFERENCE: Room Checklist
 * 
 * Before implementing:
 * ☐ All DAO methods are suspend or return Flow?
 * ☐ Entity has @PrimaryKey with autoGenerate?
 * ☐ Database version is tracked?
 * ☐ exportSchema = true?
 * 
 * During implementation:
 * ☐ Using copy() for entity updates?
 * ☐ Calling from coroutine scope?
 * ☐ Using stateIn() for shared Flows?
 * 
 * Before release:
 * ☐ Migration path for each version change?
 * ☐ fallbackToDestructiveMigration() removed?
 * ☐ File cleanup on record deletion?
 */
@Suppress("unused")
object RoomPitfalls {
    // This file is documentation only - no runtime code
}
