@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lawyerup.app

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lawyerup.app.data.local.*
import com.lawyerup.app.ui.theme.LawyerUpTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var db: PracticeDatabase
    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = PracticeDatabase.get(this)
        if (Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LawyerUpTheme {
                Assistant(db.dao())
            }
        }
    }
}

@Composable
fun Assistant(dao: PracticeDao) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("خانه", "پرونده‌ها", "مواعد", "پیش‌نویس‌ها")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("دستیار حقوقی LawyerUp") })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = i == tab,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                imageVector = when (i) {
                                    0 -> Icons.Default.Home
                                    1 -> Icons.Default.Folder
                                    2 -> Icons.Default.Event
                                    else -> Icons.Default.Description
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> Home(dao)
                1 -> Cases(dao)
                2 -> Deadlines(dao)
                3 -> Drafts(dao)
            }
        }
    }
}

@Composable
fun Home(dao: PracticeDao) {
    val cases by dao.cases().collectAsState(emptyList())
    val deadlines by dao.deadlines().collectAsState(emptyList())

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("نمای کلی", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text("پرونده‌ها: ${cases.size}   |   مواعد باز: ${deadlines.count { !it.completed }}")
        }
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("محاسبه‌گر موعد")
                    Text("تاریخ‌ها تقریبی و برای نظم کاری هستند؛ مقررات دادگاه، تعطیلات، شیوه ابلاغ و قانون جاری باید توسط وکیل بررسی شود.")
                }
            }
        }
        item { PortalCard() }
    }
}

@Composable
fun PortalCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("پرتال قضایی")
            Text("ورود و کار با ثنا فقط با اقدام مستقیم شما و مرورگر خارجی انجام می‌شود. اطلاعات ورود در برنامه ذخیره نمی‌شود.")
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://adliran.ir"))
                    )
                }
            ) {
                Text("بازکردن پرتال در مرورگر")
            }
        }
    }
}

@Composable
fun Cases(dao: PracticeDao) {
    val cases by dao.cases().collectAsState(emptyList())
    var show by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("پرونده‌ها", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { show = true }) { Text("پرونده جدید") }
        }

        if (cases.isEmpty()) {
            Text("هنوز پرونده‌ای ثبت نشده است.", Modifier.padding(top = 24.dp))
        }

        LazyColumn {
            items(cases) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.title)
                        Text("موکل: ${item.client}")
                        Text("مرجع: ${item.court}")
                        Row {
                            TextButton(onClick = {}) { Text("ویرایش") }
                            TextButton(onClick = {}) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }

    if (show) CaseDialog(dao) { show = false }
}

@Composable
fun CaseDialog(dao: PracticeDao, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var court by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = close,
        title = { Text("پرونده جدید") },
        text = {
            Column {
                Field("عنوان پرونده", title) { title = it }
                Field("موکل", client) { client = it }
                Field("شعبه / مرجع", court) { court = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        scope.launch {
                            dao.addCase(
                                PracticeCase(
                                    title = title,
                                    client = client,
                                    court = court
                                )
                            )
                            close()
                        }
                    }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("انصراف") }
        }
    )
}

@Composable
fun Deadlines(dao: PracticeDao) {
    val deadlines by dao.deadlines().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("مواعد", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { show = true }) { Text("موعد جدید") }
        }

        Text("مبدأ محاسبه: روز بعد از تاریخ محرک؛ سررسید قابل ویرایش است.")

        if (deadlines.isEmpty()) {
            Text("موعدی ثبت نشده است.", Modifier.padding(top = 20.dp))
        }

        LazyColumn {
            items(deadlines) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title)
                            Text("سررسید: ${item.dueDate}")
                            Text(if (item.completed) "انجام شد" else "باز")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    dao.updateDeadline(item.copy(completed = !item.completed))
                                }
                            }
                        ) {
                            Text(if (item.completed) "بازکردن" else "انجام شد")
                        }
                    }
                }
            }
        }
    }

    if (show) DeadlineDialog(dao) { show = false }
}

@Composable
fun DeadlineDialog(dao: PracticeDao, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("2024-01-01") }
    var due by remember { mutableStateOf("2024-01-04") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = close,
        title = { Text("ثبت موعد") },
        text = {
            Column {
                Field("عنوان", title) { title = it }
                Field("تاریخ محرک YYYY-MM-DD", trigger) { trigger = it }
                Field("سررسید YYYY-MM-DD", due) { due = it }
                Text("این محاسبه راهنمای کاری است و جایگزین بررسی وکیل نیست.")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        scope.launch {
                            dao.addDeadline(
                                Deadline(
                                    caseId = 0,
                                    title = title,
                                    triggerDate = trigger,
                                    dueDate = due
                                )
                            )
                            close()
                        }
                    }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("انصراف") }
        }
    )
}

@Composable
fun Drafts(dao: PracticeDao) {
    val drafts by dao.drafts().collectAsState(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("پیش‌نویس‌ها", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { show = true }) { Text("پیش‌نویس جدید") }
        }

        LazyColumn {
            items(drafts) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text(item.title, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, item.body)
                                        },
                                        "اشتراک‌گذاری پیش‌نویس"
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "اشتراک")
                        }
                    }
                }
            }
        }
    }

    if (show) DraftDialog(dao) { show = false }
}

@Composable
fun DraftDialog(dao: PracticeDao, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = close,
        title = { Text("پیش‌نویس") },
        text = {
            Column {
                Field("عنوان", title) { title = it }
                Field("متن", body) { body = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        scope.launch {
                            dao.addDraft(
                                Draft(
                                    title = title,
                                    body = body,
                                    caseId = null
                                )
                            )
                            close()
                        }
                    }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("انصراف") }
        }
    )
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = label != "متن"
    )
}