package com.einote.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, NoteBlockEntity::class, FinanceTransactionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteBlockDao(): NoteBlockDao
    abstract fun financeTransactionDao(): FinanceTransactionDao

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

        fun get(context: Context): NoteDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, NoteDatabase::class.java, "einote.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4).build()
                    .also { INSTANCE = it }
            }
    }
}
