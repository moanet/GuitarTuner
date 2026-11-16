package com.example.guitartuner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guitartuner.ui.theme.Accent
import com.example.guitartuner.ui.theme.Danger
import com.example.guitartuner.ui.theme.TextPrimary
import com.example.guitartuner.ui.theme.TextSecondary
import com.example.guitartuner.ui.theme.TunerBackground

/**
 * 麦克风权限拒绝后的引导页：说明用途，提供重新申请与打开系统设置两个入口。
 * 不申请权限时应用不崩溃，仅停留在引导页。
 */
@Composable
fun PermissionGuide(onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("需要麦克风权限", color = TextPrimary, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "吉他调音器通过麦克风实时采集琴弦声音进行音高检测。\n拒绝后将无法开始监听。",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) { Text("授予麦克风权限") }
        TextButton(
            onClick = {
                runCatching {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }
            }
        ) { Text("前往系统设置手动开启", color = TextSecondary) }
    }
}
