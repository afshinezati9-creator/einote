package com.einote.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migrateFromVersion2ToCurrentPreservesDataAndAddsAllTables() = runBlocking {
        createVersion2DatabaseWithData()

        val db = openMigratedDatabase()
        try {
            val note = db.noteDao().getById(7L)
            val block = db.noteBlockDao().getById(11L)

            assertEquals("قدیمی", note?.title)
            assertEquals("محتوای قدیمی", note?.content)
            assertEquals("کار قدیمی", block?.content)

            val migrated = db.openHelper.writableDatabase
            assertEquals(6, migrated.version)
            assertTrue(tableExists(migrated, "finance_transactions"))
            assertTrue(tableExists(migrated, "attachments"))
            assertTrue(columnExists(migrated, "note_blocks", "dueAt"))
            assertTrue(columnExists(migrated, "note_blocks", "reminderAt"))
            assertTrue(columnExists(migrated, "note_blocks", "completedAt"))
            assertTrue(columnExists(migrated, "note_blocks", "textColor"))
            assertTrue(columnExists(migrated, "note_blocks", "textSizeSp"))
        } finally {
            db.close()
        }
    }

    @Test
    fun migrateFromVersion2CreatesNullablePlanningFields() {
        createVersion2DatabaseWithData(insertData = false)

        val db = openMigratedDatabase()
        try {
            val migrated = db.openHelper.writableDatabase
            assertTrue(columnExists(migrated, "note_blocks", "dueAt"))
            assertTrue(columnExists(migrated, "note_blocks", "reminderAt"))
            assertTrue(columnExists(migrated, "note_blocks", "completedAt"))
        } finally {
            db.close()
        }
    }

    private fun openMigratedDatabase(): NoteDatabase =
        Room.databaseBuilder(
            context,
            NoteDatabase::class.java,
            DB_NAME
        )
            .allowMainThreadQueries()
            .addMigrations(*NoteDatabase.ALL_MIGRATIONS)
            .build()

    private fun createVersion2DatabaseWithData(insertData: Boolean = true) {
        val database = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(DB_NAME),
            null
        )

        database.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                tags TEXT NOT NULL,
                isPinned INTEGER NOT NULL,
                isArchived INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE note_blocks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                noteId INTEGER NOT NULL,
                type TEXT NOT NULL,
                content TEXT NOT NULL,
                checked INTEGER NOT NULL,
                position INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        if (insertData) {
            database.insertWithOnConflict(
                "notes",
                null,
                ContentValues().apply {
                    put("id", 7L)
                    put("title", "قدیمی")
                    put("content", "محتوای قدیمی")
                    put("tags", "آزمون")
                    put("isPinned", 1)
                    put("isArchived", 0)
                    put("createdAt", 1000L)
                    put("updatedAt", 2000L)
                },
                SQLiteDatabase.CONFLICT_NONE
            )
            database.insertWithOnConflict(
                "note_blocks",
                null,
                ContentValues().apply {
                    put("id", 11L)
                    put("noteId", 7L)
                    put("type", "CHECKLIST")
                    put("content", "کار قدیمی")
                    put("checked", 0)
                    put("position", 0)
                    put("createdAt", 1000L)
                    put("updatedAt", 2000L)
                },
                SQLiteDatabase.CONFLICT_NONE
            )
        }

        database.version = 2
        database.close()
    }

    private fun tableExists(database: SupportSQLiteDatabase, name: String): Boolean =
        database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(name)
        ).use { cursor -> cursor.moveToFirst() }

    private fun columnExists(
        database: SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean =
        database.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return@use true
            }
            false
        }

    companion object {
        private const val DB_NAME = "einote_migration_test.db"
    }
}
