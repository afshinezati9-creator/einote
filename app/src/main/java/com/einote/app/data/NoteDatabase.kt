package com.einote.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, NoteBlockEntity::class, FinanceTransactionEntity::class, AttachmentEntity::class],
    version = 8,
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteBlockDao(): NoteBlockDao
    abstract fun financeTransactionDao(): FinanceTransactionDao
    abstract fun attachmentDao(): AttachmentDao

    suspend fun clearAllData() {
        attachmentDao().deleteAll()
        noteBlockDao().deleteAll()
        financeTransactionDao().deleteAll()
        noteDao().deleteAll()
    }

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amountToman INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        category TEXT NOT NULL,
                        transactionAt INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        localPath TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_noteId ON attachments(noteId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN textColor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN textSizeSp REAL NOT NULL DEFAULT 17.0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN space TEXT NOT NULL DEFAULT 'WRITING'")
                db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_space ON notes(space)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN alignment TEXT NOT NULL DEFAULT 'auto'")
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )

        fun get(context: Context): NoteDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, NoteDatabase::class.java, "einote.db"
                ).addMigrations(*ALL_MIGRATIONS).build()
                    .also { INSTANCE = it }
            }
    }
}
