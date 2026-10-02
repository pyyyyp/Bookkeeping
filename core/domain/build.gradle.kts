plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// ─────────────────────────────────────────────────────────────
// 本模块是共享内核，必须保持「零框架依赖」。
// 它不是 Android Library 而是 kotlin("jvm") 模块，因此
// 没有 Android 类路径 —— 违反 R3 不是被检查出来，而是写不出来。
// 见 docs/30-architecture/ADR-0001-tech-stack.md 决策 4
// ─────────────────────────────────────────────────────────────
