package com.example.guitartuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.example.guitartuner.tuning.TuningRepository
import com.example.guitartuner.ui.PermissionGuide
import com.example.guitartuner.ui.TunerScreen
import com.example.guitartuner.ui.TunerViewModel
import com.example.guitartuner.ui.theme.GuitarTunerTheme

/**
 * 入口 Activity。负责：
 *  1) 创建调弦仓库与 ViewModel；
 *  2) 运行时动态申请 RECORD_AUDIO，拒绝时给出引导（不崩溃）；
 *  3) 监听前后台生命周期：切后台停止采集并释放 AudioRecord，回前台恢复监听。
 */
class MainActivity : ComponentActivity() {

    private lateinit var vm: TunerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TuningRepository(applicationContext)
        vm = ViewModelProvider(this, TunerViewModel.Factory(repository, applicationContext))[TunerViewModel::class.java]

        setContent {
            GuitarTunerTheme {
                var granted by rememberSaveable { mutableStateOf(checkAudioPermission()) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { result -> granted = result }

                if (granted) {
                    TunerScreen(vm)
                } else {
                    PermissionGuide(onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) })
                }
            }
        }

        // 前后台生命周期：切后台停止采集，回前台恢复监听（不自动开启用户未开启的监听）
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onAppBackground()
                Lifecycle.Event.ON_START -> vm.onAppForeground()
                else -> Unit
            }
        })
    }

    private fun checkAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
