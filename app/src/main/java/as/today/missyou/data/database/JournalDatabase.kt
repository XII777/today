package `as`.today.missyou.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        JournalEntryEntity::class,
        AttachmentEntity::class,
        JournalStateEntity::class,
    ],
    version = JournalDatabase.VERSION,
    exportSchema = true,
)
internal abstract class JournalDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao

    companion object {
        const val VERSION = 1
        const val NAME = "today-journal.db"

        @Volatile
        private var instance: JournalDatabase? = null

        fun getInstance(context: Context): JournalDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /** Used by instrumentation tests to install an isolated database. */
        fun overrideInstanceForTesting(database: JournalDatabase?) {
            instance = database
        }

        private fun build(context: Context): JournalDatabase =
            Room.databaseBuilder(context, JournalDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(ImmutabilityTriggers)
                // No fallbackToDestructiveMigration(): losing a journal to a schema
                // mistake is not an acceptable failure mode. If a migration is
                // missing the app refuses to start rather than starting empty.
                .build()
    }
}

/**
 * Installs the immutability triggers.
 *
 * They are re-applied on every open on purpose. That way the security property
 * survives a partial restore, cannot be lost by a future migration that rebuilds a
 * table, and costs a handful of `IF NOT EXISTS` statements.
 *
 * The work happens on the connection thread, never on the main thread.
 */
private object ImmutabilityTriggers : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) = apply(db)

    override fun onOpen(db: SupportSQLiteDatabase) = apply(db)

    private fun apply(db: SupportSQLiteDatabase) {
        Triggers.STATEMENTS.forEach(db::execSQL)
    }
}

/**
 * Schema migrations.
 *
 * The list is explicit and non-destructive by construction: the specification
 * forbids destructive migrations and requires that an application update can never
 * delete an entry, unlock an old entry, drop a tag, or destroy formatting. There is
 * currently a single schema version, so there is nothing to migrate yet; new
 * versions add an entry to [ALL] rather than editing an existing one.
 *
 * `JournalDatabaseMigrationTest` in `androidTest` asserts the resulting invariant:
 * data and immutability triggers survive a full close and reopen.
 */
internal object Migrations {

    val ALL: Array<Migration> = emptyArray()
}
