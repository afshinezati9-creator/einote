package com.einote.app

import android.Manifest
import android.app.TimePickerDialog
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einote.app.data.BlockType
import com.einote.app.data.NoteBlockEntity
import com.einote.app.data.NoteEntity
import com.einote.app.ui.NoteViewModel
import com.einote.app.ui.FinanceViewModel
import com.einote.app.ui.AttachmentViewModel
import com.einote.app.data.FinanceTransactionEntity
import com.einote.app.util.PersianFormat
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EiNoteApp() }
    }
}

@Composable
fun EiNoteApp(viewModel: NoteViewModel = viewModel()) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    var plannerOpen by remember { mutableStateOf(false) }
    var financeOpen by remember { mutableStateOf(false) }
    val financeViewModel: FinanceViewModel = viewModel()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    MaterialTheme {
        when {
            editingId != null -> NoteEditor(
                noteId = editingId!!,
                viewModel = viewModel,
                onClose = { editingId = null },
                onReminderScheduled = ::askNotificationPermission
            )
            plannerOpen -> PlannerScreen(viewModel, onBack = { plannerOpen = false })
            financeOpen -> FinanceScreen(financeViewModel, onBack = { financeOpen = false })
            else -> HomeScreen(
                viewModel = viewModel,
                onCreate = { viewModel.createNote { editingId = it } },
                onOpen = { editingId = it },
                onPlanner = { plannerOpen = true },
                onFinance = { financeOpen = true }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: NoteViewModel,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onPlanner: () -> Unit,
    onFinance: () -> Unit
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val query by viewModel.searchQuery.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("جستجو در دفتر…") }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchOpen = false; viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.ArrowBack, "بازگشت")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("eiNote", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = onPlanner) { Icon(Icons.Default.CalendarMonth, "برنامه") }
                        IconButton(onClick = onFinance) { Icon(Icons.Default.AccountBalanceWallet, "مالی") }
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "جستجو") }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Edit, "یادداشت جدید") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("دفتر من", style = MaterialTheme.typography.headlineMedium)
            Text(if (query.isBlank()) "هر چیزی که می‌خواهی، همین‌جا." else "نتیجه‌های جستجو")
            Spacer(Modifier.height(18.dp))
            if (notes.isEmpty()) {
                Text(if (query.isBlank()) "هنوز یادداشتی نداری." else "چیزی پیدا نشد.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { onOpen(note.id) },
                            onPin = { viewModel.togglePin(note) },
                            onArchive = { viewModel.archive(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = note.title.ifBlank { "یادداشت بدون عنوان" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (note.isPinned) Icon(Icons.Default.PushPin, "سنجاق", tint = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "بیشتر") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (note.isPinned) "برداشتن سنجاق" else "سنجاق کردن") },
                        onClick = { menu = false; onPin() }
                    )
                    DropdownMenuItem(
                        text = { Text("بایگانی") },
                        onClick = { menu = false; onArchive() }
                    )
                }
            }
            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(note.content.take(180), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerScreen(viewModel: NoteViewModel, onBack: () -> Unit) {
    val blocks by viewModel.observePlannedBlocks().collectAsState(initial = emptyList())
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("برنامه من") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") } }
            )
        }
    ) { padding ->
        if (blocks.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("هنوز کاری برنامه‌ریزی نشده.", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("در یک چک‌لیست، زمان انجام کار را مشخص کن.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(blocks, key = { it.id }) { block ->
                    PlannedBlockCard(block, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PlannedBlockCard(block: NoteBlockEntity, viewModel: NoteViewModel) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Checkbox(
                checked = block.checked,
                onCheckedChange = {
                    val updated = block.copy(
                        checked = it,
                        completedAt = if (it) System.currentTimeMillis() else null
                    )
                    viewModel.updateBlock(updated)
                    if (it) viewModel.cancelReminder(block.id)
                }
            )
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(block.content.ifBlank { "کار بدون عنوان" }, style = MaterialTheme.typography.titleMedium)
                block.dueAt?.let {
                    Text("انجام: " + PersianFormat.jalaliDateTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                block.reminderAt?.let {
                    Text("یادآوری: " + PersianFormat.jalaliDateTime(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
    noteId: Long,
    viewModel: NoteViewModel,
    onClose: () -> Unit,
    onReminderScheduled: () -> Unit
) {
    var note by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var loaded by remember(noteId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val blocks by viewModel.observeBlocks(noteId).collectAsState(initial = emptyList())
    val attachmentViewModel: AttachmentViewModel = viewModel()
    val attachments by attachmentViewModel.observe(noteId).collectAsState(initial = emptyList())
    val isRecording by attachmentViewModel.isRecording.collectAsState()
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { attachmentViewModel.addFromUri(noteId, it) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { attachmentViewModel.addFromUri(noteId, it) }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) attachmentViewModel.startVoiceRecording(noteId)
    }

    LaunchedEffect(noteId) {
        viewModel.getNote(noteId) { note = it; loaded = true }
    }
    BackHandler { onClose() }

    if (!loaded || note == null) {
        Surface(Modifier.fillMaxSize()) {}
        return
    }

    val current = note!!
    LaunchedEffect(current.title, current.tags) {
        delay(500)
        viewModel.saveNote(current)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title.ifBlank { "یادداشت جدید" }, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "ابزارها") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("متن جدید") },
                            onClick = { menu = false; viewModel.addTextBlock(noteId) }
                        )
                        DropdownMenuItem(
                            text = { Text("چک‌لیست جدید") },
                            onClick = { menu = false; viewModel.addChecklistBlock(noteId) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (current.isPinned) "برداشتن سنجاق" else "سنجاق کردن") },
                            onClick = {
                                menu = false
                                viewModel.togglePin(current)
                                note = current.copy(isPinned = !current.isPinned)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف یادداشت") },
                            onClick = { menu = false; viewModel.deleteNote(current) { onClose() } }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { note = current.copy(title = it) },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    placeholder = { Text("عنوان") }
                )
            }
            items(blocks, key = { it.id }) { block ->
                BlockEditor(block, viewModel, onReminderScheduled)
            }
            item {
                OutlinedTextField(
                    value = current.tags,
                    onValueChange = { note = current.copy(tags = it) },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("برچسب‌ها، با ویرگول جدا کن") }
                )
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.addTextBlock(noteId) }) { Text("+ متن") }
                    TextButton(onClick = { viewModel.addChecklistBlock(noteId) }) { Text("+ چک‌لیست") }
                    TextButton(onClick = { photoPicker.launch(ActivityResultContracts.PickVisualMedia.ImageOnly) }) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(4.dp))
                        Text("عکس")
                    }
                    TextButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(4.dp))
                        Text("فایل")
                    }
                    TextButton(onClick = {
                        if (isRecording) attachmentViewModel.stopVoiceRecording()
                        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRecording) "توقف ضبط" else "صدا")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("بستن") }
                }

                if (attachments.isNotEmpty()) {
                    Text("پیوست‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    attachments.forEach { attachment ->
                        when {
                            attachment.mimeType.startsWith("image/") ->
                                ImageAttachmentCard(attachment.localPath, attachment.fileName, attachment.sizeBytes) {
                                    attachmentViewModel.delete(attachment)
                                }
                            attachment.mimeType.startsWith("audio/") ->
                                AudioAttachmentCard(attachment.localPath, attachment.fileName, attachment.sizeBytes) {
                                    attachmentViewModel.delete(attachment)
                                }
                            else ->
                                AttachmentCard(attachment.fileName, attachment.sizeBytes) {
                                    attachmentViewModel.delete(attachment)
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentCard(
    path: String,
    fileName: String,
    sizeBytes: Long,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = File(path),
                contentDescription = fileName,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(10.dp))
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(fileName, maxLines = 2)
                    Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف عکس") }
            }
        }
    }
}

