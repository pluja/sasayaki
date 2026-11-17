package com.sasayaki.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.R
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

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun suggestedFileName(): String = backupFileName(LocalDate.now())

    fun export(uri: Uri) = run(R.string.backup_exported) { backupRepository.exportTo(uri) }

    fun import(uri: Uri) = run(R.string.backup_imported) { backupRepository.importFrom(uri) }

    private fun run(@StringRes success: Int, block: suspend () -> Unit) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            val message = try {
                block()
                success
            } catch (e: BackupException) {
                e.problem.message()
            } catch (_: IOException) {
                R.string.backup_file_unreadable
            } catch (_: SecurityException) {
                R.string.backup_file_unreadable
            } finally {
                _working.value = false
            }
            _messages.send(message)
        }
    }
}

@StringRes
private fun BackupProblem.message(): Int = when (this) {
    BackupProblem.NOT_SETTINGS_FILE -> R.string.backup_not_settings_file
    BackupProblem.NEWER_VERSION -> R.string.backup_newer_version
    BackupProblem.DAMAGED -> R.string.backup_damaged
}
