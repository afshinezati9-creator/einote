package com.einote.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, NoteBlockEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteBlockDao(): NoteBlockDao

    companion object {
        @Volatile private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN dueAt INTEGER")
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN reminderAt INTEGER")
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN completedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_blocks_type_dueAt ON note_blocks(type, dueAt)")
            }
        }

        fun get(context: Context): NoteDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "einote.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
