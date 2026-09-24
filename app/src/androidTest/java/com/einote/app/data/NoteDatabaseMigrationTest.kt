package com.einote.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NoteDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var helper: MigrationTestHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        helper = MigrationTestHelper(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
            NoteDatabase::class.java
        )
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    @Throws(IOException::class)
    fun migrateFromVersion2ToCurrentPreservesDataAndAddsAllTables() {
        val database = createVersion2Database()

        database.insert(
            "notes",
            SQLiteDatabase.CONFLICT_NONE,
            android.content.ContentValues().apply {
                put("id", 7L)
                put("title", "قدیمی")
                put("content", "محتوای قدیمی")
                put("tags", "آزمون")
                put("isPinned", 1)
                put("isArchived", 0)
                put("createdAt", 1000L)
                put("updatedAt", 2000L)
            }
        )
        database.insert(
            "note_blocks",
            SupportSQLiteDatabase.CONFLICT_NONE,
            android.content.ContentValues().apply {
                put("id", 11L)
                put("noteId", 7L)
                put("type", "CHECKLIST")
                put("content", "کار قدیمی")
                put("checked", 0)
                put("position", 0)
                put("createdAt", 1000L)
                put("updatedAt", 2000L)
            }
        )
        database.close()

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            5,
            true,
            *NoteDatabase.ALL_MIGRATIONS
        )

        migrated.query("SELECT title, content FROM notes WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("قدیمی", cursor.getString(0))
            assertEquals("محتوای قدیمی", cursor.getString(1))
        }
        migrated.query("SELECT content FROM note_blocks WHERE id = 11").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("کار قدیمی", cursor.getString(0))
        }

        assertTrue(tableExists(migrated, "finance_transactions"))
        assertTrue(tableExists(migrated, "attachments"))
        assertTrue(columnExists(migrated, "note_blocks", "dueAt"))
        assertTrue(columnExists(migrated, "note_blocks", "reminderAt"))
        assertTrue(columnExists(migrated, "note_blocks", "completedAt"))
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateFromVersion2CreatesNullablePlanningFields() {
        val database = createVersion2Database()
        database.close()

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            5,
            true,
            *NoteDatabase.ALL_MIGRATIONS
        )

        assertTrue(columnExists(migrated, "note_blocks", "dueAt"))
        assertTrue(columnExists(migrated, "note_blocks", "reminderAt"))
        assertTrue(columnExists(migrated, "note_blocks", "completedAt"))
        migrated.close()
    }

    private fun createVersion2Database(): SupportSQLiteDatabase =
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
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
            execSQL(
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
