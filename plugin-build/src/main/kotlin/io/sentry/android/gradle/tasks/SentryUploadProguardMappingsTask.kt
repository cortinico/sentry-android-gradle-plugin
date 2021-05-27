package io.sentry.android.gradle.tasks

import java.io.File
import org.apache.tools.ant.taskdefs.condition.Os
import org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CacheableTask
abstract class SentryUploadProguardMappingsTask : Exec() {

    init {
        description = "Uploads the proguard mappings file to Sentry"
    }

    @get:Input
    abstract val cliExecutable: Property<String>

    @get:Input
    abstract val uuidDirectory: DirectoryProperty

    @get:Internal
    val uuidFile: Provider<RegularFile>
        get() = uuidDirectory.file("sentry-debug-meta.properties")

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingsFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sentryProperties: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val sentryOrganization: Property<String>

    @get:Input
    @get:Optional
    abstract val sentryProject: Property<String>

    @get:Input
    abstract val autoUpload: Property<Boolean>

    override fun exec() {
        computeCommandLineArgs().let {
            commandLine(it)
            logger.info("cli args: $it")
        }
        setSentryPropertiesEnv()
        super.exec()
    }

    internal fun setSentryPropertiesEnv() {
        val sentryProperties = sentryProperties.orNull
        if (sentryProperties != null) {
            environment("SENTRY_PROPERTIES", sentryProperties)
        } else {
            logger.info("propsFile is null")
        }
    }

    internal fun computeCommandLineArgs(): List<String> {
        val uuid = readUuidFromFile(uuidFile.get().asFile)
        val args = mutableListOf(
            cliExecutable.get(),
            "upload-proguard",
            "--uuid",
            uuid,
            mappingsFile.get().toString()
        )

        if (!autoUpload.get()) {
            args.add("--no-upload")
        }

        sentryOrganization.orNull?.let {
            args.add("--org")
            args.add(it)
        }

        sentryProject.orNull?.let {
            args.add("--project")
            args.add(it)
        }

        if (Os.isFamily(FAMILY_WINDOWS)) {
            args.add(0, "cmd")
            args.add(1, "/c")
        }
        return args
    }

    companion object {
        private const val PROPERTY_PREFIX = "io.sentry.ProguardUuids="

        fun readUuidFromFile(file: File): String {
            check(file.exists()) {
                "UUID properties file is missing"
            }
            val content = file.readText().trim()
            check(content.startsWith(PROPERTY_PREFIX)) {
                "io.sentry.ProguardUuids property is missing"
            }
            return content.removePrefix(PROPERTY_PREFIX)
        }
    }
}
