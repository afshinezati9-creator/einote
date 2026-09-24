package com.einote.app

import android.Manifest
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.einote.app.security.SecurityManager
import com.einote.app.ui.SecurityViewModel
import com.einote.app.ui.SettingsViewModel
import com.einote.app.settings.SettingsManager
import com.einote.app.ui.EiNoteTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einote.app.data.BlockType
import com.einote.app.data.NoteBlockEntity
import com.einote.app.data.NoteEntity
import com.einote.app.ui.NoteViewModel
import com.einote.app.ui.FinanceViewModel
import com.einote.app.ui.AttachmentViewModel
import com.einote.app.ui.BackupViewModel
import com.einote.app.data.FinanceTransactionEntity
import com.einote.app.util.PersianFormat
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EiNoteApp() }
    }
}

@Composable
fun EiNoteApp(viewModel: NoteViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()
    val securityViewModel: SecurityViewModel = viewModel()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val accent by settingsViewModel.accent.collectAsState()
    val textScale by settingsViewModel.textScale.collectAsState()
    val editorMode by settingsViewModel.editorMode.collectAsState()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var plannerOpen by remember { mutableStateOf(false) }
    var financeOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var securityOpen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var backgroundAt by remember { mutableLongStateOf(0L) }
    val backupViewModel: BackupViewModel = viewModel()
    val financeViewModel: FinanceViewModel = viewModel()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (SecurityManager(context).isLockEnabled() && SecurityManager(context).hasPin()) {
            locked = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (SecurityManager(context).isLockEnabled()) {
                    backgroundAt = System.currentTimeMillis()
                }
            } else if (event == Lifecycle.Event.ON_START && backgroundAt > 0L) {
                val minutes = securityViewModel.autoLockMinutes.value
                if (System.currentTimeMillis() - backgroundAt >= minutes * 60_000L &&
                    SecurityManager(context).isLockEnabled() &&
                    SecurityManager(context).hasPin()
                ) {
                    locked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && settingsViewModel.notifications.value) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    EiNoteTheme(themeMode, accent, textScale) {
        when {
            locked -> LockScreen(
                viewModel = securityViewModel,
                onUnlocked = { locked = false },
                onBiometric = {
                    val activity = context as? FragmentActivity
                    if (activity != null) showBiometric(activity) { locked = false }
                }
            )
            editingId != null -> NoteEditor(
                noteId = editingId!!,
                viewModel = viewModel,
                onClose = { editingId = null },
                onReminderScheduled = ::askNotificationPermission,
                editorMode = editorMode
            )
            plannerOpen -> PlannerScreen(viewModel, onBack = { plannerOpen = false })
            financeOpen -> FinanceScreen(financeViewModel, onBack = { financeOpen = false })
            backupOpen -> BackupScreen(backupViewModel, onBack = { backupOpen = false })
            securityOpen -> SecurityScreen(securityViewModel, onBack = { securityOpen = false })
            settingsOpen -> SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { settingsOpen = false }
            )
            else -> HomeScreen(
                viewModel = viewModel,
                onCreate = { viewModel.createNote { editingId = it } },
                onOpen = { editingId = it },
                onPlanner = { plannerOpen = true },
                onFinance = { financeOpen = true },
                onBackup = { backupOpen = true },
                onSecurity = { securityOpen = true },
                onSettings = { settingsOpen = true }
            )
        }
    }
}

private fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val manager = BiometricManager.from(activity)
    val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن ای‌نوت")
            .setSubtitle("برای ادامه، هویتت را تأیید کن.")
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}

