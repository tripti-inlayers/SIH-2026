package com.sancharsaathi.app.data.remote

enum class FailureReason {
    TIMEOUT,
    NO_CONNECTION,
    SERVER_ERROR,
    UNKNOWN
}

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val reason: FailureReason, val message: String) : NetworkResult<Nothing>
}
