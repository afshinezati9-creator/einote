package com.einote.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

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
            else -> HomeScreen(
                viewModel = viewModel,
                onCreate = { viewModel.createNote { editingId = it } },
                onOpen = { editingId = it },
                onPlanner = { plannerOpen = true }
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
    onPlanner: () -> Unit
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
                    Text("انجام: " + formatDateTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                block.reminderAt?.let {
                    Text("یادآوری: " + formatDateTime(it), style = MaterialTheme.typography.bodySmall)
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
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("بستن") }
                }
            }
        }
    }
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
                            pickDateTime(context, block.dueAt ?: System.currentTimeMillis()) {
                                if (it > System.currentTimeMillis()) {
                                    viewModel.updateBlock(block.copy(dueAt = it, reminderAt = it))
                                    onReminderScheduled()
                                }
                            }
                        }) {
                            Icon(Icons.Default.CalendarMonth, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (block.dueAt == null) "برنامه‌ریزی" else formatDateTime(block.dueAt))
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

private fun pickDateTime(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selected = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSelected(selected.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
