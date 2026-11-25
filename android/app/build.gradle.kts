import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Backend base URL comes from android/local.properties (gitignored): tindi.baseUrl=http://HOST:7000/api/v1
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tindiBaseUrl: String = (localProps.getProperty("tindi.baseUrl") ?: "").trim().trimEnd('/')
if (tindiBaseUrl.isEmpty()) {
    logger.warn("tindi.baseUrl is not set in android/local.properties; the app will not reach a server. See local.properties.example.")
}
val tindiHost: String = Regex("^https?://([^/:]+)").find(tindiBaseUrl)?.groupValues?.get(1) ?: "invalid.example"

/** Writes the network security config: cleartext HTTP is allowed for the Tindi host only. */
abstract class GenerateNetworkConfig : DefaultTask() {
    @get:Input abstract val host: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = outputDir.get().asFile.resolve("xml").also { it.mkdirs() }
        dir.resolve("network_security_config.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">${host.get()}</domain>
    </domain-config>
</network-security-config>
""",
        )
    }
}

val generateNetworkConfig = tasks.register<GenerateNetworkConfig>("generateNetworkConfig") {
    host.set(tindiHost)
    outputDir.set(layout.buildDirectory.dir("generated/tindi/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateNetworkConfig, GenerateNetworkConfig::outputDir)
    }
}

android {
    namespace = "com.tindi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tindi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BASE_URL", "\"$tindiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
