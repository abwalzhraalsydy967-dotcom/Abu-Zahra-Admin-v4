package com.abuzahra.admin.data.api

/**
 * Sealed class representing the result of an API operation.
 * Used throughout the app to handle success, error, and loading states.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}