@Composable
private fun AudioAttachmentCard(
    path: String,
    fileName: String,
    sizeBytes: Long,
    onDelete: () -> Unit
) {
    var playing by remember(path) { mutableStateOf(false) }
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(path) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(if (playing) Icons.Default.VolumeUp else Icons.Default.Mic, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 2)
                Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = {
                if (playing) {
                    player?.stop()
                    player?.release()
                    player = null
                    playing = false
                } else {
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(path)
                            prepare()
                            setOnCompletionListener {
                                playing = false
                                release()
                                player = null
                            }
                            start()
                            player = this
                            playing = true
                        }
                    }
                }
            }) {
                Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "پخش/توقف")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف صدا")
            }
        }
    }
}

@Composable
private fun AttachmentCard(fileName: String, sizeBytes: Long, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 2)
                Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف پیوست") }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return PersianFormat.digits(bytes) + " بایت"
    if (bytes < 1024 * 1024) return PersianFormat.digits(bytes / 1024) + " کیلوبایت"
    return PersianFormat.digits(bytes / (1024 * 1024)) + " مگابایت"
}

@Composable
private fun BlockEditor(
    block: NoteBlockEntity,
    viewModel: NoteViewModel,
    onReminderScheduled: () -> Unit
) {
    var value by remember(block.id, block.content) { mutableStateOf(block.content) }
    var checked by remember(block.id, block.checked) { mutableStateOf(block.checked) }
    var expanded by remember(block.id) { mutableStateOf(true) }
    val context = LocalContext.current

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(if (block.type == BlockType.CHECKLIST.name) "چک‌لیست" else "متن", Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Default.Edit, "باز و بسته") }
                IconButton(onClick = { viewModel.deleteBlock(block) }) { Icon(Icons.Default.Delete, "حذف") }
            }

            if (expanded) {
                if (block.type == BlockType.CHECKLIST.name) {
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                val updated = block.copy(
                                    checked = it,
                                    completedAt = if (it) System.currentTimeMillis() else null
                                )
                                viewModel.updateBlock(updated)
                                if (it) viewModel.cancelReminder(block.id)
                            }
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                                viewModel.updateBlock(block.copy(content = it))
                            },
                            Modifier.weight(1f),
                            placeholder = { Text("یک کار بنویس…") }
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            pickJalaliDateTime(context, block.dueAt ?: System.currentTimeMillis()) {
                                if (it > System.currentTimeMillis()) {
                                    viewModel.updateBlock(block.copy(dueAt = it, reminderAt = it))
                                    onReminderScheduled()
                                }
                            }
                        }) {
                            Icon(Icons.Default.CalendarMonth, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (block.dueAt == null) "برنامه‌ریزی" else PersianFormat.jalaliDateTime(block.dueAt))
                        }
                        if (block.dueAt != null) {
                            TextButton(onClick = {
                                viewModel.updateBlock(block.copy(dueAt = null, reminderAt = null))
                                viewModel.cancelReminder(block.id)
                            }) { Text("پاک کردن") }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            viewModel.updateBlock(block.copy(content = it))
                        },
                        Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("اینجا بنویس…") }
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val transactions by viewModel.transactions.collectAsState(initial = emptyList())
    val income by viewModel.income.collectAsState(initial = 0L)
    val expense by viewModel.expense.collectAsState(initial = 0L)
    var showAdd by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دخل و هزینه") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "تراکنش جدید") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "تراکنش جدید") } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("مانده", style = MaterialTheme.typography.labelLarge)
                        Text(PersianFormat.toman(income - expense), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("دریافت: " + PersianFormat.toman(income))
                        Text("پرداخت: " + PersianFormat.toman(expense))
                    }
                }
            }
            items(transactions, key = { it.id }) { transaction ->
                FinanceTransactionCard(transaction, viewModel)
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onSave = { title, amount, type, category, date ->
                viewModel.addTransaction(title, amount, type, category, date)
                showAdd = false
            }
        )
    }
}

