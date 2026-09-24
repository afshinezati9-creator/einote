package com.einote.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var db: NoteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)

        val oldDb = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(DB_NAME),
            null
        )
        oldDb.execSQL(
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
        oldDb.execSQL(
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
        oldDb.execSQL(
            """
            INSERT INTO notes
            (id, title, content, tags, isPinned, isArchived, createdAt, updatedAt)
            VALUES (7, 'قدیمی', 'محتوای قدیمی', 'آزمون', 1, 0, 1000, 2000)
            """.trimIndent()
        )
        oldDb.execSQL(
            """
            INSERT INTO note_blocks
            (id, noteId, type, content, checked, position, createdAt, updatedAt)
            VALUES (11, 7, 'CHECKLIST', 'کار قدیمی', 0, 0, 1000, 2000)
            """.trimIndent()
        )
        oldDb.version = 2
        oldDb.close()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migrateFromVersion2ToCurrentPreservesDataAndAddsAllTables() = runBlocking {
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath(DB_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        database.beginTransaction()
        try {
            MIGRATIONS.forEach { it.migrate(androidx.sqlite.db.framework.FrameworkSQLiteDatabase(database)) }
            database.version = 5
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        val noteCursor = database.rawQuery("SELECT title, content FROM notes WHERE id = 7", null)
        val blockCursor = database.rawQuery("SELECT content FROM note_blocks WHERE id = 11", null)
        val noteTitle: String
        val noteContent: String
        val blockContent: String
        noteCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            noteTitle = cursor.getString(0)
            noteContent = cursor.getString(1)
        }
        blockCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            blockContent = cursor.getString(0)
        }
        val financeTableExists = tableExists(database, "finance_transactions")
        val attachmentsTableExists = tableExists(database, "attachments")

        assertEquals("قدیمی", noteTitle)
        assertEquals("محتوای قدیمی", noteContent)
        assertEquals("کار قدیمی", blockContent)
        assertEquals(5, database.version)
        database.close()
        assertTrue(financeTableExists)
        assertTrue(attachmentsTableExists)
    }

    @Test
    fun migrateFromVersion2CreatesNullablePlanningFields() = runBlocking {
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath(DB_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        MIGRATIONS.forEach { it.migrate(androidx.sqlite.db.framework.FrameworkSQLiteDatabase(database)) }
        val cursor = database.rawQuery("PRAGMA table_info(note_blocks)", null)
        val columns = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) columns += it.getString(1) }
        database.close()
        assertTrue("dueAt" in columns)
        assertTrue("reminderAt" in columns)
        assertTrue("completedAt" in columns)
    }

    private fun tableExists(database: androidx.sqlite.db.SupportSQLiteDatabase, name: String): Boolean {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(name)
        ).use { cursor -> return cursor.moveToFirst() }
    }

    companion object {
        private const val DB_NAME = "einote_migration_test.db"
        private val MIGRATIONS = NoteDatabase.ALL_MIGRATIONS
    }
}
