package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.SemanticVersion
import io.github.shizukajiku.imagewatch.domain.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Estado observable de la autoactualización: un chequeo al arrancar, otro cada [period] mientras la
 * app corre -vive en bandeja días enteros-, y `checkNow()` para el botón de Ajustes. Serializa los
 * chequeos con un `Mutex`: si hay uno en vuelo, el siguiente no hace nada.
 *
 * `pendingManifest` guarda el último `Available` para que el instalador sepa qué descargar. Un
 * `CheckFailed` de un chequeo posterior **no lo borra**: se mantiene lo último bueno, igual que
 * hace `SettingsViewModel` con los campos numéricos.
 */
class UpdateService(
    private val checker: UpdateCheck,
    private val currentVersion: SemanticVersion,
    private val scope: CoroutineScope,
    private val period: Duration = 24.hours,
) {
    private val mutablePhase = MutableStateFlow<UpdatePhase>(UpdatePhase.Idle)
    val phase: StateFlow<UpdatePhase> = mutablePhase.asStateFlow()

    @Volatile
    var pendingManifest: UpdateManifest? = null
        private set

    private val checking = Mutex()

    fun start() {
        scope.launch {
            check()
            while (isActive) {
                delay(period)
                check()
            }
        }
    }

    fun checkNow() {
        scope.launch { check() }
    }

    fun reportDownloadProgress(fraction: Float?) {
        mutablePhase.value = UpdatePhase.Downloading(fraction)
    }

    fun reportDownloadFailed(reason: String) {
        mutablePhase.value = UpdatePhase.Failed(reason)
    }

    fun markReadyToApply() {
        val version = pendingManifest?.latestVersion?.toString() ?: return
        mutablePhase.value = UpdatePhase.ReadyToApply(version)
    }

    private suspend fun check() {
        if (!checking.tryLock()) return
        try {
            val previous = mutablePhase.value
            mutablePhase.value = UpdatePhase.Checking
            when (val status = checker.check(currentVersion)) {
                is UpdateStatus.UpToDate -> {
                    pendingManifest = null
                    mutablePhase.value = UpdatePhase.UpToDate
                }

                is UpdateStatus.Available -> {
                    pendingManifest = status.manifest
                    mutablePhase.value = UpdatePhase.Available(
                        status.manifest.latestVersion.toString(),
                        status.manifest.notes,
                    )
                }

                is UpdateStatus.CheckFailed -> {
                    mutablePhase.value = when (previous) {
                        is UpdatePhase.Available,
                        is UpdatePhase.Downloading,
                        is UpdatePhase.ReadyToApply,
                        -> previous

                        else -> UpdatePhase.Failed(status.reason)
                    }
                }
            }
        } finally {
            checking.unlock()
        }
    }
}
