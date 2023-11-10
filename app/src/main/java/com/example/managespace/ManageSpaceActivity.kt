package com.example.managespace

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.managespace.ui.PieChart
import com.example.managespace.ui.theme.ManageSpaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis

class ManageSpaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManageSpaceTheme {
                ManageSpaceScreen()
            }
        }
    }
}

/** - contract */
sealed class ManageSpaceAction {
    object LoadSpaceData : ManageSpaceAction()
    object ClearCache : ManageSpaceAction()
}

sealed class ManageSpaceState {
    object Loading : ManageSpaceState()
    data class StorageSpace(
        val apkBytes: Long,
        val dataBytes: Long,
        val cacheBytes: Long,
        val apkSize: String,
        val dataSize: String,
        val cacheSize: String,
    ) : ManageSpaceState()
}

sealed class ManageSpaceEffect {
    data class ShowToast(val content: String) : ManageSpaceEffect()
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun ManageSpaceScreen() {
    val scaffoldState = remember { SnackbarHostState() }
    val viewModel = viewModel<ManageSpaceViewModel>()
    SideEffect {
        viewModel.sendAction(ManageSpaceAction.LoadSpaceData)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel.effect) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect {
                if (it is ManageSpaceEffect.ShowToast) {
                    scaffoldState.showSnackbar(it.content)
                }
            }
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(scaffoldState) }, floatingActionButton = {
        if (viewModel.state.value is ManageSpaceState.StorageSpace) {
            FloatingActionButton(onClick = {
                scaffoldState.currentSnackbarData?.dismiss()
                viewModel.sendAction(ManageSpaceAction.ClearCache)
            }) {
                Icon(Icons.Outlined.Delete, contentDescription = "")
            }
        }
    }) {
        when (val spaceState = viewModel.state.value) {
            ManageSpaceState.Loading -> CircularProgressIndicator()
            is ManageSpaceState.StorageSpace -> Box(
                modifier = Modifier
                    .padding(it)
                    .fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                val point = listOf(
                    spaceState.apkBytes.toFloat(), spaceState.dataBytes.toFloat(), spaceState.cacheBytes.toFloat()
                )
                val labels = listOf(
                    "应用大小:${spaceState.apkSize}", "用户数据:${spaceState.dataSize}", "缓存:${spaceState.cacheSize}"
                )
                val color = listOf(Color.Magenta, Color.Green, Color.Gray)
                PieChart(color, point, labels)
            }
        }
    }
}

class ManageSpaceViewModel constructor(
    val context: Application,
) : AndroidViewModel(context) {
    private val repository: ManageSpaceRepository = ManageSpaceRepository(context)

    private val _viewState: MutableState<ManageSpaceState> = mutableStateOf(ManageSpaceState.Loading)
    val state: State<ManageSpaceState> = _viewState

    private val _effect = MutableSharedFlow<ManageSpaceEffect>()
    val effect: SharedFlow<ManageSpaceEffect> by lazy { _effect.asSharedFlow() }

    fun sendAction(action: ManageSpaceAction) {
        when (action) {
            ManageSpaceAction.LoadSpaceData -> {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.getAppSize()
                    }.onSuccess {
                        _viewState.value = it
                    }.onFailure {
                        _effect.tryEmit(ManageSpaceEffect.ShowToast("free space load error"))
                    }
                }
            }

            ManageSpaceAction.ClearCache -> {
                viewModelScope.launch {
                    _viewState.value = (ManageSpaceState.Loading)
                    withContext(Dispatchers.IO) {
                        repository.clearCache()
                        repository.getAppSize()
                    }.onSuccess {
                        _viewState.value = it
                    }.onFailure {
                        _effect.tryEmit(ManageSpaceEffect.ShowToast("space clear error"))
                    }
                }
            }
        }
    }
}

class ManageSpaceRepository(private val context: Context) {
    private val filesDir: File
        get() = context.filesDir

    fun getAppSize() = kotlin.runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val uid = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).uid
            val storageStats = storageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, uid)
            ManageSpaceState.StorageSpace(
                dataBytes = storageStats.dataBytes,
                cacheBytes = storageStats.cacheBytes,
                apkBytes = storageStats.appBytes,
                dataSize = Formatter.formatFileSize(context, storageStats.dataBytes),
                cacheSize = Formatter.formatFileSize(context, storageStats.cacheBytes),
                apkSize = Formatter.formatFileSize(context, storageStats.appBytes),
            )
        } else {
            TODO()
        }
    }

    fun clearCache() = kotlin.runCatching {
        val time = measureTimeMillis {
            filesDir.deleteRecursively()
        }
        println(2500 - time)
    }
}
