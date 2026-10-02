package com.jizhangbao.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jizhangbao.core.ui.theme.JizhangbaoTheme
import com.jizhangbao.ledger.presentation.LedgerRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一的 Activity，承载 Compose 宿主。
 *
 * ⚠️ **本类不得包含业务规则**（R8）：它只做三件事——装主题、起 Compose、
 * 把首屏指向某个上下文提供的界面。界面本身属于 `:feature:*`。
 *
 * 首屏目前直接是 Ledger 的记账界面（`REQ-001`）。等有了第二个上下文与导航，
 * 这里会换成一个导航宿主——那时才需要引入 navigation 依赖（版本已核实记录在目录里）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JizhangbaoTheme {
                // 不加外层 Scaffold：记账界面自带 Scaffold 与 TopAppBar，
                // 外面再套一层会多出一份无人使用的内边距（lint 的
                // UnusedMaterial3ScaffoldPaddingParameter 就是这么报出来的）
                LedgerRoute()
            }
        }
    }
}
