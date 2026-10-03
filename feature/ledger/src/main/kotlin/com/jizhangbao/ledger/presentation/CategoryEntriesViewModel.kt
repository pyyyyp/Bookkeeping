package com.jizhangbao.ledger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhangbao.core.domain.Outcome
import com.jizhangbao.core.domain.toTimeRange
import com.jizhangbao.ledger.application.LoadCategoryEntriesUseCase
import com.jizhangbao.ledger.domain.model.CategoryId
import com.jizhangbao.ledger.domain.model.LedgerEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 分类下钻清单的状态（`REQ-007`）。
 *
 * **没有金额字段**：清单里的条目金额之和由界面自己加吗？不 —— 界面**只显示条目**，
 * 而"这个分类一共多少"由占比行负责（那个数字来自 SQL 的 `GROUP BY`）。
 * 在这里再算一遍合计，就又多了一个可能不一致的数字（`BR-1`）。
 */
internal data class CategoryEntriesUiState(
    /** 用于标题：用户要知道自己在看哪个分类（`AC-1`）。 */
    val categoryName: String = "",
    val month: YearMonth? = null,
    val entries: List<LedgerEntry> = emptyList(),
    /** 坏行计数，与主列表同一套处理（`REQ-006/AC-3`）。 */
    val unreadableEntries: Int = 0,
    /** 读失败与"这个月没有"是两件事（`REQ-006/AC-2` 的同一原则）。 */
    val hasFailure: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * 下钻清单的 ViewModel（`REQ-007`）。
 *
 * 为什么单独一个 ViewModel 而不是塞进 [LedgerViewModel]：那个已经背着表单、
 * 编辑、删除、分类管理四件事。**这个界面与记账页没有共享状态**——
 * 它只回答"某个分类某个月的条目是什么"，而且**只读**（`BR-3`）。
 *
 * 月份口径走内核的 `toTimeRange`，与合计/占比**同一个函数**（`BR-1`）：
 * 这里是"一致性靠结构保证"而不是"靠我记得改两处"。
 */
@HiltViewModel
internal class CategoryEntriesViewModel @Inject constructor(
    private val loadCategoryEntries: LoadCategoryEntriesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryEntriesUiState())
    val uiState: StateFlow<CategoryEntriesUiState> = _uiState.asStateFlow()

    /**
     * 打开某个分类、某个月的清单（`AC-1`）。
     *
     * @param categoryKey 内核读模型里的**不透明标识**（`BR-4`）。到这里它已经是
     *   Ledger 自己的 `CategoryId` —— 是组合根原样转交的，中间没有任何人解释过它。
     */
    fun load(categoryKey: String, categoryName: String, month: YearMonth, zone: ZoneId) {
        _uiState.value = CategoryEntriesUiState(
            categoryName = categoryName,
            month = month,
            isLoading = true,
        )

        viewModelScope.launch {
            val loaded = loadCategoryEntries(CategoryId(categoryKey), month.toTimeRange(zone))
            _uiState.value = when (loaded) {
                is Outcome.Ok -> CategoryEntriesUiState(
                    categoryName = categoryName,
                    month = month,
                    entries = loaded.value.entries,
                    unreadableEntries = loaded.value.unreadable,
                    isLoading = false,
                )
                // 读失败说读的事（REQ-006/AC-2）；空态与失败态分开（AC-3）
                is Outcome.Err -> CategoryEntriesUiState(
                    categoryName = categoryName,
                    month = month,
                    hasFailure = true,
                    isLoading = false,
                )
            }
        }
    }
}
