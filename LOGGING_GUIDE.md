# 统一错误处理和日志系统使用指南

## 概述

本系统提供了一套完整的日志记录、异常处理和错误显示方案，已集成到 RemoteControl 项目中。

## 核心组件

### 1. Logger - 统一日志工具

**位置：** `utils.Logger`

**功能：**
- 多级别日志（VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT）
- 自动生成标签（包含包名前缀）
- 支持生产环境开关
- 支持异常堆栈打印

**使用方法：**

```kotlin
// 基本使用（自动使用调用类名作为标签）
Logger.d("这是调试信息")
Logger.i("应用已启动")
Logger.w("警告信息")
Logger.e("错误信息", exception)

// 自定义标签
Logger.d("Network", "请求成功")
Logger.e("Database", "查询失败", throwable)

// 打印对象
Logger.logObject(user, Logger.Level.INFO)
Logger.logException(exception, "CustomTag")

// 控制日志开关（在 Application 中已配置）
Logger.isEnabled = BuildConfig.DEBUG
```

### 2. GlobalExceptionHandler - 全局异常处理器

**位置：** `utils.GlobalExceptionHandler`

**功能：**
- 捕获所有未处理的异常
- 记录详细崩溃报告（设备信息、堆栈）
- 自动保存到本地文件
- 可扩展上报机制

**初始化：** 已在 `MyApp.onCreate()` 中自动初始化

**崩溃报告保存位置：**
```
/storage/emulated/0/Android/data/com.lumostech.remotecontrol/cache/crashes/crash_yyyyMMdd_HHmmss.txt
```

### 3. CoroutineExtensions - 协程安全执行

**位置：** `utils.CoroutineExtensions`

**功能：**
- 统一协程异常处理
- 自动重试机制
- IO/Main 线程安全执行

**使用方法：**

```kotlin
// 1. 使用预定义的异常处理器
viewModelScope.launch(AppCoroutineExceptionHandler) {
    // 协程代码
}

// 2. 安全执行（自动捕获异常）
val result = runSafely {
    repository.loadData()
}.onSuccess { data ->
    // 处理数据
}.onError { e ->
    // 处理错误
}.logError("Repository")

// 3. IO 线程安全执行
val result = ioSafe {
    api.getData()
}

// 4. 主线程安全执行
val result = mainSafe {
    updateUi()
}

// 5. 自动重试（网络请求）
val result = retryIO(maxRetries = 3, delayMillis = 1000) {
    api.fetchData()
}
```

### 4. NetworkErrorHandler - 网络错误处理

**位置：** `utils.NetworkErrorHandler`

**功能：**
- 统一处理网络异常
- 提供用户友好的错误消息
- 判断是否可重试
- 指数退避延迟计算

**使用方法：**

```kotlin
try {
    val response = api.getData()
} catch (e: Exception) {
    val errorMessage = NetworkErrorHandler.getErrorMessage(e)
    // 显示给用户
    showError(errorMessage)
    
    // 判断是否可重试
    if (NetworkErrorHandler.isRetryable(e)) {
        // 自动重试
    }
}
```

### 5. UiErrorHandler - UI 错误显示

**位置：** `utils.UiErrorHandler`

**功能：**
- 自动将异常转换为用户友好消息
- 集成 Toast/Snackbar 显示
- 同时记录日志

**使用方法：**

```kotlin
// 显示 Toast
UiErrorHandler.handleException(context, exception)

// 显示 Snackbar（需要 View）
UiErrorHandler.showSnackbar(view, exception, "重试") {
    viewModel.retry()
}

// 显示简单消息
UiErrorHandler.showMessage(context, "操作成功")
```

## 集成到现有代码

### 示例 1：Repository 中的网络请求

```kotlin
class UserRepository @Inject constructor(
    private val api: UserApiService
) {
    suspend fun getUser(userId: String): Result<User> {
        return retryIO(maxRetries = 3) {
            api.getUser(userId)
        }.logError("UserRepository")
        .onError { e ->
            // 可选：额外处理
            Logger.w("UserRepository", "获取用户失败: $userId", e)
        }
    }
}
```

### 示例 2：ViewModel 中使用

```kotlin
class UserViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadUser(userId: String) {
        viewModelScope.launch(AppCoroutineExceptionHandler) {
            _uiState.value = UiState.Loading
            
            repository.getUser(userId)
                .onSuccess { user ->
                    _uiState.value = UiState.Success(user)
                    Logger.d("UserViewModel", "用户数据加载成功")
                }
                .onError { e ->
                    _uiState.value = UiState.Error(e)
                    Logger.e("UserViewModel", "用户数据加载失败", e)
                }
        }
    }
}
```

### 示例 3：Activity/Fragment 中显示错误

```kotlin
class UserActivity : ComponentActivity() {

    private val viewModel: UserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 收集 UI 状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> showData(state.data)
                        is UiState.Error -> {
                            UiErrorHandler.showSnackbar(
                                findViewById(android.R.id.content),
                                state.error,
                                "重试"
                            ) { viewModel.retry() }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
```

### 示例 4：传统回调中的错误处理

```kotlin
class LegacyTask {
    
    fun execute(task: Task, callback: (Result) -> Unit) {
        try {
            val result = task.run()
            Logger.d("LegacyTask", "任务执行成功")
            callback(Result.success(result))
        } catch (e: Exception) {
            Logger.e("LegacyTask", "任务执行失败", e)
            callback(Result.failure(e))
        }
    }
}
```

## 配置建议

### 1. 日志级别控制

在 `MyApp` 中已根据 `BuildConfig.DEBUG` 自动控制：

```kotlin
// 调试版本：详细日志
// 发布版本：仅 WARN/ERROR
Logger.isEnabled = BuildConfig.DEBUG
```

### 2. ProGuard 配置

发布版本中，确保保留日志类：

```
-keep class com.lumostech.remotecontrol.utils.** { *; }
-keepclassmembers class com.lumostech.remotecontrol.utils.** { *; }
```

### 3. 崩溃报告收集

建议在 `GlobalExceptionHandler` 中添加：
- 将崩溃报告上传到服务器
- 集成 Firebase Crashlytics 或 Sentry
- 添加用户反馈功能

## 最佳实践

1. **统一使用 Logger** - 避免直接使用 `Log.d/e` 等
2. **协程必须使用异常处理器** - 使用 `AppCoroutineExceptionHandler`
3. **网络请求使用 retryIO** - 自动重试 + 日志
4. **UI 层使用 UiErrorHandler** - 确保错误可见且记录日志
5. **Result 类型包装** - 明确成功/失败状态
6. **异常不吞噬** - 所有异常必须记录日志

## 待扩展功能

- [ ] 集成 Firebase Crashlytics
- [ ] 用户行为日志（需隐私合规）
- [ ] 性能监控（启动时间、FPS）
- [ ] 网络请求拦截器（自动重试、缓存）
- [ ] 日志文件轮转和上传

---

**创建时间：** 2026-03-14
**版本：** 1.0
