plugins {
    `kotlin-dsl`
}

group = "com.jizhangbao.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // compileOnly 而非 implementation：这些插件的真正实现由**消费方构建**提供
    // （root build.gradle.kts 里 `apply false` 的声明把它们放上了插件类路径）。
    // 约定插件只按 id 应用它们，编译期需要它们的类型。
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "jizhangbao.android.library"
            implementationClass = "com.jizhangbao.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "jizhangbao.android.application"
            implementationClass = "com.jizhangbao.buildlogic.AndroidApplicationConventionPlugin"
        }
        // 应用在**根项目**上：注册 verifyDomainPurity / checkModuleDependencies 两个聚合校验任务
        register("architecture") {
            id = "jizhangbao.architecture"
            implementationClass = "com.jizhangbao.buildlogic.ArchitectureVerificationPlugin"
        }
    }
}
