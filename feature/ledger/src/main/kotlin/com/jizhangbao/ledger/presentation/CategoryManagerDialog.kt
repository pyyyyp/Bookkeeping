package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.Category

/**
 * 分类管理（`REQ-004`）：一个**全屏对话框**，不是新界面。
 *
 * ## 为什么不引入导航
 *
 * 到这一步应用只有一个界面。为一个"打开/关闭"引入 navigation 依赖，
 * 是为了将来的需要付现在的成本（新依赖、新概念、返回栈要处理）。
 * 全屏对话框能满足"从记账页进去、办完事回来"这个全部需求，而且**没有返回栈**要维护。
 * 真需要多界面导航时再引入，那时是一次有依据的决策，不是现在猜。
 *
 * ## 预置分类只读（`AC-7`）
 *
 * 它们**列出来但没有改名/归档入口**，并给出文字说明 ——
 * 而不是让按钮点了没反应（那是最差的形态：用户以为坏了）。
 */
@Composable
internal fun CategoryManagerDialog(
    onDismiss: () -> Unit,
    viewModel: CategoryManagerViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.category_manager_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                NewCategorySection(state = state, viewModel = viewModel)

                state.failure?.let { failure ->
                    Text(
                        text = stringResource(failure.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider()

                CustomCategoriesSection(state = state, viewModel = viewModel)

                HorizontalDivider()

                PresetCategoriesSection(state = state)

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.category_manager_close))
                }
            }
        }
    }
}

/**
 * 方向的显示名。
 *
 * 不用 `EntryForm` 里那个 `labelRes()`：它是那个文件的 private 扩展，
 * 复用它要么放宽可见性、要么把这条映射挪成公共工具 —— 为两个字符串不值得。
 * 各自的界面自己映射文案，本来就是这一层的职责。
 */
@Composable
private fun EntryDirection.directionLabel(): String = stringResource(
    when (this) {
        EntryDirection.Expense -> R.string.ledger_direction_expense
        EntryDirection.Income -> R.string.ledger_direction_income
    },
)

@Composable
private fun CustomCategoriesSection(
    state: CategoryManagerUiState,
    viewModel: CategoryManagerViewModel,
) {
    Text(
        text = stringResource(R.string.category_custom_title),
        style = MaterialTheme.typography.titleMedium,
    )
    if (state.customCategories.isEmpty()) {
        Text(
            text = stringResource(R.string.category_custom_empty),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    state.customCategories.forEach { category ->
        CategoryRow(
            category = category,
            isRenaming = state.renamingId == category.id,
            renameText = state.renameText,
            enabled = !state.isBusy,
            onRenameStart = { viewModel.onRenameStart(category) },
            onRenameTextChange = viewModel::onRenameTextChange,
            onRenameConfirm = viewModel::onRenameConfirm,
            onRenameCancel = viewModel::onRenameCancel,
            onArchive = { viewModel.onArchive(category) },
            onRestore = { viewModel.onRestore(category) },
        )
    }
}

/**
 * 内置分类：**只列出来，没有按钮**（`AC-7`），并说明为什么。
 *
 * 刻意不是"按钮禁用"：那会让人反复点、猜自己是不是操作错了。
 * "这里没有这个操作，原因是……" 是更诚实的表达。
 */
@Composable
private fun PresetCategoriesSection(state: CategoryManagerUiState) {
    Text(
        text = stringResource(R.string.category_preset_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.category_preset_readonly),
        style = MaterialTheme.typography.bodySmall,
    )
    state.presetCategories.forEach { category ->
        Text(text = category.displayName, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NewCategorySection(
    state: CategoryManagerUiState,
    viewModel: CategoryManagerViewModel,
) {
    OutlinedTextField(
        value = state.newName,
        onValueChange = viewModel::onNewNameChange,
        label = { Text(stringResource(R.string.category_new_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.category_direction_label),
        style = MaterialTheme.typography.labelLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EntryDirection.entries.forEach { direction ->
            FilterChip(
                selected = direction in state.newDirections,
                onClick = { viewModel.onNewDirectionToggled(direction) },
                label = { Text(direction.directionLabel()) },
            )
        }
    }

    Button(
        onClick = viewModel::onCreate,
        enabled = state.canCreate,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.category_create))
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isRenaming: Boolean,
    renameText: String,
    enabled: Boolean,
    onRenameStart: () -> Unit,
    onRenameTextChange: (String) -> Unit,
    onRenameConfirm: () -> Unit,
    onRenameCancel: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    if (isRenaming) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = renameText,
                onValueChange = onRenameTextChange,
                label = { Text(stringResource(R.string.category_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRenameConfirm, enabled = enabled) {
                    Text(stringResource(R.string.category_rename_confirm))
                }
                TextButton(onClick = onRenameCancel) {
                    Text(stringResource(R.string.ledger_cancel))
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // 归档的分类在这里显示出来：用户要能把它恢复，而"看不见"就没法恢复
                text = if (category.archived) {
                    category.displayName + " " + stringResource(R.string.category_archived_mark)
                } else {
                    category.displayName
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRenameStart, enabled = enabled) {
                    Text(stringResource(R.string.category_rename))
                }
                if (category.archived) {
                    TextButton(onClick = onRestore, enabled = enabled) {
                        Text(stringResource(R.string.category_restore))
                    }
                } else {
                    // 「归档」而不是「删除」：历史条目引用了这个分类（ADR-0009）
                    TextButton(onClick = onArchive, enabled = enabled) {
                        Text(stringResource(R.string.category_archive))
                    }
                }
            }
        }
    }
}
