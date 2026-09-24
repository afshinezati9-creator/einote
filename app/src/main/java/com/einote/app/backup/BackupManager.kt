package com.einote.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.einote.app.ReminderWorker
import androidx.room.withTransaction
import com.einote.app.data.AttachmentEntity
import com.einote.app.data.FinanceTransactionEntity
import com.einote.app.data.NoteBlockEntity
import com.einote.app.data.NoteDatabase
import com.einote.app.data.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {
    companion object {
        const val FORMAT_VERSION = 1
        private const val SCHEMA_VERSION = 6
        private const val MANIFEST = "manifest.json"
        private const val NOTES = "data/notes.json"
        private const val BLOCKS = "data/blocks.json"
        private const val FINANCE = "data/finance.json"
        private const val ATTACHMENTS = "data/attachments.json"
        private const val FILES_DIR = "files/"
    }

    private val db = NoteDatabase.get(context)

    suspend fun exportTo(uri: Uri) {
        val notes = db.noteDao().getAll()
        val blocks = db.noteBlockDao().getAll()
        val finance = db.financeTransactionDao().getAll()
        val attachments = db.attachmentDao().getAll()

        attachments.forEach { attachment ->
            if (!File(attachment.localPath).isFile) {
                throw BackupException("فایل پیوست «" + attachment.fileName + "» پیدا نشد.")
            }
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                putText(zip, MANIFEST, manifestJson(notes.size, blocks.size, finance.size, attachments.size))
                putText(zip, NOTES, notesJson(notes))
                putText(zip, BLOCKS, blocksJson(blocks))
                putText(zip, FINANCE, financeJson(finance))
                putText(zip, ATTACHMENTS, attachmentsJson(attachments))

                attachments.forEach { attachment ->
                    val file = File(attachment.localPath)
                    zip.putNextEntry(ZipEntry(FILES_DIR + attachment.id + ".bin"))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw BackupException("محل ذخیره پشتیبان قابل دسترسی نیست.")
    }

    suspend fun importFrom(uri: Uri) {
        val tempRoot = File(context.cacheDir, "backup-import-" + System.currentTimeMillis())
        val extracted = File(tempRoot, "files")
        tempRoot.mkdirs()
        extracted.mkdirs()

        try {
            var manifestText: String? = null
            var notesText: String? = null
            var blocksText: String? = null
            var financeText: String? = null
            var attachmentsText: String? = null

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }

                        when (entry.name) {
                            MANIFEST -> manifestText = zip.readUtf8Limited(1024 * 1024)
                            NOTES -> notesText = zip.readUtf8Limited(16 * 1024 * 1024)
                            BLOCKS -> blocksText = zip.readUtf8Limited(32 * 1024 * 1024)
                            FINANCE -> financeText = zip.readUtf8Limited(16 * 1024 * 1024)
                            ATTACHMENTS -> attachmentsText = zip.readUtf8Limited(32 * 1024 * 1024)
                            else -> if (entry.name.matches(Regex("""files/[0-9]+\.bin"""))) {
                                val fileName = entry.name.removePrefix(FILES_DIR)
                                val target = File(extracted, fileName)
                                target.outputStream().use { out -> zip.copyTo(out) }
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw BackupException("فایل پشتیبان قابل خواندن نیست.")

            val manifest = parseManifest(requireText(manifestText, MANIFEST))
            val notes = parseNotes(requireText(notesText, NOTES))
            val blocks = parseBlocks(requireText(blocksText, BLOCKS))
            val finance = parseFinance(requireText(financeText, FINANCE))
            val attachmentRecords = parseAttachments(requireText(attachmentsText, ATTACHMENTS))

            validate(manifest, notes, blocks, finance, attachmentRecords, extracted)

            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val copiedFiles = mutableListOf<File>()
            val restoredAttachments = attachmentRecords.map { record ->
                val source = File(extracted, record.id.toString() + ".bin")
                val target = File(
                    attachmentsDir,
                    "restored_" + System.currentTimeMillis() + "_" +
                        record.id + "_" + safeFileName(record.entity.fileName)
                )
                source.copyTo(target, overwrite = false)
                copiedFiles += target
                record.entity.copy(localPath = target.absolutePath, sizeBytes = target.length())
            }

            try {
                db.withTransaction {
                    db.clearAllData()
                    db.noteDao().insertAll(notes)
                    db.noteBlockDao().insertAll(blocks)
                    db.financeTransactionDao().insertAll(finance)
                    db.attachmentDao().insertAll(restoredAttachments)
                }
                scheduleRestoredReminders(blocks)
            } catch (e: Exception) {
                copiedFiles.forEach { runCatching { it.delete() } }
                throw BackupException("بازیابی انجام نشد؛ اطلاعات فعلی دست‌نخورده باقی ماند.", e)
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }


    private fun scheduleRestoredReminders(blocks: List<NoteBlockEntity>) {
        val workManager = WorkManager.getInstance(context)
        blocks.forEach { block ->
            val reminderAt = block.reminderAt ?: return@forEach
            if (block.checked || reminderAt <= System.currentTimeMillis()) return@forEach
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(reminderAt - System.currentTimeMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.Data.Builder().putLong(ReminderWorker.KEY_BLOCK_ID, block.id).build())
                .build()
            workManager.enqueueUniqueWork(
                ReminderWorker.WORK_PREFIX + block.id,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun validate(
        manifest: Manifest,
        notes: List<NoteEntity>,
        blocks: List<NoteBlockEntity>,
        finance: List<FinanceTransactionEntity>,
        attachments: List<AttachmentRecord>,
        extracted: File
    ) {
        if (manifest.formatVersion != FORMAT_VERSION) {
            throw BackupException("نسخه پشتیبان پشتیبانی نمی‌شود.")
        }
        if (manifest.schemaVersion != SCHEMA_VERSION) {
            throw BackupException("نسخه ساختار داده این پشتیبان با این نسخه ای‌نوت سازگار نیست.")
        }
        if (manifest.notesCount != notes.size ||
            manifest.blocksCount != blocks.size ||
            manifest.financeCount != finance.size ||
            manifest.attachmentsCount != attachments.size
        ) {
            throw BackupException("تعداد داده‌های پشتیبان با اطلاعات داخلی آن همخوانی ندارد.")
        }

        val noteIds = notes.map { it.id }.toSet()
        val blockIds = blocks.map { it.id }.toSet()
        val attachmentIds = attachments.map { it.id }.toSet()

        if (noteIds.size != notes.size || noteIds.any { it <= 0 }) {
            throw BackupException("شناسه‌های یادداشت معتبر نیستند.")
        }
        if (blockIds.size != blocks.size || blockIds.any { it <= 0 }) {
            throw BackupException("شناسه‌های بخش‌های یادداشت معتبر نیستند.")
        }
        if (attachmentIds.size != attachments.size || attachmentIds.any { it <= 0 }) {
            throw BackupException("شناسه‌های پیوست معتبر نیستند.")
        }
        if (blocks.any { it.noteId !in noteIds }) {
            throw BackupException("ارتباط بین بخش‌ها و یادداشت‌ها معتبر نیست.")
        }
        if (attachments.any { it.entity.noteId !in noteIds }) {
            throw BackupException("ارتباط بین پیوست‌ها و یادداشت‌ها معتبر نیست.")
        }

        attachments.forEach {
            val source = File(extracted, it.id.toString() + ".bin")
            if (!source.isFile || source.length() <= 0L) {
                throw BackupException("فایل پیوست «" + it.entity.fileName + "» داخل پشتیبان وجود ندارد.")
            }
        }
    }

    private fun manifestJson(n: Int, b: Int, f: Int, a: Int) = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("schemaVersion", SCHEMA_VERSION)
        .put("createdAt", System.currentTimeMillis())
        .put("app", "eiNote")
        .put("notesCount", n)
        .put("blocksCount", b)
        .put("financeCount", f)
        .put("attachmentsCount", a)
        .toString()

    private fun notesJson(items: List<NoteEntity>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("content", it.content)
                .put("tags", it.tags)
                .put("isPinned", it.isPinned)
                .put("isArchived", it.isArchived)
                .put("createdAt", it.createdAt)
                .put("updatedAt", it.updatedAt))
        }
    }.toString()

    private fun blocksJson(items: List<NoteBlockEntity>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject()
                .put("id", it.id)
                .put("noteId", it.noteId)
                .put("type", it.type)
                .put("content", it.content)
                .put("checked", it.checked)
                .put("position", it.position)
                .put("dueAt", it.dueAt ?: JSONObject.NULL)
                .put("reminderAt", it.reminderAt ?: JSONObject.NULL)
                .put("completedAt", it.completedAt ?: JSONObject.NULL)
                .put("textColor", it.textColor)
                .put("textSizeSp", it.textSizeSp)
                .put("createdAt", it.createdAt)
                .put("updatedAt", it.updatedAt))
        }
    }.toString()

    private fun financeJson(items: List<FinanceTransactionEntity>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("amountToman", it.amountToman)
                .put("type", it.type)
                .put("category", it.category)
                .put("transactionAt", it.transactionAt)
                .put("note", it.note)
                .put("createdAt", it.createdAt))
        }
    }.toString()

    private fun attachmentsJson(items: List<AttachmentEntity>): String = JSONArray().apply {
        items.forEach {
            put(JSONObject()
                .put("id", it.id)
                .put("noteId", it.noteId)
                .put("fileName", it.fileName)
                .put("mimeType", it.mimeType)
                .put("sizeBytes", it.sizeBytes)
                .put("createdAt", it.createdAt))
        }
    }.toString()

    private fun parseManifest(text: String) = JSONObject(text).let {
        Manifest(
            it.getInt("formatVersion"),
            it.getInt("schemaVersion"),
            it.getInt("notesCount"),
            it.getInt("blocksCount"),
            it.getInt("financeCount"),
            it.getInt("attachmentsCount")
        )
    }

    private fun parseNotes(text: String): List<NoteEntity> = JSONArray(text).let { array ->
        (0 until array.length()).map { i ->
            array.getJSONObject(i).let {
                NoteEntity(
                    it.getLong("id"),
                    it.getString("title"),
                    it.getString("content"),
                    it.getString("tags"),
                    it.getBoolean("isPinned"),
                    it.getBoolean("isArchived"),
                    it.getLong("createdAt"),
                    it.getLong("updatedAt")
                )
            }
        }
    }

    private fun parseBlocks(text: String): List<NoteBlockEntity> = JSONArray(text).let { array ->
        (0 until array.length()).map { i ->
            array.getJSONObject(i).let {
                NoteBlockEntity(
                    it.getLong("id"),
                    it.getLong("noteId"),
                    it.getString("type"),
                    it.getString("content"),
                    it.getBoolean("checked"),
                    it.getInt("position"),
                    it.optNullableLong("dueAt"),
                    it.optNullableLong("reminderAt"),
                    it.optNullableLong("completedAt"),
                    it.optLong("textColor", 0L),
                    it.optDouble("textSizeSp", 17.0).toFloat(),
                    it.getLong("createdAt"),
                    it.getLong("updatedAt")
                )
            }
        }
    }

    private fun parseFinance(text: String): List<FinanceTransactionEntity> = JSONArray(text).let { array ->
        (0 until array.length()).map { i ->
            array.getJSONObject(i).let {
                FinanceTransactionEntity(
                    it.getLong("id"),
                    it.getString("title"),
                    it.getLong("amountToman"),
                    it.getString("type"),
                    it.getString("category"),
                    it.getLong("transactionAt"),
                    it.getString("note"),
                    it.getLong("createdAt")
                )
            }
        }
    }

    private fun parseAttachments(text: String): List<AttachmentRecord> = JSONArray(text).let { array ->
        (0 until array.length()).map { i ->
            array.getJSONObject(i).let {
                AttachmentRecord(
                    it.getLong("id"),
                    AttachmentEntity(
                        id = it.getLong("id"),
                        noteId = it.getLong("noteId"),
                        fileName = it.getString("fileName"),
                        mimeType = it.getString("mimeType"),
                        sizeBytes = it.getLong("sizeBytes"),
                        localPath = "",
                        createdAt = it.getLong("createdAt")
                    )
                )
            }
        }
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun requireText(value: String?, name: String): String =
        value ?: throw BackupException("بخش «" + name + "» در پشتیبان وجود ندارد.")

    private fun safeFileName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120)

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun InputStream.readUtf8Limited(maxBytes: Int): String {
        val bytes = readBytes()
        if (bytes.size > maxBytes) {
            throw BackupException("فایل پشتیبان بیش از حد مجاز است.")
        }
        return bytes.toString(Charsets.UTF_8)
    }

    data class Manifest(
        val formatVersion: Int,
        val schemaVersion: Int,
        val notesCount: Int,
        val blocksCount: Int,
        val financeCount: Int,
        val attachmentsCount: Int
    )

    data class AttachmentRecord(val id: Long, val entity: AttachmentEntity)

    class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
