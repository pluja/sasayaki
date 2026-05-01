package com.sasayaki.service

sealed class ServiceState {
    data object Idle : ServiceState()
    data class Recording(val paused: Boolean = false) : ServiceState()
    data object Transcribing : ServiceState()
    data object PostProcessing : ServiceState()
    data object Injecting : ServiceState()
    data class Error(val message: String, val retryEntryId: Long? = null) : ServiceState()
}
