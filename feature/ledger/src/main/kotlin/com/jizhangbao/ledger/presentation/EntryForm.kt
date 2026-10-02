package com.jizhangbao.ledger.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jizhangbao.core.domain.EntryDirection
import com.jizhangbao.ledger.R
import com.jizhangbao.ledger.domain.model.CategoryId
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 记账表单：金额、方向、分类、日期、备注、保存。
 *
 * 无状态（state + 回调），因此可以直接用任意状态预览与测试，不需要 ViewModel。
 * 独立成文件的理由：界面文件不该既是「页面骨架」又是「表单细节」。
 */
@Composable
internal fun EntryForm(
    state: LedgerUiState,
    zone: ZoneId,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (EntryDirection) -> Unit,
    onCategorySelected: (CategoryId) -> Unit,
    onDateClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AmountField(state = state, onAmountChange = onAmountChange)

        DirectionChips(selected = state.direction, onDirectionChange = onDirectionChange)

        CategoryChips(state = state, onCategorySelected = onCategorySelected)

        OutlinedButton(onClick = onDateClick) {
            Text(
                stringResource(R.string.ledger_date_label) + "：" +
                    state.occurredAt.atZone(zone).toLocalDate().format(DATE_FORMAT),
            )
        }

        OutlinedTextField(
            value = state.noteText,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.ledger_note_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.failure?.let { failure ->
            Text(
                text = stringResource(failure.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onSave,
            enabled = state.canPressSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (state.isSaving) R.string.ledger_saving else R.string.ledger_save))
        }
    }
}

@Composable
private fun AmountField(state: LedgerUiState, onAmountChange: (String) -> Unit) {
    OutlinedTextField(
        value = state.amountText,
        onValueChange = onAmountChange,
        label = { Text(stringResource(R.string.ledger_amount_label)) },
        singleLine = true,
        isError = state.amountError != null,
        supportingText = state.amountError?.let { error ->
            { Text(stringResource(error.messageRes())) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DirectionChips(
    selected: EntryDirection,
    onDirectionChange: (EntryDirection) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EntryDirection.entries.forEach { direction ->
            FilterChip(
                selected = direction == selected,
                onClick = { onDirectionChange(direction) },
                label = { Text(stringResource(direction.labelRes())) },
            )
        }
    }
}

@Composable
private fun CategoryChips(state: LedgerUiState, onCategorySelected: (CategoryId) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        state.selectableCategories.forEach { category ->
            FilterChip(
                selected = category.id == state.selectedCategoryId,
                onClick = { onCategorySelected(category.id) },
                label = { Text(category.displayName) },
            )
        }
    }
}
