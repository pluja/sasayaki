package com.sasayaki.service

sealed class ServiceState {
    data object Idle : ServiceState()
    data object Recording : ServiceState()
    data object Transcribing : ServiceState()
    data object Injecting : ServiceState()
    data class Error(val message: String) : ServiceState()
}
