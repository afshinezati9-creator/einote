package com.einote.app

import android.Manifest
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
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
import com.einote.app.security.SecurityManager
import com.einote.app.ui.SecurityViewModel
import com.einote.app.ui.SettingsViewModel
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
    val darkMode by settingsViewModel.darkMode.collectAsState()
    val accent by settingsViewModel.accent.collectAsState()
    val textScale by settingsViewModel.textScale.collectAsState()

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

    EiNoteTheme(darkMode, accent, textScale) {
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
                onReminderScheduled = ::askNotificationPermission
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
    val darkMode by viewModel.darkMode.collectAsState()
    val accent by viewModel.accent.collectAsState()
    val textScale by viewModel.textScale.collectAsState()
    val pinnedFirst by viewModel.pinnedFirst.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val compactBlocks by viewModel.compactBlocks.collectAsState()

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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("حالت تاریک", fontWeight = FontWeight.Medium)
                                Text("ظاهر آرام‌تر برای استفاده در نور کم.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = darkMode, onCheckedChange = viewModel::setDarkMode)
                        }
                        Text("رنگ تأکیدی", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "blue" to "آبی",
                                "purple" to "بنفش",
                                "green" to "سبز",
                                "orange" to "نارنجی"
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
                            "پشتیبانی RTL و فارسی فعال است. فونت فارسی Bundled در مرحله بعدی تکمیل انتشار منابع اضافه می‌شود.",
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
    val query by viewModel.searchQuery.collectAsState()
    val pinnedFirst by settingsViewModel.pinnedFirst.collectAsState()
    val showArchived by settingsViewModel.showArchived.collectAsState()
    val pinnedOnly by viewModel.pinnedOnlyFilter.collectAsState()
    val archivedOnly by viewModel.archivedOnlyFilter.collectAsState()
    val selectedTag by viewModel.selectedTagFilter.collectAsState()
    val sort by viewModel.sortFilter.collectAsState()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidth < 600
    val isTablet = screenWidth in 600..839
    var searchOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    val allTags = remember(notes) {
        notes.flatMap { it.tags.split(',', '،').map { tag -> tag.trim() }.filter { it.isNotBlank() } }
            .distinctBy { it.replace('ي','ی').replace('ك','ک') }.sorted()
    }
    val visibleNotes = remember(notes, pinnedFirst, showArchived, pinnedOnly, archivedOnly, selectedTag, sort) {
        notes.filter { showArchived || !it.isArchived }
            .filter { !pinnedOnly || it.isPinned }
            .filter { !archivedOnly || it.isArchived }
            .filter { selectedTag == null || it.tags.split(',', '،').any { t -> t.trim().replace('ي','ی').replace('ك','ک') == selectedTag } }
            .let { list -> when(sort) {
                NoteViewModel.SORT_CREATED -> list.sortedByDescending { it.createdAt }
                NoteViewModel.SORT_TITLE -> list.sortedBy { it.title.trim().ifBlank { "یادداشت بدون عنوان" } }
                else -> if(pinnedFirst) list.sortedWith(compareByDescending<NoteEntity>{it.isPinned}.thenByDescending{it.updatedAt}) else list.sortedByDescending{it.updatedAt}
            }}
    }
    val activeFilters = listOf(pinnedOnly, archivedOnly, selectedTag != null, sort != NoteViewModel.SORT_UPDATED).count { it }

    Scaffold(
        topBar = {
            if (searchOpen) TopAppBar(
                title = { OutlinedTextField(query, viewModel::setSearchQuery, Modifier.fillMaxWidth(), singleLine=true, placeholder={Text("جستجو در عنوان، متن، برچسب، کار و پیوست…")}) },
                navigationIcon = { IconButton(onClick={searchOpen=false;viewModel.setSearchQuery("")}){Icon(Icons.Default.ArrowBack,"بازگشت")} },
                actions = { IconButton(onClick={filtersOpen=true}){Icon(Icons.Default.FilterList,"فیلترها")} }
            ) else CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("eiNote", fontWeight = FontWeight.SemiBold)
                        if (!isPhone) {
                            Text(
                                "دفترچه شخصی",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isPhone) {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Default.Menu, "منوی بیشتر")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Default.Search, "جستجو")
                    }
                    IconButton(onClick = { filtersOpen = true }) {
                        Icon(Icons.Default.FilterList, "فیلترها")
                    }
                    if (!isPhone) {
                        IconButton(onClick = onPlanner) {
                            Icon(Icons.Default.CalendarMonth, "برنامه")
                        }
                        IconButton(onClick = onFinance) {
                            Icon(Icons.Default.AccountBalanceWallet, "مالی")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { moreOpen = true }) {
                                Icon(Icons.Default.MoreVert, "بیشتر")
                            }
                            DropdownMenu(
                                expanded = moreOpen,
                                onDismissRequest = { moreOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("برنامه‌ریزی") },
                                    leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                                    onClick = { moreOpen = false; onPlanner() }
                                )
                                DropdownMenuItem(
                                    text = { Text("حساب‌کتاب") },
                                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                                    onClick = { moreOpen = false; onFinance() }
                                )
                                DropdownMenuItem(
                                    text = { Text("پشتیبان") },
                                    leadingIcon = { Icon(Icons.Default.SettingsBackupRestore, null) },
                                    onClick = { moreOpen = false; onBackup() }
                                )
                                DropdownMenuItem(
                                    text = { Text("امنیت") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = { moreOpen = false; onSecurity() }
                                )
                                DropdownMenuItem(
                                    text = { Text("تنظیمات") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { moreOpen = false; onSettings() }
                                )
                            }
                        }
                    }
                    if (!isPhone) {
                        IconButton(onClick = onBackup) {
                            Icon(Icons.Default.SettingsBackupRestore, "پشتیبان")
                        }
                        IconButton(onClick = onSecurity) {
                            Icon(Icons.Default.Lock, "امنیت")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, "تنظیمات")
                        }
                    }
                }
            )
        },
        floatingActionButton={FloatingActionButton(onClick=onCreate){Icon(Icons.Default.Edit,"یادداشت جدید")}}
    ){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = if (isPhone) 14.dp else if (isTablet) 24.dp else 20.dp)){
            Spacer(Modifier.height(18.dp)); Text("دفتر من",style=MaterialTheme.typography.headlineMedium)
            Text(when {query.isNotBlank()->"نتیجه‌های جستجو";activeFilters>0->"یادداشت‌های فیلترشده";else->"هر چیزی که می‌خواهی، همین‌جا."})
            if(selectedTag!=null){Spacer(Modifier.height(8.dp));AssistChip(onClick={viewModel.setSelectedTag(null)},label={Text("#$selectedTag")},trailingIcon={Icon(Icons.Default.Close,"حذف")})}
            Spacer(Modifier.height(14.dp))
            if(visibleNotes.isEmpty()) Text(when {query.isNotBlank()->"چیزی پیدا نشد.";activeFilters>0->"یادداشتی با این فیلترها پیدا نشد.";else->"هنوز یادداشتی نداری."})
            else LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){items(visibleNotes,key={it.id}){note->
                NoteCard(note,{onOpen(note.id)},{viewModel.togglePin(note)},{viewModel.archive(note)},{viewModel.setSelectedTag(it)})
            }}
        }
    }
    if(filtersOpen) NoteFiltersDialog(pinnedOnly,archivedOnly,selectedTag,sort,allTags,viewModel::setPinnedOnly,viewModel::setArchivedOnly,viewModel::setSelectedTag,viewModel::setSort,viewModel::clearFilters,{filtersOpen=false})
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
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.export(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.import(it) }
    }
    var confirmRestore by remember { mutableStateOf(false) }

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
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پشتیبان کامل دفتر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("یادداشت‌ها، چک‌لیست‌ها، برنامه‌ریزی، یادآوری‌ها، دخل‌وخرج و همه عکس‌ها، فایل‌ها و صداها در یک فایل آفلاین ذخیره می‌شوند.")
                    Text("فرمت پشتیبان نسخه‌دار است و قبل از بازیابی، ساختار و ارتباط داده‌ها بررسی می‌شود.")
                }
            }

            Button(
                onClick = {
                    val current = PersianFormat.currentJalali()
                    val date = "${current.first}-${current.second}-${current.third}"
                    exportLauncher.launch("eiNote-backup-" + date + ".einote")
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(Modifier.width(8.dp))
                Text("ساخت پشتیبان")
            }

            OutlinedButton(
                onClick = { confirmRestore = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text("بازیابی پشتیبان")
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("در حال انجام عملیات…")
            }

            message?.let {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(it, Modifier.padding(14.dp))
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "نکته: بازیابی، دفتر فعلی را با نسخه داخل پشتیبان جایگزین می‌کند. اگر فایل خراب یا ناسازگار باشد، بازیابی متوقف می‌شود.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmRestore = false },
            title = { Text("بازیابی پشتیبان؟") },
            text = { Text("اطلاعات فعلی این دفتر با اطلاعات موجود در پشتیبان جایگزین می‌شود. قبل از ادامه مطمئن شو که پشتیبان درست را انتخاب می‌کنی.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "application/*"))
                }) { Text("ادامه") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("انصراف") }
            }
        )
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
    onReminderScheduled: () -> Unit
) {
    var note by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var loaded by remember(noteId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var orderedBlocks by remember(noteId) { mutableStateOf<List<NoteBlockEntity>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }

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
    LaunchedEffect(current.title, current.tags) {
        delay(450)
        viewModel.saveNote(current)
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
                            PersianFormat.jalaliDate(current.updatedAt),
                            style = MaterialTheme.typography.labelSmall
                        )
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
                compact = isPhone,
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
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(orderedBlocks, key = { _, item -> item.id }) { index, block ->
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
                                listState = listState
                            )
                        }

                        item(key = "insert-after-$index") {
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
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    var dragOffset by remember(block.id) { mutableFloatStateOf(0f) }
    val currentIndex = remember { mutableIntStateOf(index) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = if (dragOffset != 0f) dragOffset else 0f
                shadowElevation = if (dragOffset != 0f) 18f else 0f
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(42.dp)
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
    var value by remember(block.id, block.content) { mutableStateOf(block.content) }
    var checked by remember(block.id, block.checked) { mutableStateOf(block.checked) }
    var showTools by remember(block.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val textColor = if (block.textColor == 0L) MaterialTheme.colorScheme.onSurface else Color(block.textColor.toULong())
    val size = block.textSizeSp.coerceIn(13f, 32f)

    if (block.type == BlockType.AUDIO.name || block.type == BlockType.IMAGE.name) {
        if (attachment != null) {
            if (block.type == BlockType.AUDIO.name) {
                AudioBlockEditor(
                    attachment = attachment,
                    onDelete = onDeleteAudio
                )
            } else {
                ImageBlockEditor(
                    attachment = attachment,
                    onDelete = onDeleteAudio
                )
            }
        } else {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        if (block.type == BlockType.IMAGE.name) Icons.Default.Image else Icons.Default.GraphicEq,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (block.type == BlockType.IMAGE.name) "فایل تصویری پیدا نشد" else "فایل صوتی پیدا نشد",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                    IconButton(onClick = onDeleteAudio) {
                        Icon(Icons.Default.DeleteOutline, "حذف بلوک صوتی")
                    }
                }
            }
        }
        return
    }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                when (block.type) {
                    BlockType.CHECKLIST.name -> "چک‌لیست"
                    BlockType.BULLET.name -> "فهرست"
                    else -> "متن"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showTools = !showTools }) {
                Icon(Icons.Default.Tune, "قالب‌بندی")
            }
            IconButton(onClick = { viewModel.deleteBlock(block) }) {
                Icon(Icons.Default.DeleteOutline, "حذف")
            }
        }

        if (showTools) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("اندازه", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = size,
                    onValueChange = { viewModel.updateBlock(block.copy(textSizeSp = it)) },
                    valueRange = 13f..32f,
                    modifier = Modifier.weight(1f)
                )
                Text(PersianFormat.digits(size.toInt().toLong()), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.primary,
                    Color(0xFF7C3AED),
                    Color(0xFFDC2626),
                    Color(0xFF059669),
                    Color(0xFFF59E0B)
                ).forEach { color ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .clickable {
                                viewModel.updateBlock(block.copy(textColor = color.value.toLong()))
                            }
                    )
                }
            }
        }

        if (block.type == BlockType.CHECKLIST.name) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        viewModel.updateBlock(
                            block.copy(
                                checked = it,
                                completedAt = if (it) System.currentTimeMillis() else null
                            )
                        )
                        if (it) viewModel.cancelReminder(block.id)
                    }
                )
                BasicEditorField(
                    value = value,
                    onValueChange = {
                        value = it
                        viewModel.updateBlock(block.copy(content = it))
                    },
                    textColor = textColor,
                    size = size,
                    placeholder = "یک کار بنویس…",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                if (block.type == BlockType.BULLET.name) {
                    Text("•", fontSize = size.sp, color = textColor, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
                }
                BasicEditorField(
                    value = value,
                    onValueChange = {
                        value = it
                        viewModel.updateBlock(block.copy(content = it))
                    },
                    textColor = textColor,
                    size = size,
                    placeholder = if (block.type == BlockType.BULLET.name) "آیتم فهرست…" else "اینجا بنویس…",
                    modifier = Modifier.weight(1f)
                )
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

@Composable
private fun BasicEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    textColor: Color,
    size: Float,
    placeholder: String,
    modifier: Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = textColor,
            fontSize = size.sp,
            lineHeight = (size * 1.55f).sp
        ),
        decorationBox = { inner ->
            if (value.isBlank()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = size.sp
                )
            }
            inner()
        }
    )
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