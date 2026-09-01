package com.manish.helperai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HelperAIResponseStore {

    private val _latestResponse =
        MutableStateFlow("")

    val latestResponse: StateFlow<String> =
        _latestResponse.asStateFlow()

    fun updateResponse(
        response: String
    ) {
        val cleaned =
            response.trim()

        if (cleaned.isEmpty()) {
            return
        }

        _latestResponse.value =
            cleaned
    }

    fun clearResponse() {
        _latestResponse.value = ""
    }
}