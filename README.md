# 使用存储空间管理来优化用户体验

## 应用管理详情

用户在感觉手机空间被APP占用较大时候，会查看APP的应用占用磁盘详情，可以在设置的应用管理找到当前APP然后查看。
<img src="https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/e283198833364f46a81af79a9ef52e0f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?h=200\&s=268348\&e=png\&b=f4f4f4" alt="drawing" width="300"/>

- 应用大小：安装APK解压后占用磁盘空间
- 缓存：指`data/data/{package-name}/cache`和`sdcard/Android/{package-name}/cache`占用磁盘大小
- 用户数据：指`data/data/{package-name}/`和`sdcard/Android/{package-name}/`**减去缓存磁盘**占用后的大小，可能不同厂商定制后的计算有所不同。

## 管理储存空间

app安装后要恢复到安装时候的状态有2种方式，卸载重新安装或者打开设置找到APP的应用管理-> 清除数据 -> 清除全部数据/清除缓存。而在日常使用中发现有个特别的情况，在TG和bilibili的应用在选择清除数据时候没有清除全部数据的选项，而是管理空间（在piexl7Pro 上还是clear data）点击后会打开APP自己的一个缓存管理定制页面，在这里可以自由的清理一些开发者允许的数据。

![EasyGIF-1699615409439.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/d73b77decf5144bfbf41e99c43f99a47~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=360\&h=780\&s=436031\&e=gif\&f=33\&b=ffffff)

## 定制存储空间管理

其实在google官方文档中有专门对这个功能的介绍，只是平常开发中并没有产品对此有特别需求，我们就很少知道了。
<https://developer.android.com/training/data-storage/app-specific?hl=zh-cn#create-storage-management-activity>

让我们跟着文档学习下如何定制一个存储空间管理的页面吧。

1.  创建并在Manifest中注册一个普通Activity或者是你APP中已经存在的管理和缓存清理的Activity，不需要添加任何特殊属性。当您的 activity 将 android:exported 设置为 false 时，仍然可以调用。
    ```xml
    <application
     ...
     android:name=".app.DemoApplication"
     android:theme="@style/AppTheme">
     ...
     <activity
         android:name=".screen.ManageSpaceActivity"
         android:exported="false"/>
    ```
2.  在application的标签中添加android:manageSpaceActivity并声明上面的Activity路径
    ```xml
    <application
         android:name=".app.DemoApplication"
         ...
         android:manageSpaceActivity=".screen.ManageSpaceActivity"
         android:theme="@style/AppTheme">
    ```
3.  安装APP在设置中找到应用详情，点击清理数据 -> 管理空间

## 用Compose + MVI 快速实现存储空间管理页面

1.  使用Compose编写UI，并且提供一个按钮去清理空间, PieChart源码查看后面github link
    ```kotlin
     class ManageSpaceActivity : AppCompatActivity() {
         override fun onCreate(savedInstanceState: Bundle?) {
             super.onCreate(savedInstanceState)
             setContent {
                 MVIHiltTheme {
                     ManageSpaceScreen()
                 }
             }
         }
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
    ```

2.  定义MVI中的model和Intent
    ```kotlin
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
    ```

3.  给ViewModel添加一个repository来获取数据，并且管理M和I
    ```kotlin
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
    ```

4.  repository通过StorageStatsManager来获取APP的所有信息和清理缓存
    ```kotlin
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
    ```

5.  运行查看下 
    <img src="https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/dcbeb9d4c397407db0563d8b3b443420~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=512&h=1089&s=185254&e=png&b=fffbfe" alt="drawing" width="300"/>

## Github

<https://github.com/forJrking/ManageSpace>
