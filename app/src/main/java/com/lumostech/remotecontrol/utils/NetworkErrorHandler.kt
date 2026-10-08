package com.lumostech.remotecontrol.utils

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络错误处理工具
 * 统一处理各种网络异常，提供用户友好的错误消息
 */
object NetworkErrorHandler {

    /**
     * 处理异常并返回用户友好的错误信息
     */
    fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "请求超时，请检查网络连接"
            is ConnectException, is UnknownHostException -> "无法连接到服务器，请检查网络"
            is HttpException -> handleHttpError(throwable)
            is IOException -> "网络错误: ${throwable.message}"
            else -> "发生错误: ${throwable.message ?: "未知错误"}"
        }
    }

    /**
     * 处理 HTTP 错误码
     */
    private fun handleHttpError(exception: HttpException): String {
        return when (exception.code()) {
            400 -> "请求参数错误"
            401 -> "未授权，请重新登录"
            403 -> "访问被拒绝"
            404 -> "请求的资源不存在"
            409 -> "资源冲突"
            500 -> "服务器内部错误"
            502 -> "网关错误"
            503 -> "服务暂时不可用"
            504 -> "网关超时"
            else -> "HTTP 错误 ${exception.code()}: ${exception.message()}"
        }
    }

    /**
     * 判断是否是网络错误（可重试）
     */
    fun isRetryable(throwable: Throwable): Boolean {
        return when (throwable) {
            is SocketTimeoutException -> true
            is ConnectException -> true
            is UnknownHostException -> true
            is IOException -> true
            is HttpException -> throwable.code() in 500..599
            else -> false
        }
    }

    /**
     * 获取建议的重试延迟时间（指数退避）
     */
    fun getRetryDelay(attempt: Int, baseDelay: Long = 1000): Long {
        return (baseDelay * Math.pow(2.0, attempt.toDouble())).toLong()
    }
}

/**
 * Result 扩展函数 - 简化错误处理
 */
fun <T> Result<T>.getOrDefault(default: T): T {
    return this.getOrElse { default }
}

fun <T> Result<T>.isSuccess(): Boolean = this.isSuccess

fun <T> Result<T>.isFailure(): Boolean = this.isFailure

fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    this.onSuccess { action(it) }
    return this
}

fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    this.onFailure { action(it) }
    return this
}

fun <T> Result<T>.logError(tag: String = "Result"): Result<T> {
    this.onFailure { Logger.e(tag, "操作失败", it) }
    return this
}
