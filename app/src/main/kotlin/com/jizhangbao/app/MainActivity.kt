package com.jizhangbao.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jizhangbao.core.ui.theme.JizhangbaoTheme
import com.jizhangbao.insight.presentation.MonthlyTotalsRoute
import com.jizhangbao.ledger.presentation.LedgerRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一的 Activity，承载 Compose 宿主。
 *
 * ⚠️ **本类不得包含业务规则**（R8）：它只做三件事——装主题、起 Compose、
 * 把各上下文提供的界面**拼装**起来。
 *
 * 不加外层 Scaffold：记账界面自带 Scaffold 与 TopAppBar，
 * 外面再套一层会多出一份无人使用的内边距（lint 的
 * `UnusedMaterial3ScaffoldPaddingParameter` 就是这么报出来的）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JizhangbaoTheme {
                HomeScreen()
            }
        }
    }
}

/**
 * 组合根负责把两个上下文拼在一起。
 *
 * ## 为什么拼装在这里，而不是让记账界面直接显示合计
 *
 * `REQ-002` 的合计属于 Insight，而 R2 禁止 `:feature:*` 之间互相依赖 ——
 * Ledger **不能**引用 Insight（反之亦然）。`:app` 是唯一同时看得见两者的模块，
 * 所以"把合计放在记账界面上方"这件事只能在这里做。
 *
 * 两边的接口都是**通用**的，因此没有互相泄露：
 * - Ledger 提供一个顶部插槽（它不知道插槽里会放什么）；
 * - Insight 提供一个 `refreshSignal`（它不认识账本，只知道自己该重算了）。
 *
 * ## 修订号是怎么把两者连起来的
 *
 * 记账或删除成功后，Ledger 状态里的 `entriesRevision` +1 → 这里观察到变化 →
 * `MonthlyTotalsRoute` 收到新的 `refreshSignal` → 合计重算。
 * 用户因此看到合计**立即**跟着变（`REQ-002/AC-4` `AC-5`），不需要切界面。
 */
@Composable
private fun HomeScreen() {
    var entriesRevision by remember { mutableIntStateOf(0) }

    LedgerRoute(
        onEntriesChanged = { entriesRevision++ },
        header = { MonthlyTotalsRoute(refreshSignal = entriesRevision) },
    )
}
