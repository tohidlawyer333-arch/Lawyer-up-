package com.lawyerup.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lawyerup.app.ui.WorkspaceApp
import com.lawyerup.app.ui.WelcomeScreen
import com.lawyerup.app.ui.theme.LawyerUpTheme

class MainActivity: ComponentActivity() {
    private val model: WorkspaceModel by viewModels()
    private var unlocked by mutableStateOf(false)
    private var gateMessage by mutableStateOf("با قفل دستگاه وارد دفتر امن خود شوید")
    private val authentication=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        unlocked=result.resultCode==Activity.RESULT_OK
        if(!unlocked) gateMessage="ورود تأیید نشد؛ برای ادامه دوباره تلاش کنید"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawyerUpTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    WorkspaceApp(model,onLock={unlocked=false},locked=!unlocked)
                    if(!unlocked) androidx.compose.ui.window.Dialog(onDismissRequest={},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false,dismissOnClickOutside=false)) {
                        WelcomeScreen(gateMessage,::authenticate) {startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))}
                    }
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun authenticate() {
        val manager=getSystemService(KeyguardManager::class.java)
        if(!manager.isDeviceSecure) {gateMessage="برای حفاظت از پرونده‌ها، ابتدا قفل صفحه دستگاه را فعال کنید";return}
        val intent=manager.createConfirmDeviceCredentialIntent("ورود به پایش","قفل دستگاه را برای دسترسی به پایش تأیید کنید")
        if(intent!=null) authentication.launch(intent) else gateMessage="احراز هویت دستگاه در دسترس نیست"
    }
}
