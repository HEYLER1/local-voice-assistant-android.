import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
// Keep the two incompatible ONNX runtimes in separate ELF namespaces.
val soniqoArchive = configurations.create("soniqoArchive") { isTransitive = false }
dependencies.add(soniqoArchive.name, "audio.soniqo:speech:0.0.22@aar")
val isolatedSoniqo = layout.buildDirectory.file("isolated-sdk/soniqo.aar")
val isolateSoniqo by tasks.registering {
    inputs.files(soniqoArchive)
    outputs.file(isolatedSoniqo)
    doLast {
        val output = isolatedSoniqo.get().asFile
        output.parentFile.mkdirs()
        val old = "libonnxruntime.so".toByteArray()
        val replacement = "libsoniqo_ortx.so".toByteArray()
        ZipFile(soniqoArchive.singleFile).use { source ->
            ZipOutputStream(output.outputStream()).use { target ->
                source.entries().asSequence().forEach { entry ->
                    val name = entry.name.replace("libonnxruntime.so", "libsoniqo_ortx.so")
                    target.putNextEntry(ZipEntry(name))
                    if (!entry.isDirectory) {
                        val bytes = source.getInputStream(entry).use { it.readBytes() }
                        if (entry.name.startsWith("jni/") && entry.name.endsWith(".so")) {
                            for (i in 0..(bytes.size-old.size)) {
                                if (old.indices.all { bytes[i+it] == old[it] }) replacement.copyInto(bytes,i)
                            }
                        }
                        target.write(bytes)
                    }
                    target.closeEntry()
                }
            }
        }
    }
}
android {
    namespace = "com.heyler.voicelab"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.heyler.voicelab"
        minSdk = 31
        targetSdk = 37
        versionCode = 25
        versionName = "0.20-assistant"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs.pickFirsts += setOf("**/libonnxruntime.so","**/libc++_shared.so","**/libLiteRt.so"); resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("ai.moonshine:moonshine-voice:0.1.5")
    implementation(files(isolatedSoniqo).builtBy(isolateSoniqo))
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.core:core:1.19.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
