package com.lawyerup.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lawyerup.app.ui.*
import com.lawyerup.app.ui.theme.LawyerUpTheme
import com.lawyerup.app.data.Workspace
import com.lawyerup.app.domain.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class WorkspaceUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dashboardHasRealEmptyCountsAndWorkingReceive() {
        var clicked=false
        compose.setContent {LawyerUpTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {Dashboard(Workspace(),{}, {clicked=true},{})}}}
        compose.onNodeWithText("دریافت و تطبیق ثنا").performClick()
        assertTrue(clicked)
        compose.onAllNodesWithText("۰").assertCountEquals(4)
    }
    @Test fun PersianSearchFindsEnglishIdentifier() {
        val record=CourtRecord(RecordKind.CASE,caseNumber="1000000000000001",fields=mapOf("شماره پرونده" to "1000000000000001"))
        compose.setContent {LawyerUpTheme {RecordsScreen(Workspace(records=listOf(record)),false,{})}}
        compose.onNodeWithText("شماره، شعبه یا موضوع").performTextInput("۱۰۰۰۰۰۰۰")
        compose.onNodeWithText("۱۰۰۰۰۰۰۰۰۰۰۰۰۰۰۱").assertExists()
    }
    @Test fun statusDoesNotClaimUnobservedAction() {
        val record=CourtRecord(RecordKind.NOTICE,noticeNumber="200000000000000001")
        compose.setContent {LawyerUpTheme {RecordTile(record,Workspace(records=listOf(record)),{})}}
        compose.onNodeWithText("نیازمند جزئیات یا گردش پرونده").assertExists()
    }
}
