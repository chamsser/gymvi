import groovy.json.JsonSlurper
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val configuredApiBaseUrl = providers.gradleProperty("GYMVI_API_BASE_URL")
    .orElse(providers.environmentVariable("GYMVI_API_BASE_URL"))
val naverMapsClientId = providers.gradleProperty("GYMVI_NAVER_MAPS_CLIENT_ID")
    .orElse(providers.environmentVariable("GYMVI_NAVER_MAPS_CLIENT_ID"))
    .orElse("")
val liveUiEndpoint = providers.gradleProperty("GYMVI_LIVE_UI_ENDPOINT")
    .orElse(providers.environmentVariable("GYMVI_LIVE_UI_ENDPOINT"))
    .orElse("")
val liveUiToken = providers.gradleProperty("GYMVI_LIVE_UI_TOKEN")
    .orElse(providers.environmentVariable("GYMVI_LIVE_UI_TOKEN"))
    .orElse("")

android {
    namespace = "io.github.chamsser.gymvi"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.chamsser.gymvi"
        minSdk = 26
        targetSdk = 37
        versionCode = 59
        versionName = "0.3.29-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "API_BASE_URL",
                configuredApiBaseUrl.orElse("http://10.0.2.2:8080").get().asBuildConfigString(),
            )
            buildConfigField("String", "NAVER_MAPS_CLIENT_ID", naverMapsClientId.get().asBuildConfigString())
            buildConfigField("String", "LIVE_UI_ENDPOINT", liveUiEndpoint.get().asBuildConfigString())
            buildConfigField("String", "LIVE_UI_TOKEN", liveUiToken.get().asBuildConfigString())
        }
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "API_BASE_URL",
                configuredApiBaseUrl.orElse("").get().asBuildConfigString(),
            )
            buildConfigField("String", "NAVER_MAPS_CLIENT_ID", naverMapsClientId.get().asBuildConfigString())
            buildConfigField("String", "LIVE_UI_ENDPOINT", "".asBuildConfigString())
            buildConfigField("String", "LIVE_UI_TOKEN", "".asBuildConfigString())
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.naver.maps)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.json.java)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val verifyOpenSourceLicenses = tasks.register<VerifyOpenSourceLicenses>("verifyOpenSourceLicenses") {
    group = "verification"
    description = "Checks the open source license data against the packaged runtime artifacts."
    val release = configurations.named("releaseRuntimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }
    val debug = configurations.named("debugRuntimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }
    releaseArtifacts.set(VerifyOpenSourceLicenses.describe(release))
    debugArtifacts.set(VerifyOpenSourceLicenses.describe(debug))
    artifactFiles.from(VerifyOpenSourceLicenses.files(release), VerifyOpenSourceLicenses.files(debug))
    mainData.set(layout.projectDirectory.dir("src/main/assets/open_source_licenses"))
    debugData.set(layout.projectDirectory.dir("src/debug/assets/open_source_licenses"))
    report.set(layout.buildDirectory.file("reports/open-source-licenses/verify.txt"))
}

tasks.named("preBuild") {
    dependsOn(verifyOpenSourceLicenses)
}

/**
 * Keeps the static open source license data in step with what each variant packages.
 *
 * The release list must equal the release runtime artifacts and the debug list must equal the
 * release list plus the debug-only entries. Every license, notice or third-party license file
 * inside a packaged artifact must match a text the owning library lists, unless the library opens
 * its own notice screen.
 */
abstract class VerifyOpenSourceLicenses : DefaultTask() {
    @get:Input
    abstract val releaseArtifacts: ListProperty<String>

    @get:Input
    abstract val debugArtifacts: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifactFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainData: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val debugData: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    private class Library(
        val name: String,
        val version: String,
        val artifacts: List<String>,
        val texts: List<String>,
        val opens: String?,
    )

    private class LicenseText(val title: String, val file: String?, val url: String?)

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        val mainDir = mainData.get().asFile
        val mainRoot = readJson(mainDir.resolve(MAIN_INDEX), problems)
        val debugRoot = readJson(debugData.get().asFile.resolve(DEBUG_INDEX), problems)
        if (debugRoot?.containsKey("texts") == true) {
            problems += "$DEBUG_INDEX: texts belong in $MAIN_INDEX"
        }
        val mainLibraries = readLibraries(mainRoot, MAIN_INDEX, problems)
        val debugLibraries = readLibraries(debugRoot, DEBUG_INDEX, problems)
        val libraries = mainLibraries + debugLibraries
        val texts = readTexts(mainRoot, problems)
        val contents = readTextContents(mainDir, texts, problems)

        val referenced = mutableSetOf<String>()
        for (library in libraries) {
            val where = "${library.name} ${library.version}"
            if (library.artifacts.first().substringAfterLast(':') != library.version) {
                problems += "$where: version does not match ${library.artifacts.first()}"
            }
            if (library.opens != null && library.opens !in KNOWN_SCREENS) {
                problems += "$where: unknown screen ${library.opens}"
            }
            if (library.texts.toSet().size != library.texts.size) {
                problems += "$where: a text is listed twice"
            }
            for (id in library.texts) {
                if (id !in texts) problems += "$where: unknown text $id"
            }
            referenced += library.texts
        }
        for (id in texts.keys - referenced) {
            problems += "text $id is not used by any library"
        }

        val owners = mutableMapOf<String, Library>()
        for (library in libraries) {
            for (artifact in library.artifacts) {
                if (owners.put(artifact, library) != null) problems += "$artifact is listed twice"
            }
        }
        val release = parseArtifacts(releaseArtifacts.get())
        val debug = parseArtifacts(debugArtifacts.get())
        compare("release", release.keys, mainLibraries.flatMap { it.artifacts }.toSet(), problems)
        compare("debug", debug.keys, libraries.flatMap { it.artifacts }.toSet(), problems)

        val scanned = mutableSetOf<File>()
        for ((coordinate, files) in release.entries + debug.entries) {
            val library = owners[coordinate] ?: continue
            if (library.opens != null) continue
            for (file in files) {
                if (scanned.add(file)) checkBundledNotices(coordinate, file, library, texts, contents, problems)
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Open source license data is out of date:\n" + problems.distinct().joinToString("\n") { "- $it" },
            )
        }
        report.get().asFile.writeText(
            buildString {
                appendLine("release artifacts: ${release.size}")
                appendLine("debug artifacts: ${debug.size}")
                appendLine("libraries: ${mainLibraries.size} + ${debugLibraries.size} debug only")
                appendLine("texts: ${texts.size}")
            },
        )
    }

    private fun readJson(file: File, problems: MutableList<String>): Map<*, *>? {
        if (!file.isFile) {
            problems += "${file.name} is missing"
            return null
        }
        val root = runCatching { JsonSlurper().parse(file, "UTF-8") }.getOrNull() as? Map<*, *>
        if (root == null) problems += "${file.name} is not a JSON object"
        return root
    }

    private fun readLibraries(root: Map<*, *>?, source: String, problems: MutableList<String>): List<Library> {
        if (root == null) return emptyList()
        val items = root["libraries"] as? List<*>
        if (items == null) {
            problems += "$source: libraries must be a list"
            return emptyList()
        }
        return items.mapIndexedNotNull { index, item ->
            val entry = item as? Map<*, *>
            val name = entry?.get("name") as? String
            val version = entry?.get("version") as? String
            val artifacts = (entry?.get("artifacts") as? List<*>)?.map { it as? String }
            val textIds = (entry?.get("texts") as? List<*>)?.map { it as? String }
            val opens = entry?.get("opens") as? String
            val where = "$source libraries[$index]" + if (name != null) " ($name)" else ""
            when {
                name.isNullOrBlank() || version.isNullOrBlank() -> {
                    problems += "$where: name and version are required"
                    null
                }
                artifacts.isNullOrEmpty() || artifacts.any { it == null || !COORDINATE.matches(it) } -> {
                    problems += "$where: artifacts must be group:name:version coordinates"
                    null
                }
                (textIds == null) == (opens == null) -> {
                    problems += "$where: exactly one of texts or opens is required"
                    null
                }
                textIds != null && (textIds.isEmpty() || textIds.any { it.isNullOrBlank() }) -> {
                    problems += "$where: texts must not be empty"
                    null
                }
                else -> Library(name, version, artifacts.filterNotNull(), textIds?.filterNotNull().orEmpty(), opens)
            }
        }
    }

    private fun readTexts(root: Map<*, *>?, problems: MutableList<String>): Map<String, LicenseText> {
        if (root == null) return emptyMap()
        val entries = root["texts"] as? Map<*, *>
        if (entries == null) {
            problems += "$MAIN_INDEX: texts must be an object"
            return emptyMap()
        }
        val texts = linkedMapOf<String, LicenseText>()
        for ((key, value) in entries) {
            val entry = value as? Map<*, *>
            val title = entry?.get("title") as? String
            val file = entry?.get("file") as? String
            val url = entry?.get("url") as? String
            when {
                key !is String || title.isNullOrBlank() -> problems += "text $key: title is required"
                (file == null) == (url == null) -> problems += "text $key: exactly one of file or url is required"
                url != null && !url.startsWith("https://") -> problems += "text $key: url must use https"
                file != null && !TEXT_FILE.matches(file) -> problems += "text $key: file must be a path under texts/"
                else -> texts[key] = LicenseText(title, file, url)
            }
        }
        return texts
    }

    private fun readTextContents(
        mainDir: File,
        texts: Map<String, LicenseText>,
        problems: MutableList<String>,
    ): Map<String, String> {
        val contents = mutableMapOf<String, String>()
        val files = mutableSetOf<String>()
        for ((id, text) in texts) {
            val path = text.file ?: continue
            files += path
            val file = mainDir.resolve(path)
            val content = if (file.isFile) decode(file.readBytes()) else null
            when {
                !file.isFile -> problems += "text $id: $path is missing"
                content == null -> problems += "text $id: $path is not UTF-8"
                content.isBlank() -> problems += "text $id: $path is empty"
                normalize(content) != content ->
                    problems += "text $id: $path must use LF line endings, no trailing spaces and one final newline"
                else -> contents[id] = content
            }
        }
        mainDir.resolve("texts").walkTopDown().filter { it.isFile }.forEach { file ->
            val path = file.relativeTo(mainDir).invariantSeparatorsPath
            if (path !in files) problems += "$path is not used by any text"
        }
        return contents
    }

    private fun parseArtifacts(values: List<String>): Map<String, Set<File>> {
        val artifacts = sortedMapOf<String, MutableSet<File>>()
        for (value in values) {
            val coordinate = value.substringBefore('\t')
            artifacts.getOrPut(coordinate) { mutableSetOf() } += File(value.substringAfter('\t'))
        }
        return artifacts
    }

    private fun compare(variant: String, packaged: Set<String>, listed: Set<String>, problems: MutableList<String>) {
        val missing = packaged - listed
        val stale = listed - packaged
        val staleByModule = stale.groupBy { it.substringBeforeLast(':') }
        for (coordinate in missing.sorted()) {
            val module = coordinate.substringBeforeLast(':')
            val previous = staleByModule[module]
            problems += if (previous != null) {
                "$variant: $module changed from ${previous.joinToString { it.substringAfterLast(':') }} " +
                    "to ${coordinate.substringAfterLast(':')}"
            } else {
                "$variant: $coordinate is packaged but not listed"
            }
        }
        val changedModules = missing.map { it.substringBeforeLast(':') }.toSet()
        for (coordinate in stale.sorted()) {
            if (coordinate.substringBeforeLast(':') !in changedModules) {
                problems += "$variant: $coordinate is listed but not packaged"
            }
        }
    }

    private fun checkBundledNotices(
        coordinate: String,
        file: File,
        library: Library,
        texts: Map<String, LicenseText>,
        contents: Map<String, String>,
        problems: MutableList<String>,
    ) {
        val listed = library.texts.mapNotNull { id -> contents[id]?.let { texts.getValue(id).title to it } }
        val listedContents = listed.map { it.second }.toSet()
        val entries = bundledNoticeEntries(file)
        for ((path, bytes) in entries) {
            if (THIRD_PARTY.matches(path.substringAfterLast('/'))) continue
            val content = decode(bytes)?.let(::normalize)
            if (content == null || content !in listedContents) {
                problems += "$coordinate: $path is not one of the texts listed for ${library.name}"
            }
        }
        val thirdParty = entries.filter { THIRD_PARTY.matches(it.first.substringAfterLast('/')) }
            .groupBy { it.first.substringBeforeLast('.') }
        for ((base, pair) in thirdParty) {
            val index = pair.firstOrNull { it.first == "$base.json" }?.second
            val text = pair.firstOrNull { it.first == "$base.txt" }?.second
            val components = index?.let { runCatching { JsonSlurper().parse(it, "UTF-8") }.getOrNull() } as? Map<*, *>
            if (components == null || text == null) {
                problems += "$coordinate: $base.json and $base.txt must both be readable"
                continue
            }
            for ((name, range) in components) {
                val start = ((range as? Map<*, *>)?.get("start") as? Number)?.toInt()
                val length = ((range as? Map<*, *>)?.get("length") as? Number)?.toInt()
                val body = if (start != null && length != null && start >= 0 && length >= 0 && start + length <= text.size) {
                    decode(text.copyOfRange(start, start + length))?.let(::normalize)
                } else {
                    null
                }
                if (body == null || listed.none { it.first == name && it.second == body }) {
                    problems += "$coordinate: third-party text '$name' is not listed for ${library.name}"
                }
            }
        }
    }

    private fun bundledNoticeEntries(file: File): List<Pair<String, ByteArray>> {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipFile(file).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                if (isNoticeFile(entry.name)) entries += entry.name to zip.getInputStream(entry).readBytes()
                if (entry.name.endsWith(".jar")) {
                    ZipInputStream(zip.getInputStream(entry)).use { nested ->
                        while (true) {
                            val inner = nested.nextEntry ?: break
                            if (!inner.isDirectory && isNoticeFile(inner.name)) {
                                entries += "${entry.name}!/${inner.name}" to nested.readBytes()
                            }
                        }
                    }
                }
            }
        }
        return entries
    }

    private fun isNoticeFile(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return !name.endsWith(".class") && (NOTICE_NAME.matches(name) || THIRD_PARTY.matches(name))
    }

    private fun decode(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun normalize(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trimEnd() }
        val first = lines.indexOfFirst { it.isNotEmpty() }
        if (first < 0) return ""
        val last = lines.indexOfLast { it.isNotEmpty() }
        return lines.subList(first, last + 1).joinToString("\n") + "\n"
    }

    companion object {
        private const val MAIN_INDEX = "libraries.json"
        private const val DEBUG_INDEX = "libraries-debug.json"
        private val KNOWN_SCREENS = setOf("naver-map-sdk")
        private val COORDINATE = Regex("[^:\\s]+:[^:\\s]+:[^:\\s]+")
        private val TEXT_FILE = Regex("texts/([a-z0-9][a-z0-9.-]*/)*[a-z0-9][a-z0-9.-]*\\.txt")
        private val NOTICE_NAME =
            Regex("(licen[cs]es?|notice|copying|copyright|patents|al2\\.0|lgpl2\\.1)([-._][^/]*)?", RegexOption.IGNORE_CASE)
        private val THIRD_PARTY = Regex("third_party_licenses\\.(json|txt)")

        fun describe(artifacts: Provider<Set<ResolvedArtifactResult>>): Provider<List<String>> =
            artifacts.map { resolved ->
                resolved.map { artifact ->
                    val id = artifact.id.componentIdentifier
                    val coordinate = if (id is ModuleComponentIdentifier) {
                        "${id.group}:${id.module}:${id.version}"
                    } else {
                        id.displayName
                    }
                    coordinate + "\t" + artifact.file.absolutePath
                }
            }

        fun files(artifacts: Provider<Set<ResolvedArtifactResult>>): Provider<List<File>> =
            artifacts.map { resolved -> resolved.map { it.file } }
    }
}