@Composable
private fun LockScreen(
    viewModel: SecurityViewModel,
    onUnlocked: () -> Unit,
    onBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val canBiometric = remember {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("ای‌نوت قفل است", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("رمز ورودت را وارد کن.")
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("رمز") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (viewModel.verify(pin)) { pin = ""; onUnlocked() } },
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth()
            ) { Text("باز کردن") }
            if (canBiometric) {
                TextButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن با اثر انگشت / قفل دستگاه")
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accent by viewModel.accent.collectAsState()
    val textScale by viewModel.textScale.collectAsState()
    val pinnedFirst by viewModel.pinnedFirst.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val compactBlocks by viewModel.compactBlocks.collectAsState()
    val editorMode by viewModel.editorMode.collectAsState()

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("ظاهر برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("حالت نمایش", fontWeight = FontWeight.Medium)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "system" to "سیستم",
                                "light" to "روشن",
                                "dark" to "تاریک"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = themeMode == value,
                                    onClick = { viewModel.setThemeMode(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("رنگ تأکیدی", fontWeight = FontWeight.Medium)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "blue" to "آبی",
                                "purple" to "بنفش",
                                "green" to "سبز",
                                "orange" to "نارنجی",
                                "pink" to "صورتی"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = accent == value,
                                    onClick = { viewModel.setAccent(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("اندازه متن", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.9f to "کوچک", 1f to "معمولی", 1.1f to "بزرگ", 1.2f to "خیلی بزرگ").forEach { (value, label) ->
                                FilterChip(
                                    selected = textScale == value,
                                    onClick = { viewModel.setTextScale(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("صفحه اصلی و یادداشت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = "یادداشت‌های سنجاق‌شده اول نمایش داده شوند",
                            checked = pinnedFirst,
                            onCheckedChange = viewModel::setPinnedFirst
                        )
                        SettingSwitchRow(
                            title = "نمایش یادداشت‌های بایگانی‌شده",
                            checked = showArchived,
                            onCheckedChange = viewModel::setShowArchived
                        )
                        SettingSwitchRow(
                            title = "بلوک‌های فشرده",
                            checked = compactBlocks,
                            onCheckedChange = viewModel::setCompactBlocks
                        )
                        Text("حالت ویرایش", fontWeight = FontWeight.Medium)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                SettingsManager.EDITOR_STANDARD to "استاندارد",
                                SettingsManager.EDITOR_COMPACT to "فشرده",
                                SettingsManager.EDITOR_FOCUS to "تمرکز"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = editorMode == value,
                                    onClick = { viewModel.setEditorMode(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("اعلان‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    SettingSwitchRow(
                        title = "اعلان یادآوری‌ها",
                        checked = notifications,
                        onCheckedChange = viewModel::setNotifications
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("فونت", fontWeight = FontWeight.Medium)
                        Text(
                            "اندازه متن، حالت نمایش و رنگ تأکیدی از همین بخش قابل شخصی‌سازی است.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityScreen(viewModel: SecurityViewModel, onBack: () -> Unit) {
    val enabled by viewModel.enabled.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val minutes by viewModel.autoLockMinutes.collectAsState()
    val message by viewModel.message.collectAsState()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var disablePin by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(!hasPin) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("امنیت و قفل") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قفل برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (enabled) "قفل فعال است و پس از مدتی دور بودن از برنامه، ای‌نوت دوباره رمز می‌خواهد." else "با فعال‌کردن قفل، اطلاعات دفتر در برابر دسترسی اتفاقی محافظت می‌شود.")
                }
            }

            if (!enabled || !hasPin) {
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("رمز جدید") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("تکرار رمز") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Button(
                    onClick = { if (viewModel.savePin(pin, confirm)) { pin = ""; confirm = "" } },
                    enabled = pin.length >= 4 && confirm.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("فعال‌کردن قفل") }
            } else {
                Text("قفل فعال است")
                Text("قفل خودکار بعد از $minutes دقیقه")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15, 30).forEach { value ->
                        FilterChip(selected = minutes == value, onClick = { viewModel.setAutoLock(value) }, label = { Text("$value دقیقه") })
                    }
                }
                OutlinedTextField(
                    value = disablePin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) disablePin = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("رمز برای غیرفعال‌کردن") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedButton(
                    onClick = { if (viewModel.disable(disablePin)) disablePin = "" },
                    enabled = disablePin.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("غیرفعال‌کردن قفل") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: NoteViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit,
    onPlanner: () -> Unit, onFinance: () -> Unit, onBackup: () -> Unit,
    onSecurity: () -> Unit, onSettings: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val financeViewModel: FinanceViewModel = viewModel()
    val transactions by financeViewModel.transactions.collectAsState(initial = emptyList())
    val income by financeViewModel.income.collectAsState(initial = 0L)
    val expense by financeViewModel.expense.collectAsState(initial = 0L)
    val plannedBlocks by viewModel.observePlannedBlocks().collectAsState(initial = emptyList())
    val query by viewModel.searchQuery.collectAsState()
    val pinnedFirst by settingsViewModel.pinnedFirst.collectAsState()
    val showArchived by settingsViewModel.showArchived.collectAsState()
    val pinnedOnly by viewModel.pinnedOnlyFilter.collectAsState()
    val archivedOnly by viewModel.archivedOnlyFilter.collectAsState()
    val selectedTag by viewModel.selectedTagFilter.collectAsState()
    val sort by viewModel.sortFilter.collectAsState()
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.setSpace("WRITING") }

    val allTags = remember(notes) {
        notes.flatMap { it.tags.split(',', '،').map { tag -> tag.trim() }.filter { it.isNotBlank() } }
            .distinctBy { it.replace('ي','ی').replace('ك','ک') }.sorted()
    }
    val visibleNotes = remember(notes, pinnedFirst, showArchived, pinnedOnly, archivedOnly, selectedTag, sort) {
        notes.filter { showArchived || !it.isArchived }
            .filter { !pinnedOnly || it.isPinned }
            .filter { !archivedOnly || it.isArchived }
            .filter { selectedTag == null || it.tags.split(',', '،').any { t -> t.trim().replace('ي','ی').replace('ك','ک') == selectedTag } }
            .let { list ->
                when (sort) {
                    NoteViewModel.SORT_CREATED -> list.sortedByDescending { it.createdAt }
                    NoteViewModel.SORT_TITLE -> list.sortedBy { it.title.trim().ifBlank { "یادداشت بدون عنوان" } }
                    else -> if (pinnedFirst) list.sortedWith(compareByDescending<NoteEntity>{ it.isPinned }.thenByDescending { it.updatedAt })
                    else list.sortedByDescending { it.updatedAt }
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = searchOpen && tab == 0,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "home_top_bar"
            ) { searching ->
                if (searching) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = query,
                                onValueChange = viewModel::setSearchQuery,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("جستجوی یادداشت…") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.setSearchQuery("")
                                        searchOpen = false
                                    }) { Icon(Icons.Default.Close, "بستن جستجو") }
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        },
                        navigationIcon = {}
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text("eiNote", fontWeight = FontWeight.Bold)
                                Text(
                                    when (tab) { 1 -> "حساب‌کتاب شخصی"; 2 -> "برنامه‌ریزی شخصی"; else -> "دفتر شخصی" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            if (isPhone) IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.Menu, "منوی بیشتر") }
                        },
                        actions = {
                            if (tab == 0) {
                                IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "جستجو") }
                                IconButton(onClick = { filtersOpen = true }) { Icon(Icons.Default.FilterList, "فیلترها") }
                            }
                            if (!isPhone) {
                                IconButton(onClick = onBackup) { Icon(Icons.Default.SettingsBackupRestore, "پشتیبان") }
                                IconButton(onClick = onSecurity) { Icon(Icons.Default.Lock, "امنیت") }
                                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "تنظیمات") }
                            } else {
                                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                    DropdownMenuItem(text = { Text("پشتیبان") }, leadingIcon = { Icon(Icons.Default.SettingsBackupRestore, null) }, onClick = { moreOpen = false; onBackup() })
                                    DropdownMenuItem(text = { Text("امنیت") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, onClick = { moreOpen = false; onSecurity() })
                                    DropdownMenuItem(text = { Text("تنظیمات") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { moreOpen = false; onSettings() })
                                }
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                0 -> FloatingActionButton(onClick = { viewModel.setSpace("WRITING"); onCreate() }) { Icon(Icons.Default.Edit, "یادداشت جدید") }
                1 -> FloatingActionButton(onClick = onFinance) { Icon(Icons.Default.Add, "تراکنش جدید") }
                else -> FloatingActionButton(onClick = onPlanner) { Icon(Icons.Default.AddTask, "کار جدید") }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = if (isPhone) 18.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    when (tab) { 1 -> "حساب‌کتاب من"; 2 -> "برنامه من"; else -> "دفتر من" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    when (tab) {
                        1 -> "پول‌هایت را ساده و روشن دنبال کن."
                        2 -> "کارها و برنامه‌های روزت را یک‌جا ببین."
                        else -> "هر چیزی که می‌خواهی، همین‌جا بنویس."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { EiNoteSectionTabs(tab) { tab = it } }

            when (tab) {
                0 -> {
                    if (selectedTag != null) item {
                        AssistChip(onClick = { viewModel.setSelectedTag(null) }, label = { Text("#$selectedTag") }, trailingIcon = { Icon(Icons.Default.Close, "حذف") })
                    }
                    if (visibleNotes.isEmpty()) {
                        item { NotesEmptyState { viewModel.setSpace("WRITING"); onCreate() } }
                    } else {
                        item { Text("یادداشت‌های اخیر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        items(visibleNotes.take(12), key = { it.id }) { note ->
                            NoteRow(note, { onOpen(note.id) }, { viewModel.togglePin(note) }, { viewModel.archive(note) }, { viewModel.setSelectedTag(it) })
                        }
                    }
                }
                1 -> {
                    item { FinanceHero(income, expense, income - expense) }
                    item { Text("آخرین تراکنش‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (transactions.isEmpty()) item { FinanceEmptyState(onFinance) }
                    else {
                        items(transactions.take(5), key = { it.id }) { FinanceDashboardRow(it) }
                        item { TextButton(onClick = onFinance, modifier = Modifier.fillMaxWidth()) { Text("مشاهده همه و ثبت تراکنش"); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ArrowBack, null) } }
                    }
                }
                else -> {
                    item { PlannerHero(plannedBlocks.size, plannedBlocks.count { it.checked }) }
                    item { Text("کارهای پیش‌رو", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (plannedBlocks.isEmpty()) item { PlannerEmptyState(onPlanner) }
                    else {
                        items(plannedBlocks.take(6), key = { it.id }) { block -> PlannerDashboardRow(block, viewModel) }
                        item { TextButton(onClick = onPlanner, modifier = Modifier.fillMaxWidth()) { Text("مشاهده برنامه کامل و مدیریت کارها"); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ArrowBack, null) } }
                    }
                }
            }
            item { Spacer(Modifier.height(55.dp)) }
        }
    }

    if (filtersOpen) {
        NoteFiltersDialog(
            pinnedOnly = pinnedOnly, archivedOnly = archivedOnly, selectedTag = selectedTag,
            sort = sort, tags = allTags,
            onPinnedOnly = viewModel::setPinnedOnly, onArchivedOnly = viewModel::setArchivedOnly,
            onTag = viewModel::setSelectedTag, onSort = viewModel::setSort,
            onClear = viewModel::clearFilters, onDismiss = { filtersOpen = false }
        )
    }
}

@Composable
private fun EiNoteSectionTabs(selected: Int, onSelected: (Int) -> Unit) {
    val items = listOf(
        "یادداشت من" to Icons.Default.EditNote,
        "حساب‌کتاب" to Icons.Default.AccountBalanceWallet,
        "برنامه‌ریزی" to Icons.Default.CalendarMonth
    )
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    ) {
        Row(Modifier.fillMaxWidth().padding(5.dp)) {
            items.forEachIndexed { index, item ->
                val active = selected == index
                val elevation by animateDpAsState(if (active) 3.dp else 0.dp, label = "tab_elevation")
                val iconScale by animateFloatAsState(if (active) 1.08f else 1f, label = "tab_icon_scale")
                Surface(
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                    tonalElevation = elevation
                ) {
                    Row(
                        Modifier.padding(vertical = 11.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            item.second,
                            null,
                            Modifier.size(19.dp).graphicsLayer(scaleX = iconScale, scaleY = iconScale),
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            item.first,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesEmptyState(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Surface(Modifier.size(96.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(Icons.Default.AutoStories, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("دفترت هنوز سفیده", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("هر چیزی که توی ذهنت هست، از یک جمله شروع می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onCreate, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(7.dp)); Text("شروع یک یادداشت") }
    }
}

@Composable
private fun FinanceHero(income: Long, expense: Long, balance: Long) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("مانده", style = MaterialTheme.typography.labelLarge)
                    Text(PersianFormat.toman(balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceMiniMetric("درآمد", income, true, Modifier.weight(1f))
                FinanceMiniMetric("هزینه", expense, false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FinanceMiniMetric(label: String, amount: Long, positive: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(PersianFormat.toman(amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FinanceDashboardRow(transaction: FinanceTransactionEntity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(if (transaction.type == FinanceTransactionEntity.TYPE_INCOME) Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, Modifier.padding(9.dp), tint = if (transaction.type == FinanceTransactionEntity.TYPE_INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.title.ifBlank { transaction.category }, fontWeight = FontWeight.SemiBold)
            Text(transaction.category + " • " + PersianFormat.jalaliDate(transaction.transactionAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text((if (transaction.type == FinanceTransactionEntity.TYPE_INCOME) "+" else "−") + PersianFormat.toman(transaction.amountToman), fontWeight = FontWeight.Bold, color = if (transaction.type == FinanceTransactionEntity.TYPE_INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FinanceEmptyState(onOpen: () -> Unit) {
    SimpleSectionEmptyState(Icons.Default.AccountBalanceWallet, "حساب‌کتابت هنوز شروع نشده", "اولین درآمد یا هزینه‌ات را ثبت کن.", "ثبت تراکنش", onOpen)
}

@Composable
private fun PlannerHero(total: Int, completed: Int) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("برنامه امروز", style = MaterialTheme.typography.labelLarge)
                    Text(PersianFormat.digits(completed) + " از " + PersianFormat.digits(total) + " کار انجام شده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.secondary)
                }
            }
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlannerDashboardRow(block: NoteBlockEntity, viewModel: NoteViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = block.checked, onCheckedChange = {
            viewModel.updateBlock(block.copy(checked = it, completedAt = if (it) System.currentTimeMillis() else null))
            if (it) viewModel.cancelReminder(block.id)
        })
        Column(Modifier.weight(1f)) {
            Text(block.content.ifBlank { "کار بدون عنوان" }, fontWeight = FontWeight.SemiBold)
            block.dueAt?.let { Text("موعد: " + PersianFormat.jalaliDateTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun PlannerEmptyState(onOpen: () -> Unit) {
    SimpleSectionEmptyState(Icons.Default.CalendarMonth, "برنامه‌ات هنوز خالی است", "یک کار کوچک اضافه کن و از همین امروز شروع کن.", "رفتن به برنامه", onOpen)
}

@Composable
private fun SimpleSectionEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, action: String, onAction: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Surface(Modifier.size(72.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onOpen: () -> Unit, onPin: () -> Unit, onArchive: () -> Unit, onTagClick: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        Surface(Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(if (note.isPinned) Icons.Default.PushPin else Icons.Default.Notes, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(note.title.ifBlank { "یادداشت بدون عنوان" }, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                if (note.isPinned) Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "بیشتر", Modifier.size(19.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (note.isPinned) "برداشتن سنجاق" else "مهم کردن") }, onClick = { menu = false; onPin() })
                        DropdownMenuItem(text = { Text(if (note.isArchived) "از بایگانی خارج کن" else "بایگانی") }, onClick = { menu = false; onArchive() })
                    }
                }
            }
            Text(if (note.content.isNotBlank()) note.content.take(150) else "یادداشت خالی", maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val tags = note.tags.split(',', '،').map { it.trim() }.filter { it.isNotBlank() }.take(4)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 59.dp))
}

@Composable
private fun NoteSpaceAndColorRow(
    current: NoteEntity,
    onSpace: (String) -> Unit,
    onColor: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("فضای یادداشت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("WRITING" to "نوشتن", "PLANNING" to "برنامه‌ریزی", "FINANCE" to "حساب‌کتاب").forEach { (value, label) ->
                FilterChip(selected = current.space == value, onClick = { onSpace(value) }, label = { Text(label) })
            }
        }
        Text("رنگ دفتر", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("default" to MaterialTheme.colorScheme.primary, "purple" to Color(0xFF7C3AED), "green" to Color(0xFF059669), "orange" to Color(0xFFF59E0B), "pink" to Color(0xFFDB2777)).forEach { (value, color) ->
                Box(
                    Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color).clickable { onColor(value) }
                )
            }
        }
    }
}

@Composable
private fun NoteFiltersDialog(
    pinnedOnly:Boolean, archivedOnly:Boolean, selectedTag:String?, sort:String, tags:List<String>,
    onPinnedOnly:(Boolean)->Unit, onArchivedOnly:(Boolean)->Unit, onTag:(String?)->Unit, onSort:(String)->Unit,
    onClear:()->Unit, onDismiss:()->Unit
){
    AlertDialog(onDismissRequest=onDismiss,title={Text("جستجو و فیلتر")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("وضعیت",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            SettingSwitchRow("فقط یادداشت‌های مهم",pinnedOnly,onPinnedOnly)
            SettingSwitchRow("فقط بایگانی‌شده‌ها",archivedOnly,onArchivedOnly)
            Text("مرتب‌سازی",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(sort==NoteViewModel.SORT_UPDATED,{onSort(NoteViewModel.SORT_UPDATED)},label={Text("آخرین ویرایش")})
                FilterChip(sort==NoteViewModel.SORT_CREATED,{onSort(NoteViewModel.SORT_CREATED)},label={Text("جدیدترین")})
                FilterChip(sort==NoteViewModel.SORT_TITLE,{onSort(NoteViewModel.SORT_TITLE)},label={Text("الفبا")})
            }
            if(tags.isNotEmpty()){
                Text("برچسب‌ها",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
                    tags.take(20).forEach{tag->FilterChip(selectedTag==tag,{onTag(if(selectedTag==tag)null else tag)},label={Text("#$tag",maxLines=1)})}
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("اعمال")}},dismissButton={TextButton(onClick={onClear();onDismiss()}){Text("پاک کردن فیلترها")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val info by viewModel.info.collectAsState()
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var secureExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var secureExportDialog by remember { mutableStateOf(false) }
    var secureImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var securePassword by remember { mutableStateOf("") }
    var securePasswordConfirm by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.export(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedRestoreUri = uri
        uri?.let { viewModel.inspect(it) }
    }
    val secureExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        secureExportUri = uri
        if (uri != null) {
            securePassword = ""
            securePasswordConfirm = ""
            secureExportDialog = true
        }
    }
    val secureImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        secureImportUri = uri
        if (uri != null) securePassword = ""
    }

    BackHandler { if (!busy) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پشتیبان و بازیابی") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("دفتر همیشه قابل برگشت است", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("یک نسخه آفلاین از اطلاعاتت بساز.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "یادداشت‌ها، بلوک‌ها، برنامه‌ریزی، دخل‌وخرج، یادآوری‌ها و فایل‌های پیوست در یک پشتیبان نسخه‌دار ذخیره می‌شوند.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val current = PersianFormat.currentJalali()
                        val date = current.first.toString() + "-" + current.second + "-" + current.third
                        exportLauncher.launch("eiNote-backup-" + date + ".einote")
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Backup, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ساخت پشتیبان جدید")
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        securePassword = ""
                        securePasswordConfirm = ""
                        secureExportLauncher.launch("eiNote-secure-backup.einote.secure")
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ساخت پشتیبان رمزگذاری‌شده")
                }
            }

            item {
                TextButton(
                    onClick = {
                        securePassword = ""
                        secureImportLauncher.launch(arrayOf("application/octet-stream", "application/*"))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("بازیابی پشتیبان رمزگذاری‌شده")
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        selectedRestoreUri = null
                        viewModel.clearInfo()
                        importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "application/*"))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Restore, null)
                    Spacer(Modifier.width(8.dp))
                    Text("انتخاب پشتیبان برای بازیابی")
                }
            }

            if (busy) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("در حال بررسی پشتیبان…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            info?.let { backup ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("پشتیبان معتبر است", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                "ساختار نسخه " + backup.schemaVersion + " با ای‌نوت سازگار است و ساختار فایل قبل از بازیابی بررسی شده.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BackupStat("یادداشت", backup.notesCount, Modifier.weight(1f))
                                BackupStat("بلوک", backup.blocksCount, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BackupStat("تراکنش", backup.financeCount, Modifier.weight(1f))
                                BackupStat("پیوست", backup.attachmentsCount, Modifier.weight(1f))
                            }
                            Text(
                                "بازیابی جایگزین کامل است؛ بهتر است قبل از ادامه یک پشتیبان از دفتر فعلی هم داشته باشی.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            message?.let { msg ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(msg, Modifier.padding(14.dp))
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "بازیابی به‌صورت تراکنشی انجام می‌شود؛ اگر داده‌ها یا فایل‌های پشتیبان ناسالم باشند، عملیات متوقف می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (secureExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) secureExportDialog = false },
            title = { Text("رمزگذاری پشتیبان") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("رمز حداقل ۸ کاراکتری انتخاب کن. این رمز در ای‌نوت ذخیره نمی‌شود.")
                    OutlinedTextField(
                        value = securePassword,
                        onValueChange = { securePassword = it },
                        singleLine = true,
                        label = { Text("رمز پشتیبان") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = securePasswordConfirm,
                        onValueChange = { securePasswordConfirm = it },
                        singleLine = true,
                        label = { Text("تکرار رمز") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = securePassword.length >= 8 && securePassword == securePasswordConfirm && secureExportUri != null && !busy,
                    onClick = {
                        val uri = secureExportUri
                        secureExportDialog = false
                        if (uri != null) viewModel.exportEncrypted(uri, securePassword.toCharArray())
                        securePassword = ""
                        securePasswordConfirm = ""
                    }
                ) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { secureExportDialog = false; secureExportUri = null }) { Text("انصراف") }
            }
        )
    }

    if (secureImportUri != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) secureImportUri = null },
            title = { Text("بازیابی پشتیبان رمزگذاری‌شده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("رمز همان پشتیبانی را وارد کن. رمز روی دستگاه ذخیره نمی‌شود.")
                    OutlinedTextField(
                        value = securePassword,
                        onValueChange = { securePassword = it },
                        singleLine = true,
                        label = { Text("رمز پشتیبان") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = securePassword.length >= 8 && !busy,
                    onClick = {
                        val uri = secureImportUri
                        secureImportUri = null
                        if (uri != null) viewModel.importEncrypted(uri, securePassword.toCharArray())
                        securePassword = ""
                    }
                ) { Text("بازیابی") }
            },
            dismissButton = { TextButton(onClick = { secureImportUri = null }) { Text("انصراف") } }
        )
    }

    if (info != null && selectedRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) { selectedRestoreUri = null; viewModel.clearInfo() } },
            title = { Text("بازیابی این پشتیبان؟") },
            text = {
                Text(
                    "این عملیات اطلاعات فعلی دفتر را با " + info!!.notesCount + " یادداشت، " +
                        info!!.blocksCount + " بلوک و " + info!!.financeCount + " تراکنش جایگزین می‌کند. برای ادامه تأیید کن."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = selectedRestoreUri
                        selectedRestoreUri = null
                        viewModel.clearInfo()
                        if (uri != null) viewModel.import(uri)
                    }
                ) { Text("بازیابی") }
            },
            dismissButton = {
                TextButton(onClick = { selectedRestoreUri = null; viewModel.clearInfo() }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun BackupStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteCard(note:NoteEntity,onOpen:()->Unit,onPin:()->Unit,onArchive:()->Unit,onTagClick:(String)->Unit){
    var menu by remember{mutableStateOf(false)}
    Card(onClick=onOpen, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(16.dp)){
            Row(Modifier.fillMaxWidth()){
                Text(note.title.ifBlank{"یادداشت بدون عنوان"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                if(note.isPinned)Icon(Icons.Default.PushPin,"سنجاق",tint=MaterialTheme.colorScheme.primary)
                IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"بیشتر")}
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem(text={Text(if(note.isPinned)"برداشتن سنجاق" else "سنجاق کردن")},onClick={menu=false;onPin()})
                    DropdownMenuItem(text={Text(if(note.isArchived)"از بایگانی خارج کن" else "بایگانی")},onClick={menu=false;onArchive()})
                }
            }
            if(note.content.isNotBlank()){Spacer(Modifier.height(6.dp));Text(note.content.take(180))}
            val tags=note.tags.split(',', '،').map{it.trim()}.filter{it.isNotBlank()}.take(6)
            if(tags.isNotEmpty()){Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){tags.forEach{tag->AssistChip(onClick={onTagClick(tag.replace('ي','ی').replace('ك','ک'))},label={Text("#$tag")})}}}
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
    onReminderScheduled: () -> Unit,
    editorMode: String = SettingsManager.EDITOR_STANDARD
) {
    var note by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var loaded by remember(noteId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var orderedBlocks by remember(noteId) { mutableStateOf<List<NoteBlockEntity>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var hasSaved by remember { mutableStateOf(true) }

    val blocks by viewModel.observeBlocks(noteId).collectAsState(initial = emptyList())
    val attachmentViewModel: AttachmentViewModel = viewModel()
    val attachments by attachmentViewModel.observe(noteId).collectAsState(initial = emptyList())
    val isRecording by attachmentViewModel.isRecording.collectAsState()
    val recordingElapsedMs by attachmentViewModel.recordingElapsedMs.collectAsState()
    var showRecordingPanel by remember { mutableStateOf(false) }
    var recordingPaused by remember { mutableStateOf(false) }
    var mediaInsertPosition by remember { mutableIntStateOf(blocks.size) }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidth < 600
    val isTablet = screenWidth in 600..839

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { attachmentViewModel.addFromUri(noteId, it) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { attachmentViewModel.addImageFromUri(noteId, it, mediaInsertPosition) }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) attachmentViewModel.startVoiceRecording(noteId, mediaInsertPosition)
    }

    LaunchedEffect(noteId) {
        viewModel.getNote(noteId) { note = it; loaded = true }
    }
    LaunchedEffect(blocks) {
        if (!dragging) {
            orderedBlocks = blocks
            mediaInsertPosition = blocks.size
        }
    }
    BackHandler { onClose() }

    if (!loaded || note == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val current = note!!
    val focusMode = editorMode == SettingsManager.EDITOR_FOCUS
    val compactMode = editorMode == SettingsManager.EDITOR_COMPACT
    LaunchedEffect(current.title, current.tags) {
        hasSaved = false
        saving = true
        delay(450)
        viewModel.saveNote(current)
        saving = false
        hasSaved = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            current.title.ifBlank { "یادداشت جدید" },
                            maxLines = 1,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                saving -> "در حال ذخیره…"
                                hasSaved -> "ذخیره شد"
                                else -> "تغییرات ذخیره نشده"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!focusMode) {
                            Text(
                                PersianFormat.jalaliDate(current.updatedAt),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePin(current); note = current.copy(isPinned = !current.isPinned) }) {
                        Icon(
                            if (current.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                            "سنجاق"
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "گزینه‌ها")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("متن") },
                                leadingIcon = { Icon(Icons.Default.Notes, null) },
                                onClick = { menu = false; viewModel.addTextBlock(noteId) }
                            )
                            DropdownMenuItem(
                                text = { Text("چک‌لیست") },
                                leadingIcon = { Icon(Icons.Default.Checklist, null) },
                                onClick = { menu = false; viewModel.addChecklistBlock(noteId) }
                            )
                            DropdownMenuItem(
                                text = { Text("فهرست نقطه‌ای") },
                                leadingIcon = { Icon(Icons.Default.FormatListBulleted, null) },
                                onClick = { menu = false; viewModel.addBulletBlock(noteId) }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف یادداشت") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = { menu = false; viewModel.deleteNote(current) { onClose() } }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (showRecordingPanel || isRecording) {
                InlineRecordingPanel(
                    isRecording = isRecording,
                    isPaused = recordingPaused,
                    elapsedMs = recordingElapsedMs,
                    onStart = {
                        if (!isRecording) {
                            recordingPaused = false
                            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onPause = {
                        recordingPaused = !recordingPaused
                        if (recordingPaused) {
                            attachmentViewModel.pauseVoiceRecording()
                        } else {
                            attachmentViewModel.resumeVoiceRecording()
                        }
                    },
                    onStop = {
                        attachmentViewModel.stopVoiceRecording()
                        recordingPaused = false
                        showRecordingPanel = false
                    },
                    onDismiss = {
                        if (!isRecording) showRecordingPanel = false
                    }
                )
            }
            NoteComposerToolbar(
                compact = isPhone || compactMode || focusMode,
                isRecording = isRecording,
                onText = { viewModel.addTextBlockAt(noteId, orderedBlocks.size) },
                onChecklist = { viewModel.addChecklistBlockAt(noteId, orderedBlocks.size) },
                onBullet = { viewModel.addBulletBlockAt(noteId, orderedBlocks.size) },
                onPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onFile = { attachmentPicker.launch(arrayOf("*/*")) },
                onAudio = {
                    mediaInsertPosition = orderedBlocks.size
                    if (isRecording) {
                        attachmentViewModel.stopVoiceRecording()
                    } else {
                        showRecordingPanel = true
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onClose = onClose
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isPhone) 12.dp else if (isTablet) 24.dp else 18.dp, vertical = if (isPhone) 8.dp else 12.dp)
                    .then(if (!isPhone) Modifier.widthIn(max = 980.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "یادداشت",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { note = current.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    placeholder = { Text("عنوان یادداشت") },
                    leadingIcon = { Icon(Icons.Default.Title, null) },
                    shape = RoundedCornerShape(18.dp)
                )
                if (!focusMode) {
                    OutlinedTextField(
                        value = current.tags,
                        onValueChange = { note = current.copy(tags = it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("برچسب‌ها را بنویس؛ مثلاً کار، ایده، شخصی") },
                        leadingIcon = { Icon(Icons.Default.Label, null) },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }

            Surface(
                Modifier.weight(1f).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                tonalElevation = 1.dp
            ) {
                if (orderedBlocks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("از نوار پایین یک بلوک اضافه کن و شروع به نوشتن کن.")
                    }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = if (focusMode) 8.dp else 14.dp, vertical = if (compactMode || focusMode) 6.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactMode || focusMode) 5.dp else 10.dp)
                    ) {
                        items(
                            count = orderedBlocks.size * 2,
                            key = { renderIndex ->
                                if (renderIndex % 2 == 0) {
                                    "block-" + orderedBlocks[renderIndex / 2].id
                                } else {
                                    "insert-after-" + (renderIndex / 2)
                                }
                            }
                        ) { renderIndex ->
                            val index = renderIndex / 2
                            if (renderIndex % 2 == 0) {
                                val block = orderedBlocks[index]
                                DraggableBlockEditor(
                                    block = block,
                                    index = index,
                                    allBlocks = orderedBlocks,
                                    attachments = attachments,
                                    attachmentViewModel = attachmentViewModel,
                                    viewModel = viewModel,
                                    onReminderScheduled = onReminderScheduled,
                                    onDraggingChanged = { dragging = it },
                                    onOrderChanged = { orderedBlocks = it },
                                    listState = listState,
                                    compact = compactMode || focusMode
                                )
                            } else {
                                BlockInsertRow(
                                    onText = {
                                        mediaInsertPosition = index + 1
                                        viewModel.addTextBlockAt(noteId, index + 1)
                                    },
                                    onChecklist = {
                                        mediaInsertPosition = index + 1
                                        viewModel.addChecklistBlockAt(noteId, index + 1)
                                    },
                                    onBullet = {
                                        mediaInsertPosition = index + 1
                                        viewModel.addBulletBlockAt(noteId, index + 1)
                                    },
                                    onPhoto = {
                                        mediaInsertPosition = index + 1
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onAudio = {
                                        mediaInsertPosition = index + 1
                                        showRecordingPanel = true
                                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            }
                        }

                        item {
                            val audioBlockAttachmentIds = orderedBlocks
                                .filter { it.type == BlockType.AUDIO.name }
                                .mapNotNull { it.content.toLongOrNull() }
                                .toSet()
                            val mediaBlockAttachmentIds = orderedBlocks
                                .filter { it.type == BlockType.AUDIO.name || it.type == BlockType.IMAGE.name }
                                .mapNotNull { it.content.toLongOrNull() }
                                .toSet()
                            val visibleAttachments = attachments.filterNot {
                                it.id in mediaBlockAttachmentIds
                            }
                            if (visibleAttachments.isNotEmpty()) {
                                Text(
                                    "پیوست‌ها",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                visibleAttachments.forEach { attachment ->
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
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockInsertRow(
    onText: () -> Unit,
    onChecklist: () -> Unit,
    onBullet: () -> Unit,
    onPhoto: () -> Unit,
    onAudio: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Box {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.AddCircleOutline, "افزودن بلوک")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("متن") }, leadingIcon = { Icon(Icons.Default.TextFields, null) }, onClick = { expanded = false; onText() })
                DropdownMenuItem(text = { Text("چک‌لیست") }, leadingIcon = { Icon(Icons.Default.CheckBox, null) }, onClick = { expanded = false; onChecklist() })
                DropdownMenuItem(text = { Text("فهرست") }, leadingIcon = { Icon(Icons.Default.FormatListBulleted, null) }, onClick = { expanded = false; onBullet() })
                DropdownMenuItem(text = { Text("عکس") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { expanded = false; onPhoto() })
                DropdownMenuItem(text = { Text("صدا") }, leadingIcon = { Icon(Icons.Default.GraphicEq, null) }, onClick = { expanded = false; onAudio() })
            }
        }
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun InlineRecordingPanel(
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedMs: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isRecording && !isPaused) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isRecording && !isPaused) "در حال ضبط صدا" else if (isPaused) "ضبط متوقف شده — آماده ادامه" else "آماده ضبط صدا",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isRecording) {
                IconButton(onClick = onStart) {
                    Icon(Icons.Default.Mic, "شروع ضبط")
                }
            } else {
                IconButton(onClick = onPause) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (isPaused) "ادامه ضبط" else "مکث")
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.StopCircle, "توقف و ذخیره")
                }
            }
            if (!isRecording) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "بستن")
                }
            }
        }
    }
}

@Composable
private fun NoteComposerToolbar(
    compact: Boolean,
    isRecording: Boolean,
    onText: () -> Unit,
    onChecklist: () -> Unit,
    onBullet: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
    onAudio: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ComposerTool(Icons.Default.Notes, if (compact) "متن" else "متن", onText)
            ComposerTool(Icons.Default.Checklist, if (compact) "چک" else "چک‌لیست", onChecklist)
            ComposerTool(Icons.Default.FormatListBulleted, if (compact) "لیست" else "فهرست", onBullet)
            ComposerTool(Icons.Default.Photo, "عکس", onPhoto)
            ComposerTool(Icons.Default.AttachFile, if (compact) "فایل" else "فایل", onFile)
            ComposerTool(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, if (isRecording) "توقف" else "صدا", onAudio)
            Spacer(Modifier.width(if (compact) 4.dp else 12.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, "ذخیره و بستن")
            }
        }
    }
}

@Composable
private fun ComposerTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(icon, label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DraggableBlockEditor(
    block: NoteBlockEntity,
    index: Int,
    allBlocks: List<NoteBlockEntity>,
    attachments: List<com.einote.app.data.AttachmentEntity>,
    attachmentViewModel: AttachmentViewModel,
    viewModel: NoteViewModel,
    onReminderScheduled: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onOrderChanged: (List<NoteBlockEntity>) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    compact: Boolean = false
) {
    var dragOffset by remember(block.id) { mutableFloatStateOf(0f) }
    val currentIndex = remember { mutableIntStateOf(index) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = if (dragOffset != 0f) dragOffset else 0f
                shadowElevation = if (dragOffset != 0f) 18f else 0f
            },
        color = Color.Transparent
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(if (compact) 32.dp else 42.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .pointerInput(allBlocks, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                currentIndex.intValue = index
                                dragOffset = 0f
                                onDraggingChanged(true)
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                onDraggingChanged(false)
                            },
                            onDragEnd = {
                                dragOffset = 0f
                                onDraggingChanged(false)
                                onOrderChanged(allBlocks)
                                viewModel.persistBlockOrder(allBlocks)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y

                                val visible = listState.layoutInfo.visibleItemsInfo
                                val current = visible.firstOrNull { it.index == currentIndex.intValue } ?: return@detectDragGesturesAfterLongPress
                                val center = current.offset + dragOffset + current.size / 2
                                val target = visible
                                    .filter { it.index != currentIndex.intValue }
                                    .firstOrNull { center > it.offset && center < it.offset + it.size }

                                if (target != null) {
                                    val from = currentIndex.intValue
                                    val to = target.index
                                    val mutable = allBlocks.toMutableList()
                                    val item = mutable.removeAt(from)
                                    mutable.add(to, item)
                                    currentIndex.intValue = to
                                    dragOffset = 0f
                                    onOrderChanged(mutable)
                                }
                            }
                        )
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "کشیدن برای جابه‌جایی",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box(Modifier.weight(1f)) {
                StyledBlockEditor(
                    block = block,
                    viewModel = viewModel,
                    attachment = attachments.firstOrNull { it.id == block.content.toLongOrNull() },
                    onDeleteAudio = {
                        attachments.firstOrNull { it.id == block.content.toLongOrNull() }?.let { attachmentViewModel.delete(it) }
                        viewModel.deleteBlock(block)
                    },
                    onReminderScheduled = onReminderScheduled
                )
            }
        }
    }
}


@Composable
private fun StyledBlockEditor(
    block: NoteBlockEntity,
    viewModel: NoteViewModel,
    attachment: com.einote.app.data.AttachmentEntity?,
    onDeleteAudio: () -> Unit,
    onReminderScheduled: () -> Unit
) {
    var value by remember(block.id) { mutableStateOf(richTextToFieldValue(block.content)) }
    var checked by remember(block.id, block.checked) { mutableStateOf(block.checked) }
    var showTools by remember(block.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val textColor = if (block.textColor == 0L) MaterialTheme.colorScheme.onSurface else Color(block.textColor.toULong())
    val size = block.textSizeSp.coerceIn(13f, 32f)

    if (block.type == BlockType.AUDIO.name || block.type == BlockType.IMAGE.name) {
        if (attachment != null) {
            if (block.type == BlockType.AUDIO.name) AudioBlockEditor(attachment, onDeleteAudio)
            else ImageBlockEditor(attachment, onDeleteAudio)
        } else {
            Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(if (block.type == BlockType.IMAGE.name) Icons.Default.Image else Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text(if (block.type == BlockType.IMAGE.name) "فایل تصویری پیدا نشد" else "فایل صوتی پیدا نشد", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    IconButton(onClick = onDeleteAudio) { Icon(Icons.Default.DeleteOutline, "حذف بلوک") }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                when (block.type) {
                    BlockType.CHECKLIST.name -> "چک‌لیست"
                    BlockType.BULLET.name -> "فهرست"
                    else -> "متن"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showTools = !showTools }, modifier = Modifier.size(34.dp)) {
                Icon(if (showTools) Icons.Default.ExpandLess else Icons.Default.Tune, "قالب‌بندی")
            }
            IconButton(onClick = { viewModel.deleteBlock(block) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.DeleteOutline, "حذف")
            }
        }

        if (showTools) {
            RichTextToolbar(
                value = value,
                alignment = block.alignment,
                onToggleBold = {
                    value = toggleInlineStyle(value, InlineStyle.BOLD)
                    viewModel.updateBlock(block.copy(content = fieldValueToHtml(value)))
                },
                onToggleItalic = {
                    value = toggleInlineStyle(value, InlineStyle.ITALIC)
                    viewModel.updateBlock(block.copy(content = fieldValueToHtml(value)))
                },
                onToggleUnderline = {
                    value = toggleInlineStyle(value, InlineStyle.UNDERLINE)
                    viewModel.updateBlock(block.copy(content = fieldValueToHtml(value)))
                },
                onAlignment = { viewModel.updateBlock(block.copy(alignment = it)) }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("اندازه", style = MaterialTheme.typography.labelSmall)
                Slider(value = size, onValueChange = { viewModel.updateBlock(block.copy(textSizeSp = it)) }, valueRange = 13f..32f, modifier = Modifier.weight(1f))
                Text(PersianFormat.digits(size.toInt().toLong()), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.primary,
                    Color(0xFF7C3AED),
                    Color(0xFFDC2626),
                    Color(0xFF059669),
                    Color(0xFFF59E0B)
                ).forEach { color ->
                    Box(
                        Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color).clickable {
                            viewModel.updateBlock(block.copy(textColor = color.value.toLong()))
                        }
                    )
                }
            }
        }

        val editor: @Composable (Modifier) -> Unit = { modifier ->
            RichEditorField(
                value = value,
                onValueChange = {
                    value = it
                    viewModel.updateBlock(block.copy(content = fieldValueToHtml(it)))
                },
                textColor = textColor,
                size = size,
                alignment = block.alignment,
                placeholder = if (block.type == BlockType.BULLET.name) "آیتم فهرست…" else if (block.type == BlockType.CHECKLIST.name) "یک کار بنویس…" else "اینجا بنویس…",
                modifier = modifier
            )
        }

        if (block.type == BlockType.CHECKLIST.name) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        viewModel.updateBlock(block.copy(checked = it, completedAt = if (it) System.currentTimeMillis() else null))
                        if (it) viewModel.cancelReminder(block.id)
                    }
                )
                editor(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                if (block.type == BlockType.BULLET.name) {
                    Text("•", fontSize = size.sp, color = textColor, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
                }
                editor(Modifier.weight(1f))
            }
        }

        if (block.type == BlockType.CHECKLIST.name) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Text(if (block.dueAt == null) "یادآوری" else PersianFormat.jalaliDate(block.dueAt))
                }
                if (block.dueAt != null) {
                    TextButton(onClick = {
                        viewModel.updateBlock(block.copy(dueAt = null, reminderAt = null))
                        viewModel.cancelReminder(block.id)
                    }) { Text("پاک کردن") }
                }
            }
        }
    }
}

private enum class InlineStyle { BOLD, ITALIC, UNDERLINE }

@Composable
private fun RichTextToolbar(
    value: TextFieldValue,
    alignment: String,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onAlignment: (String) -> Unit
) {
    val hasSelection = value.selection.start != value.selection.end
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RichFormatButton("B", hasSelection && rangeHasInlineStyle(value.annotatedString, value.selection, InlineStyle.BOLD), onToggleBold)
        RichFormatButton("I", hasSelection && rangeHasInlineStyle(value.annotatedString, value.selection, InlineStyle.ITALIC), onToggleItalic, italic = true)
        RichFormatButton("U", hasSelection && rangeHasInlineStyle(value.annotatedString, value.selection, InlineStyle.UNDERLINE), onToggleUnderline, underline = true)
        VerticalDivider(Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
        AlignmentButton(Icons.Default.FormatAlignRight, "راست‌چین", alignment == "right") { onAlignment("right") }
        AlignmentButton(Icons.Default.FormatAlignCenter, "وسط‌چین", alignment == "center") { onAlignment("center") }
        AlignmentButton(Icons.Default.FormatAlignLeft, "چپ‌چین", alignment == "left") { onAlignment("left") }
        AlignmentButton(Icons.Default.FormatTextdirectionRToL, "خودکار", alignment == "auto") { onAlignment("auto") }
    }
}

@Composable
private fun RichFormatButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    italic: Boolean = false,
    underline: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontWeight = if (label == "B") FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
        )
    }
}

@Composable
private fun AlignmentButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
}

@Composable
private fun RichEditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textColor: Color,
    size: Float,
    alignment: String,
    placeholder: String,
    modifier: Modifier
) {
    val align = when (alignment) {
        "left" -> TextAlign.Left
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Start
    }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 86.dp),
        textStyle = TextStyle(
            color = textColor,
            fontSize = size.sp,
            lineHeight = (size * 1.55f).sp,
            textAlign = align,
            textDirection = TextDirection.Content
        ),
        decorationBox = { inner ->
            if (value.text.isBlank()) {
                Text(placeholder, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontSize = size.sp, textAlign = align)
            }
            inner()
        }
    )
}

private fun richTextToFieldValue(content: String): TextFieldValue {
    if (content.isBlank()) return TextFieldValue("")
    if (!content.trimStart().startsWith("<")) return TextFieldValue(content)
    return runCatching {
        TextFieldValue(spannedToAnnotatedString(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)))
    }.getOrElse { TextFieldValue(content) }
}

private fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString {
    val builder = AnnotatedString.Builder(spanned.toString())
    spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
        val start = spanned.getSpanStart(span).coerceAtLeast(0)
        val end = spanned.getSpanEnd(span).coerceAtMost(spanned.length)
        if (start >= end) return@forEach
        when (span) {
            is StyleSpan -> when (span.style) {
                android.graphics.Typeface.BOLD -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                android.graphics.Typeface.ITALIC -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                android.graphics.Typeface.BOLD_ITALIC -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
            }
            is UnderlineSpan -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            is StrikethroughSpan -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            is ForegroundColorSpan -> builder.addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
        }
    }
    return builder.toAnnotatedString()
}

private fun fieldValueToHtml(value: TextFieldValue): String {
    val annotated = value.annotatedString
    if (annotated.text.isBlank()) return ""
    val out = StringBuilder()
    var i = 0
    while (i < annotated.length) {
        val styles = annotated.spanStyles.filter { it.start <= i && it.end > i }.map { it.item }
        var end = i + 1
        while (end < annotated.length) {
            val next = annotated.spanStyles.filter { it.start <= end && it.end > end }.map { it.item }
            if (next != styles) break
            end++
        }
        var text = android.text.TextUtils.htmlEncode(annotated.text.substring(i, end)).replace("
", "<br>")
        val style = mergeSpanStyles(styles)
        if (style.fontWeight == FontWeight.Bold) text = "<b>$text</b>"
        if (style.fontStyle == FontStyle.Italic) text = "<i>$text</i>"
        if (style.textDecoration?.contains(TextDecoration.Underline) == true) text = "<u>$text</u>"
        if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) text = "<s>$text</s>"
        out.append(text)
        i = end
    }
    return out.toString()
}

private fun mergeSpanStyles(styles: List<SpanStyle>): SpanStyle {
    var result = SpanStyle()
    styles.forEach { result = result.merge(it) }
    return result
}

private fun rangeHasInlineStyle(annotated: AnnotatedString, selection: TextRange, style: InlineStyle): Boolean {
    val start = minOf(selection.start, selection.end).coerceIn(0, annotated.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, annotated.length)
    if (start >= end) return false
    for (i in start until end) {
        val merged = mergeSpanStyles(annotated.spanStyles.filter { it.start <= i && it.end > i }.map { it.item })
        val styled = when (style) {
            InlineStyle.BOLD -> merged.fontWeight == FontWeight.Bold
            InlineStyle.ITALIC -> merged.fontStyle == FontStyle.Italic
            InlineStyle.UNDERLINE -> merged.textDecoration?.contains(TextDecoration.Underline) == true
        }
        if (!styled) return false
    }
    return true
}

private fun toggleInlineStyle(value: TextFieldValue, style: InlineStyle): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    if (start >= end) return value
    val remove = rangeHasInlineStyle(value.annotatedString, value.selection, style)
    val builder = AnnotatedString.Builder(value.annotatedString)
    builder.addStyle(
        when (style) {
            InlineStyle.BOLD -> SpanStyle(fontWeight = if (remove) FontWeight.Normal else FontWeight.Bold)
            InlineStyle.ITALIC -> SpanStyle(fontStyle = if (remove) FontStyle.Normal else FontStyle.Italic)
            InlineStyle.UNDERLINE -> SpanStyle(textDecoration = if (remove) TextDecoration.None else TextDecoration.Underline)
        },
        start,
        end
    )
    return value.copy(annotatedString = builder.toAnnotatedString())
}

@Composable
private fun ImageBlockEditor(
    attachment: com.einote.app.data.AttachmentEntity,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = File(attachment.localPath),
                contentDescription = attachment.fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("عکس", fontWeight = FontWeight.SemiBold)
                    Text(
                        attachment.fileName,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, "حذف عکس")
                }
            }
        }
    }
}

@Composable
private fun AudioBlockEditor(
    attachment: com.einote.app.data.AttachmentEntity,
    onDelete: () -> Unit
) {
    var playing by remember(attachment.id) { mutableStateOf(false) }
    var player by remember(attachment.id) { mutableStateOf<MediaPlayer?>(null) }
    var durationMs by remember(attachment.id) { mutableIntStateOf(0) }

    DisposableEffect(attachment.id) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    null,
                    Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("یادداشت صوتی", fontWeight = FontWeight.SemiBold)
                Text(
                    formatFileSize(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (durationMs > 0) {
                    Text(
                        String.format(java.util.Locale.US, "%d:%02d", durationMs / 60000, (durationMs / 1000) % 60),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(
                onClick = {
                    if (playing) {
                        player?.pause()
                        playing = false
                    } else {
                        runCatching {
                            val next = MediaPlayer().apply {
                                setDataSource(attachment.localPath)
                                prepare()
                                durationMs = duration
                                setOnCompletionListener {
                                    playing = false
                                    release()
                                    player = null
                                }
                                start()
                            }
                            player?.release()
                            player = next
                            playing = true
                        }
                    }
                }
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "مکث صدا" else "پخش صدا"
                )
            }
            IconButton(onClick = {
                player?.stop()
                player?.release()
                player = null
                playing = false
                onDelete()
            }) {
                Icon(Icons.Default.DeleteOutline, "حذف صدا")
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = File(path),
                contentDescription = fileName,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(fileName, maxLines = 2, fontWeight = FontWeight.Medium)
                    Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "حذف عکس") }
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

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 2, fontWeight = FontWeight.Medium)
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
                Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, "پخش/توقف")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "حذف صدا") }
        }
    }
}

@Composable
private fun AttachmentCard(fileName: String, sizeBytes: Long, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 2, fontWeight = FontWeight.Medium)
                Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "حذف پیوست") }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return PersianFormat.digits(bytes) + " بایت"
    if (bytes < 1024 * 1024) return PersianFormat.digits(bytes / 1024) + " کیلوبایت"
    return PersianFormat.digits(bytes / (1024 * 1024)) + " مگابایت"
}

@OptIn(ExperimentalMaterial3Api::class)
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
        .setTitle("انتخاب تاریخ")
        .setView(container)
        .setPositiveButton("تأیید") { _, _ ->
            val year = PersianFormat.latinDigits(fields[0].text.toString()).toIntOrNull()
            val month = PersianFormat.latinDigits(fields[1].text.toString()).toIntOrNull()
            val day = PersianFormat.latinDigits(fields[2].text.toString()).toIntOrNull()
            if (year != null && month != null && day != null &&
                year in 1300..1600 && month in 1..12 && day in 1..31
            ) {
                runCatching {
                    onSelected(
                        PersianFormat.jalaliToMillis(
                            year,
                            month,
                            day,
                            initial.get(Calendar.HOUR_OF_DAY),
                            initial.get(Calendar.MINUTE)
                        )
                    )
                }
            }
        }
        .setNegativeButton("انصراف", null)
        .show()
}