@Composable
private fun FinanceTransactionCard(transaction: FinanceTransactionEntity, viewModel: FinanceViewModel) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(transaction.title.ifBlank { transaction.category }, fontWeight = FontWeight.Medium)
                Text(transaction.category, style = MaterialTheme.typography.labelSmall)
                Text(PersianFormat.jalaliDate(transaction.transactionAt), style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text(
                    (if (transaction.type == FinanceTransactionEntity.TYPE_INCOME) "+" else "−") +
                        PersianFormat.toman(transaction.amountToman),
                    color = if (transaction.type == FinanceTransactionEntity.TYPE_INCOME)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { viewModel.delete(transaction) }) { Text("حذف") }
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FinanceTransactionEntity.TYPE_EXPENSE) }
    var category by remember { mutableStateOf("سایر") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تراکنش جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") })
                OutlinedTextField(
                    amount,
                    { amount = it.filter { c -> c.isDigit() || c in '۰'..'۹' } },
                    Modifier.fillMaxWidth(),
                    label = { Text("مبلغ به تومان") },
                    supportingText = { Text("فقط عدد وارد کن") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == FinanceTransactionEntity.TYPE_EXPENSE, { type = FinanceTransactionEntity.TYPE_EXPENSE }, label = { Text("هزینه") })
                    FilterChip(type == FinanceTransactionEntity.TYPE_INCOME, { type = FinanceTransactionEntity.TYPE_INCOME }, label = { Text("درآمد") })
                }
                OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("دسته‌بندی") })
                TextButton(onClick = {
                    pickJalaliDateTime(context, date) { date = it }
                }) { Text("تاریخ: " + PersianFormat.jalaliDate(date)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = PersianFormat.latinDigits(amount).replace("٬", "").replace(",", "").toLongOrNull() ?: 0L
                if (normalized > 0) onSave(title, normalized, type, category.ifBlank { "سایر" }, date)
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

private fun pickJalaliDateTime(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val current = PersianFormat.currentJalali()
    val fields = arrayOf(
        android.widget.EditText(context).apply { hint = "سال شمسی"; setText(current.first.toString()) },
        android.widget.EditText(context).apply { hint = "ماه"; setText(current.second.toString()) },
        android.widget.EditText(context).apply { hint = "روز"; setText(current.third.toString()) }
    )
    val container = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(48, 8, 48, 0)
        fields.forEach { addView(it) }
    }
    android.app.AlertDialog.Builder(context)
        .setTitle("تاریخ شمسی")
        .setView(container)
        .setNegativeButton("انصراف", null)
        .setPositiveButton("تأیید") { _, _ ->
            val y = fields[0].text.toString().toIntOrNull() ?: current.first
            val m = fields[1].text.toString().toIntOrNull() ?: current.second
            val d = fields[2].text.toString().toIntOrNull() ?: current.third
            onSelected(PersianFormat.jalaliToMillis(y, m, d, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE)))
        }
        .show()
}
