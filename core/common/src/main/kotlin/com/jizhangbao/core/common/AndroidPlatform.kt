package com.jizhangbao.core.common

import android.os.Build

/**
 * 编译期证明本模块**具备** Android 类路径。
 *
 * 与 `:core:domain` 形成对照：那里写 `import android.os.Build` 会直接编译失败，
 * 因为它是 `kotlin("jvm")` 模块，没有 Android 类路径。
 *
 * 这就是 ADR-0001 决策 4 所说的「R3 的违反不是被检查出来，而是写不出来」——
 * 本文件的存在让这条规则可以被演示，而不只是被声称。
 */
object AndroidPlatform {

    val sdkInt: Int get() = Build.VERSION.SDK_INT

    val isAtLeastAndroid8: Boolean get() = sdkInt >= Build.VERSION_CODES.O
}
