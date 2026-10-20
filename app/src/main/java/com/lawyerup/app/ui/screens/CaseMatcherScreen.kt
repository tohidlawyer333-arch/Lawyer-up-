package com.lawyerup.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lawyerup.app.data.CaseMatcher
import com.lawyerup.app.data.LawRepository

@Composable
fun CaseMatcherScreen(repository: LawRepository) {
    var text by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<com.lawyerup.app.data.MatchResult>()) }
    var brief by remember { mutableStateOf<String?>(null) }
    var allClaims by remember { mutableStateOf(emptyList<com.lawyerup.app.data.local.ClaimEntity>()) }

    LaunchedEffect(Unit) {
        repository.allClaims().collect { allClaims = it }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("تطبیق پرونده و تنظیم لایحه", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("شرح مختصر پرونده را وارد کنید...") },
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { matches = CaseMatcher.match(text, allClaims) }) { Text("تطبیق") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { brief = CaseMatcher.draftBrief(text, matches) }) { Text("تنظیم لایحه") }
        }
        Spacer(Modifier.height(12.dp))
        if (matches.isEmpty()) Text("پیشنهادی یافت نشد.", style = MaterialTheme.typography.bodySmall)
        for (m in matches.take(5)) {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("${m.claim.title} (امتیاز ${m.score})",
                        style = MaterialTheme.typography.titleSmall)
                    Text(m.claim.articles, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        brief?.let {
            Spacer(Modifier.height(12.dp))
            Text("لایحه پیشنهادی:", style = MaterialTheme.typography.titleSmall)
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
