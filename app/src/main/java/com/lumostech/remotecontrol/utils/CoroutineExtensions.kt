package com.lumostech.remotecontrol.utils

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * 协程异常处理器
 * 用于ViewModel、Repository等协程作用域
 */
val AppCoroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
    Logger.e(context, "协程异常: ${throwable.message}", throwable)
    handleCoroutineError(throwable)
}

/**
 * 处理协程错误
 */
private fun handleCoroutineError(throwable: Throwable) {
    when (throwable) {
        is IOException -> {
            Logger.w("Network", "网络IO异常: ${throwable.message}")
        }
        is SocketTimeoutException -> {
            Logger.w("Network", "请求超时")
        }
        is HttpException -> {
            Logger.e("API", "HTTP 错误: ${throwable.code()} - ${throwable.message()}")
        }
        else -> {
            Logger.e("Unknown", "未预期的异常", throwable)
        }
    }
}

/**
 * 安全执行 Suspend 函数，自动捕获异常
 */
suspend fun <T> runSafely(
    block: suspend () -> T,
    onError: ((Throwable) -> Unit)? = null
): Result<T> = try {
    Result.success(block())
} catch (e: Exception) {
    Logger.e("SafeRun", "执行失败: ${e.message}", e)
    onError?.invoke(e)
    Result.failure(e)
}

/**
 * 在 IO 线程安全执行
 */
suspend fun <T> ioSafe(block: suspend () -> T): Result<T> = 
    withContext(Dispatchers.IO) { runSafely(block) }

/**
 * 在主线程安全执行
 */
suspend fun <T> mainSafe(block: suspend () -> T): Result<T> = 
    withContext(Dispatchers.Main) { runSafely(block) }

/**
 * 网络请求重试包装器
 */
suspend fun <T> retryIO(
    maxRetries: Int = 3,
    delayMillis: Long = 1000,
    block: suspend () -> T
): Result<T> {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try {
            Logger.d("Retry", "尝试 ${attempt + 1}/$maxRetries")
            return Result.success(block())
        } catch (e: Exception) {
            lastException = e
            Logger.w("Retry", "尝试 ${attempt + 1} 失败: ${e.message}")
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(delayMillis)
            }
        }
    }
    Logger.e("Retry", "重试 $maxRetries 次后失败", lastException)
    return Result.failure(lastException ?: Exception("Unknown error"))
}
