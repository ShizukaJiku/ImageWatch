package io.github.shizukajiku.imagewatch.infrastructure.persistence

import io.github.shizukajiku.imagewatch.application.ConfigStore
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import okio.Path
import kotlin.time.Duration.Companion.seconds

/** [ConfigStore] respaldado por un fichero JSON, sembrado una vez desde el entorno. */
class JsonConfigStore(private val file: Path, private val seed: AppConfig) : ConfigStore {
    private val lock = Lock()

    override fun load(): AppConfig = lock.withLock {
        if (!JsonFiles.fileSystem.exists(file)) {
            save(seed)
            return@withLock seed
        }
        toConfig(JsonFiles.read<ConfigDto>(file, DESCRIPTION))
    }

    override fun save(config: AppConfig) = lock.withLock {
        JsonFiles.write(file, toDto(config), DESCRIPTION)
    }

    private fun toConfig(dto: ConfigDto) = AppConfig(
        stateFile = seed.stateFile,
        remoteUrl = dto.remoteUrl,
        pollInterval = dto.pollIntervalSeconds.seconds,
        imageNames = dto.imageNames,
        simulationMode = dto.simulationMode,
        ignoreSslErrors = dto.ignoreSslErrors,
        theme = ThemePreference.valueOf(dto.theme),
        toastsEnabled = dto.toastsEnabled,
        toastDuration = dto.toastSeconds.seconds,
        soundsEnabled = dto.soundsEnabled,
        soundVolume = dto.soundVolume,
        mutedAll = dto.mutedAll,
        teamsEnabled = dto.teamsEnabled,
        teamsWebhookUrl = dto.teamsWebhookUrl,
    )

    private fun toDto(config: AppConfig) = ConfigDto(
        remoteUrl = config.remoteUrl,
        pollIntervalSeconds = config.pollInterval.inWholeSeconds,
        imageNames = config.imageNames,
        simulationMode = config.simulationMode,
        ignoreSslErrors = config.ignoreSslErrors,
        theme = config.theme.name,
        toastsEnabled = config.toastsEnabled,
        toastSeconds = config.toastDuration.inWholeSeconds,
        soundsEnabled = config.soundsEnabled,
        soundVolume = config.soundVolume,
        mutedAll = config.mutedAll,
        teamsEnabled = config.teamsEnabled,
        teamsWebhookUrl = config.teamsWebhookUrl,
    )

    private companion object {
        private const val DESCRIPTION = "la configuración"
    }
}
