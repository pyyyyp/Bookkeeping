package com.jizhangbao.worklog.presentation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhangbao.worklog.R
import com.jizhangbao.worklog.domain.SessionState
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.Workplace
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 工时页（`REQ-016/AC-7`~`AC-9`）。
 *
 * ## 三块，各自回答一件事
 *
 * 1. **权限**：自动记录开没开？没开就**明说**（`AC-8`：静默失效最糟）。
 * 2. **工作地点**：在哪算上班？用**当前位置**配（`AC-7`：不做地图，那是新依赖）。
 * 3. **今天的时段**：围栏记下的东西等着你点头（`AC-9`：系统只记录、人确认）。
 *
 * ⚠️ 文案全部走 `stringResource`（`R12`），代码里不出现中文串。
 */
@Composable
fun WorklogScreen(
    modifier: Modifier = Modifier,
    viewModel: WorklogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onPermissionResult() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.worklog_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        PermissionSection(
            granted = state.hasLocationPermission,
            onRequest = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
        )
        WorkplacesSection(
            workplaces = state.workplaces,
            enabled = !state.isBusy,
            onAddHere = viewModel::addWorkplaceHere,
            onRemove = viewModel::removeWorkplace,
        )
        SessionsSection(
            sessions = state.sessions,
            zone = state.zone,
            onConfirm = viewModel::confirm,
            onDiscard = viewModel::discard,
        )
    }

    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            confirmButton = {
                TextButton(onClick = viewModel::dismissNotice) {
                    Text(stringResource(R.string.worklog_dismiss))
                }
            },
            text = { Text(stringResource(notice.textRes())) },
        )
    }
}

/** 把"哪一类事"翻成文案。**只在这里**把结构化的提示变成用户看到的话。 */
private fun WorklogNotice.textRes(): Int = when (this) {
    WorklogNotice.LocationPermissionMissing -> R.string.worklog_permission_missing
    WorklogNotice.LocationUnavailable -> R.string.worklog_location_unavailable
    WorklogNotice.StorageFailed -> R.string.worklog_storage_failed
}

@Composable
private fun PermissionSection(granted: Boolean, onRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                if (granted) R.string.worklog_permission_granted
                else R.string.worklog_permission_missing,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!granted) {
            Button(onClick = onRequest) {
                Text(stringResource(R.string.worklog_permission_request))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun WorkplacesSection(
    workplaces: List<Workplace>,
    enabled: Boolean,
    onAddHere: (String, Double) -> Unit,
    onRemove: (com.jizhangbao.worklog.domain.WorkplaceId) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.worklog_workplaces_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (workplaces.isEmpty()) {
            Text(
                text = stringResource(R.string.worklog_workplaces_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        workplaces.forEach { workplace ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(workplace.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.worklog_workplace_summary, workplace.radiusMeters),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onRemove(workplace.id) }) {
                    Text(stringResource(R.string.worklog_workplace_remove))
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.worklog_workplace_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = radius,
            onValueChange = { radius = it },
            label = { Text(stringResource(R.string.worklog_workplace_radius)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            // 半径解析不出数字时按钮仍然可点，但 VM 会拒绝非法值（<= 0）——
            // 界面不做校验的另一份（两条校验路径迟早漂移）
            onClick = { onAddHere(name, radius.toDoubleOrNull() ?: 0.0) },
            enabled = enabled,
        ) {
            Text(stringResource(R.string.worklog_workplace_add_here))
        }
        HorizontalDivider()
    }
}

@Composable
private fun SessionsSection(
    sessions: List<WorkSession>,
    zone: ZoneId?,
    onConfirm: (WorkSession) -> Unit,
    onDiscard: (WorkSession) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.worklog_sessions_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.worklog_sessions_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
        sessions.forEach { session ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.worklog_session_range,
                            session.startedAt.atZone(zone ?: ZoneId.systemDefault()).format(formatter),
                            session.endedAt?.atZone(zone ?: ZoneId.systemDefault())?.format(formatter)
                                ?: stringResource(R.string.worklog_session_running),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(session.state.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // 只有还没定论的时段才给按钮：已确认的不该再被随手改掉
                if (session.state == SessionState.RUNNING || session.state == SessionState.FINISHED) {
                    TextButton(onClick = { onConfirm(session) }) {
                        Text(stringResource(R.string.worklog_session_confirm))
                    }
                    TextButton(onClick = { onDiscard(session) }) {
                        Text(stringResource(R.string.worklog_session_discard))
                    }
                }
            }
        }
    }
}

private fun SessionState.labelRes(): Int = when (this) {
    SessionState.RUNNING -> R.string.worklog_session_running
    SessionState.FINISHED -> R.string.worklog_session_finished
    SessionState.CONFIRMED -> R.string.worklog_session_confirmed
    SessionState.DISCARDED -> R.string.worklog_session_discarded
}
