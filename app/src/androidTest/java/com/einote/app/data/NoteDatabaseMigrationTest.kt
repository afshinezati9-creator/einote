package com.einote.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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
        db = Room.databaseBuilder(
            context,
            NoteDatabase::class.java,
            DB_NAME
        ).addMigrations(
            *MIGRATIONS
        ).allowMainThreadQueries().build()

        val note = db.noteDao().getById(7)
        val block = db.noteBlockDao().getById(11)
        val financeTableExists = tableExists(db.openHelper.writableDatabase, "finance_transactions")
        val attachmentsTableExists = tableExists(db.openHelper.writableDatabase, "attachments")

        assertNotNull(note)
        assertEquals("قدیمی", note!!.title)
        assertEquals("محتوای قدیمی", note.content)
        assertNotNull(block)
        assertEquals("کار قدیمی", block!!.content)
        assertEquals(5, db.openHelper.writableDatabase.version)
        assertTrue(financeTableExists)
        assertTrue(attachmentsTableExists)
    }

    @Test
    fun migrateFromVersion2CreatesNullablePlanningFields() = runBlocking {
        db = Room.databaseBuilder(
            context,
            NoteDatabase::class.java,
            DB_NAME
        ).addMigrations(*MIGRATIONS).allowMainThreadQueries().build()

        val block = db.noteBlockDao().getById(11)!!
        assertEquals(null, block.dueAt)
        assertEquals(null, block.reminderAt)
        assertEquals(null, block.completedAt)
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
