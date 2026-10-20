package com.lawyerup.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lawyerup.app.data.LawRepository

@Composable
fun AuthorityDetailScreen(repository: LawRepository, id: Long) {
    val context = LocalContext.current
    var claim by remember { mutableStateOf<com.lawyerup.app.data.local.ClaimEntity?>(null) }
    LaunchedEffect(id) { claim = repository.claim(id) }
    val c = claim ?: return

    var selected by remember { mutableStateOf(setOf("عنوان", "مواد قانونی", "مهلت", "دادگاه صالح", "تعرفه")) }
    val options = listOf("عنوان", "شرح", "مواد قانونی", "مهلت", "دادگاه صالح", "تعرفه")
    val shareText = buildString {
        if ("عنوان" in selected) appendLine("عنوان دعوا: ${c.title}")
        if ("شرح" in selected) appendLine("شرح: ${c.description}")
        if ("مواد قانونی" in selected) appendLine("مواد: ${c.articles}")
        if ("مهلت" in selected) appendLine("مهلت: ${c.deadline}")
        if ("دادگاه صالح" in selected) appendLine("مرجع: ${c.authority}")
        if ("تعرفه" in selected) appendLine("تعرفه: ${c.feeRate}")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(c.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("مرجع صالح: ${c.authority}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(c.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text("مواد قانونی: ${c.articles}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("مهلت قانونی: ${c.deadline}")
        Text("تعرفه و خسارات: ${c.feeRate}")
        Spacer(Modifier.height(16.dp))
        Text("موارد برای اشتراک‌گذاری:", style = MaterialTheme.typography.titleSmall)
        for (opt in options) {
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = opt in selected, onCheckedChange = { checked ->
                    selected = if (checked) selected + opt else selected - opt
                })
                Text(opt, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری از طریق"))
        }) { Text("اشتراک‌گذاری در پیام‌رسان‌ها") }
    }
}
