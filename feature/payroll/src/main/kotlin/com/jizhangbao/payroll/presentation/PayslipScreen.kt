package com.jizhangbao.payroll.presentation

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
import com.jizhangbao.core.domain.DayType
import com.jizhangbao.payroll.R
import com.jizhangbao.payroll.domain.DailyIncome
import com.jizhangbao.payroll.domain.MonthlySalary
import com.jizhangbao.payroll.domain.Payslip
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 工资单页（`REQ-017`）。
 *
 * ## 四块，各回答一件事
 *
 * 1. **月薪**：没有它算不出工资单（`AC-1`）；
 * 2. **月份**：未来月份禁用（`AC-5`）；
 * 3. **合计与差额**：这个月该拿多少、以及**为什么对不上月薪**（`AC-2` / `AC-4`）；
 * 4. **逐日明细**：每一天的日期类型与倍率（`AC-3`）—— 这是"可解释"的落点。
 *
 * ⚠️ 文案全部走 `stringResource`（`R12`）；算术全部由 `Payslip` 给出（`BR-1`）。
 */
@Composable
fun PayslipScreen(
    modifier: Modifier = Modifier,
    viewModel: PayslipViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.payslip_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        SalarySection(
            salary = state.salary,
            enabled = !state.isBusy,
            onSave = viewModel::saveSalary,
        )
        MonthSection(
            month = state.month,
            canGoToNextMonth = state.canGoToNextMonth,
            onPrevious = viewModel::onPreviousMonth,
            onNext = viewModel::onNextMonth,
        )
        state.payslip?.let { payslip ->
            SummarySection(payslip)
            DaysSection(payslip)
        }
    }

    state.notice?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            confirmButton = {
                TextButton(onClick = viewModel::dismissNotice) {
                    Text(stringResource(R.string.payslip_dismiss))
                }
            },
            text = { Text(stringResource(R.string.payslip_storage_failed)) },
        )
    }
}

@Composable
private fun SalarySection(
    salary: MonthlySalary?,
    enabled: Boolean,
    onSave: (String, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var effectiveFrom by remember { mutableStateOf(LocalDate.now().toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.payslip_salary_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (salary == null) {
            Text(
                text = stringResource(R.string.payslip_salary_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.payslip_salary_current,
                    salary.amount.toString(),
                    salary.effectiveFrom.toString(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.payslip_salary_hint_raise),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(stringResource(R.string.payslip_salary_amount)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = effectiveFrom,
            onValueChange = { effectiveFrom = it },
            label = { Text(stringResource(R.string.payslip_salary_effective_from)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(amount, effectiveFrom) },
            enabled = enabled,
        ) {
            Text(stringResource(R.string.payslip_salary_save))
        }
        HorizontalDivider()
    }
}

@Composable
private fun MonthSection(
    month: YearMonth,
    canGoToNextMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious) {
            Text(stringResource(R.string.payslip_previous_month))
        }
        Text(
            text = stringResource(R.string.payslip_month, month.year, month.monthValue),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onNext, enabled = canGoToNextMonth) {
            Text(stringResource(R.string.payslip_next_month))
        }
    }
}

@Composable
private fun SummarySection(payslip: Payslip) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyValue(stringResource(R.string.payslip_daily_rate), payslip.dailyRate.toString())
        KeyValue(stringResource(R.string.payslip_amount_due), payslip.amountDue.toString())
        KeyValue(
            stringResource(R.string.payslip_difference),
            // 用带方向的措辞（与 REQ-009 的环比同一套）：差额可正可负，符号要看得懂
            if (payslip.differenceFromSalary.isNegative) {
                "− " + payslip.differenceFromSalary.magnitude.toString()
            } else {
                "+ " + payslip.differenceFromSalary.magnitude.toString()
            },
        )
        // AC-4：解释一句，否则用户会以为算错了
        Text(
            text = stringResource(R.string.payslip_difference_explain),
            style = MaterialTheme.typography.bodySmall,
        )
        if (payslip.holidayDataMissing) {
            // AC-6：不说的话，用户以为今年没有节假日
            Text(
                text = stringResource(R.string.payslip_holiday_data_missing),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DaysSection(payslip: Payslip) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.payslip_days_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (payslip.days.isEmpty()) {
            Text(
                text = stringResource(R.string.payslip_days_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // AC-3：全部逐日列出，不做折叠（Q-029 的决定）
        payslip.days.forEach { day -> DayRow(day) }
    }
}

@Composable
private fun DayRow(day: DailyIncome) {
    Text(
        text = stringResource(
            R.string.payslip_day_row,
            day.date.format(DAY_FORMAT),
            stringResource(dayTypeRes(day)),
            stringResource(R.string.payslip_multiplier, day.multiplier.factor),
            day.amount.toString(),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * 日期类型 → 文案。**住 presentation**：它只做"类型 → 资源 id"，
 * 不含任何文案与业务规则（文案在 `strings.xml`，规则在领域）。
 *
 * 曾经把它放进 `domain` 包并给自己编了段理由 —— 那段理由站不住：
 * 一个引用资源 id 的映射本来就不该进领域层。
 */
private fun dayTypeRes(day: DailyIncome): Int = when (day.dayType) {
    DayType.WORKDAY -> R.string.payslip_day_type_workday
    DayType.REST_DAY -> R.string.payslip_day_type_rest_day
    DayType.STATUTORY_HOLIDAY -> R.string.payslip_day_type_holiday
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
