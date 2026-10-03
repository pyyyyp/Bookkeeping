package com.jizhangbao.worklog.presentation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.jizhangbao.core.ui.component.AmountTone
import com.jizhangbao.core.ui.component.SectionCard
import com.jizhangbao.core.ui.component.SectionTitle
import com.jizhangbao.core.ui.component.StatTile
import com.jizhangbao.core.ui.theme.jizhangbaoColors
import com.jizhangbao.worklog.R
import com.jizhangbao.worklog.domain.SessionState
import com.jizhangbao.worklog.domain.WorkSession
import com.jizhangbao.worklog.domain.Workplace
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 时段状态在界面上的语气（`ADR-0014` 决策 2）。 */
private fun SessionState.tone(): AmountTone = when (this) {
    SessionState.RUNNING -> AmountTone.ACCENT
    SessionState.FINISHED -> AmountTone.NEUTRAL
    SessionState.CONFIRMED -> AmountTone.INCOME
    SessionState.DISCARDED -> AmountTone.MUTED
}

/**
 * 工时页（`REQ-016/AC-7`~`AC-9`）。
 *
 * ## 三块，各自回答一件事（`T-036` 起每块是一张卡片）
 *
 * 1. **权限**：自动记录开没开？没开就**明说**（`AC-8`：静默失效最糟）。
 * 2. **工作地点**：在哪算上班？用**当前位置**配（`AC-7`：不做地图，那是新依赖）。
 * 3. **今天的时段**：围栏记下的东西等着你点头（`AC-9`：系统只记录、人确认）。
 *
 * ⚠️ 这一轮只动**外观**（卡片 / 主次 / 语义色，`T-037`）：
 * 三块的判断逻辑、按钮出现条件、以及所有 `AC` 驱动的分支**一条没动**。
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.worklog_title),
            style = MaterialTheme.typography.titleMedium,
        )
        PermissionCard(
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
        WorkplacesCard(
            workplaces = state.workplaces,
            enabled = !state.isBusy,
            onAddHere = viewModel::addWorkplaceHere,
            onRemove = viewModel::removeWorkplace,
        )
        SessionsCard(
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

/**
 * 权限状态（`AC-8`）。
 *
 * ⚠️ **持续状态写在卡片里，不弹对话框** —— `T-031` 的真机教训：
 * 对话框会**盖住**「开启自动记录」按钮，用户看到缺权限却点不到修好它的入口。
 */
@Composable
private fun PermissionCard(granted: Boolean, onRequest: () -> Unit) {
    val colors = MaterialTheme.jizhangbaoColors
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        // 没授权时用强调色描边：这一块是当前唯一要用户动手的地方
        highlight = !granted,
    ) {
        SectionTitle(stringResource(R.string.worklog_title))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (granted) R.string.worklog_permission_granted
                else R.string.worklog_permission_missing,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) colors.income else MaterialTheme.colorScheme.onSurface,
        )
        if (!granted) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onRequest) {
                Text(stringResource(R.string.worklog_permission_request))
            }
        }
    }
}

@Composable
private fun WorkplacesCard(
    workplaces: List<Workplace>,
    enabled: Boolean,
    onAddHere: (String, Double) -> Unit,
    onRemove: (com.jizhangbao.worklog.domain.WorkplaceId) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.worklog_workplaces_title))
        Spacer(modifier = Modifier.height(6.dp))
        if (workplaces.isEmpty()) {
            Text(
                text = stringResource(R.string.worklog_workplaces_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.jizhangbaoColors.muted,
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
                        color = MaterialTheme.jizhangbaoColors.muted,
                    )
                }
                TextButton(onClick = { onRemove(workplace.id) }) {
                    Text(
                        text = stringResource(R.string.worklog_workplace_remove),
                        color = MaterialTheme.jizhangbaoColors.muted,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.worklog_workplace_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = radius,
            onValueChange = { radius = it },
            label = { Text(stringResource(R.string.worklog_workplace_radius)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            // 半径解析不出数字时按钮仍然可点，但 VM 会拒绝非法值（<= 0）——
            // 界面不做校验的另一份（两条校验路径迟早漂移）
            onClick = { onAddHere(name, radius.toDoubleOrNull() ?: 0.0) },
            enabled = enabled,
        ) {
            Text(stringResource(R.string.worklog_workplace_add_here))
        }
    }
}

@Composable
private fun SessionsCard(
    sessions: List<WorkSession>,
    zone: ZoneId?,
    onConfirm: (WorkSession) -> Unit,
    onDiscard: (WorkSession) -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.worklog_sessions_title))
        Spacer(modifier = Modifier.height(6.dp))
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.worklog_sessions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.jizhangbaoColors.muted,
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
                    // 状态按语气上色：进行中青、已确认绿、已作废灰
                    StatelessStateLabel(session.state)
                }
                // 只有还没定论的时段才给按钮：已确认的不该再被随手改掉
                if (session.state == SessionState.RUNNING || session.state == SessionState.FINISHED) {
                    TextButton(onClick = { onConfirm(session) }) {
                        Text(
                            text = stringResource(R.string.worklog_session_confirm),
                            color = MaterialTheme.jizhangbaoColors.accent,
                        )
                    }
                    TextButton(onClick = { onDiscard(session) }) {
                        Text(
                            text = stringResource(R.string.worklog_session_discard),
                            color = MaterialTheme.jizhangbaoColors.muted,
                        )
                    }
                }
            }
        }
    }
}

/** 时段状态那一行小字（用 `StatTile` 的标签样式，但颜色跟状态走）。 */
@Composable
private fun StatelessStateLabel(state: SessionState) {
    val colors = MaterialTheme.jizhangbaoColors
    Text(
        text = stringResource(state.labelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = when (state.tone()) {
            AmountTone.ACCENT -> colors.accent
            AmountTone.INCOME -> colors.income
            AmountTone.EXPENSE -> colors.expense
            else -> colors.muted
        },
    )
}

private fun SessionState.labelRes(): Int = when (this) {
    SessionState.RUNNING -> R.string.worklog_session_running
    SessionState.FINISHED -> R.string.worklog_session_finished
    SessionState.CONFIRMED -> R.string.worklog_session_confirmed
    SessionState.DISCARDED -> R.string.worklog_session_discarded
}
