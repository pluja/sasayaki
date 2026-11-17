package com.sasayaki.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.backup.BackupException
import com.sasayaki.data.backup.BackupProblem
import com.sasayaki.data.backup.SettingsBackupRepository
import com.sasayaki.data.backup.backupFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BackupViewModel @Inject constructor(private val backupRepository: SettingsBackupRepository) : ViewModel() {
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun suggestedFileName(): String = backupFileName(LocalDate.now())

    fun export(uri: Uri) = run("Settings exported") { backupRepository.exportTo(uri) }

    fun import(uri: Uri) = run("Settings imported") { backupRepository.importFrom(uri) }

    private fun run(success: String, block: suspend () -> Unit) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            val message = try {
                block()
                success
            } catch (e: BackupException) {
                e.problem.message()
            } catch (_: IOException) {
                "Couldn't open that file. Try another location."
            } catch (_: SecurityException) {
                "Couldn't open that file. Try another location."
            } finally {
                _working.value = false
            }
            _messages.send(message)
        }
    }
}

private fun BackupProblem.message(): String = when (this) {
    BackupProblem.NOT_SETTINGS_FILE -> "That file isn't a Sasayaki settings file."
    BackupProblem.NEWER_VERSION -> "This file is from a newer Sasayaki. Update the app first."
    BackupProblem.DAMAGED -> "That settings file is damaged, so nothing was changed."
}
