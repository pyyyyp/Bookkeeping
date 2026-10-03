package com.jizhangbao.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口，同时是 Hilt 的依赖图根节点。
 *
 * 本类**不含任何业务规则**（R8）——它只负责启动框架。
 */
@HiltAndroidApp
class JizhangbaoApplication : Application()
