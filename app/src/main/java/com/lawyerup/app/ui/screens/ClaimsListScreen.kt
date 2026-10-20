package com.lawyerup.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lawyerup.app.data.LawRepository
import com.lawyerup.app.data.local.ClaimEntity
import kotlinx.coroutines.flow.Flow

@Composable
fun ClaimsListScreen(repository: LawRepository, onClaimClick: (Long) -> Unit) {
    var selectedForum by remember { mutableStateOf<String?>(null) }
    val forums = listOf("حقوقی", "کیفری", "کار", "مالیاتی", "شهرداری", "ثبتی", "تجاری")
    val claims: List<ClaimEntity> by (if (selectedForum == null) repository.allClaims() else repository.claimsByForum(selectedForum!!))
        .collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Text("مرجع محدود همراه فایل اولیه؛ کامل یا به‌روز بودن مواد احراز نشده است. پیش از استناد، متن رسمی قانون جاری بررسی شود.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        Text("دعاوی و مستندات قانونی", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp))
        LazyRow(modifier = Modifier.padding(horizontal = 8.dp)) {
            items(forums.size) { i ->
                val f = forums[i]
                FilterChip(
                    selected = selectedForum == f,
                    onClick = { selectedForum = if (selectedForum == f) null else f },
                    label = { Text(f) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(claims) { claim ->
                Card(Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onClaimClick(claim.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(claim.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(claim.forum, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text(claim.articles, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2)
                    }
                }
            }
        }
    }
}
