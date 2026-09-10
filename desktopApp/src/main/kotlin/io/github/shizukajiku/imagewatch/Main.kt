package io.github.shizukajiku.imagewatch

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import io.github.shizukajiku.imagewatch.application.AutostartPort
import io.github.shizukajiku.imagewatch.application.ConfigStore
import io.github.shizukajiku.imagewatch.application.ImageSource
import io.github.shizukajiku.imagewatch.application.PollingController
import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.application.TrackedImageStore
import io.github.shizukajiku.imagewatch.application.VersionPollingService
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.config.ThemePreference
import io.github.shizukajiku.imagewatch.infrastructure.os.WindowsAutostart
import io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonConfigStore
import io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonImageStateStore
import io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonSilencedImageStore
import io.github.shizukajiku.imagewatch.infrastructure.persistence.JsonTrackedImageStore
import io.github.shizukajiku.imagewatch.infrastructure.remote.EmptyImageSource
import io.github.shizukajiku.imagewatch.infrastructure.remote.HttpClientFactory
import io.github.shizukajiku.imagewatch.infrastructure.remote.HttpImageSource
import io.github.shizukajiku.imagewatch.infrastructure.remote.ReloadableImageSource
import io.github.shizukajiku.imagewatch.infrastructure.remote.SimulatedImageSource
import io.github.shizukajiku.imagewatch.ui.AlertIconPainter
import io.github.shizukajiku.imagewatch.ui.AppIconPainter
import io.github.shizukajiku.imagewatch.ui.components.TitleBar
import io.github.shizukajiku.imagewatch.ui.dialogs.ConfirmDialog
import io.github.shizukajiku.imagewatch.ui.dialogs.NameDialog
import io.github.shizukajiku.imagewatch.ui.images.ImagesScreen
import io.github.shizukajiku.imagewatch.ui.images.ImagesViewModel
import io.github.shizukajiku.imagewatch.ui.settings.SettingsScreen
import io.github.shizukajiku.imagewatch.ui.settings.SettingsViewModel
import io.github.shizukajiku.imagewatch.ui.sound.Sound
import io.github.shizukajiku.imagewatch.ui.sound.Sounds
import io.github.shizukajiku.imagewatch.ui.theme.ImageWatchTheme
import io.github.shizukajiku.imagewatch.ui.toast.ToastKind
import io.github.shizukajiku.imagewatch.ui.toast.ToastLayer
import io.github.shizukajiku.imagewatch.ui.toast.ToastNotificationPort
import io.github.shizukajiku.imagewatch.ui.toast.ToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Cada cuánto sube de versión el origen simulado. Solo aplica en modo simulación. */
private val SIMULATED_BUMP_EVERY: Duration = 20.seconds

private val LOG = LoggerFactory.getLogger("io.github.shizukajiku.imagewatch.Wiring")

/**
 * okio no tiene `resolveSibling`. Sin padre —una ruta que es solo un nombre de fichero— el
 * hermano cuelga del directorio de trabajo, que es lo que ese nombre significaba ya.
 */
private fun Path.sibling(name: String): Path = (parent ?: ".".toPath()).resolve(name)

/**
 * Los valores con los que se siembra el fichero de configuración la primera vez. A partir de ahí
 * manda el fichero: si el entorno prevaleciera siempre, la pantalla de ajustes no podría modificar
 * nada.
 *
 * Vive aquí y no junto a [AppConfig]: leer `System.getenv` es cableado, y el cableado es lo único
 * que aporta este módulo. `AppConfig` está en `commonMain`, donde no hay variables de entorno.
 *
 * En la app empaquetada (jpackage pone `jpackage.app-path`) una instalación limpia arranca **sin
 * datos de prueba**: sin simulación y sin imágenes sembradas. En desarrollo -`gradlew run`- sigue
 * arrancando en simulación con las cuatro imágenes de ejemplo. Las variables de entorno mandan por
 * encima de ambos en cualquier caso.
 */
private fun configFromEnvironment(): AppConfig {
    val env = System.getenv()
    fun get(name: String, fallback: String) = env[name] ?: fallback
    val packaged = System.getProperty("jpackage.app-path") != null
    val home = get("USERPROFILE", ".").toPath()
    return AppConfig(
        stateFile = get("NOTIFIER_STATE_FILE", home.resolve(".notifier/images.json").toString()),
        remoteUrl = get("IMAGE_VERSION_URL", ""),
        pollInterval = get("POLL_INTERVAL_SECONDS", "300").toLong().seconds,
        imageNames = get("IMAGE_NAMES", if (packaged) "" else "alpha,beta,gamma,delta")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() },
        simulationMode = get("SIMULATION_MODE", if (packaged) "false" else "true").toBoolean(),
        ignoreSslErrors = get("IGNORE_SSL_ERRORS", "true").toBoolean(),
        theme = ThemePreference.valueOf(get("THEME", "SYSTEM")),
        toastsEnabled = get("TOASTS_ENABLED", "true").toBoolean(),
        toastDuration = get("TOAST_SECONDS", "8").toLong().seconds,
        soundsEnabled = get("SOUNDS_ENABLED", "true").toBoolean(),
        soundVolume = get("SOUND_VOLUME", "0.5").toDouble(),
        mutedAll = get("MUTED_ALL", "false").toBoolean(),
    )
}

/**
 * Composition root. El cableado vive aquí y no en el dominio ni en la aplicación: `commonMain` no
 * sabe de variables de entorno, ni de ficheros, ni de HTTP, y este módulo existe justo para eso.
 */
private class Wiring {
    private val configStore: ConfigStore
    private val source: ReloadableImageSource
    val trackedImages: TrackedImageStore
    val silencedImages: SilencedImageStore

    /** Reloj propio de los avisos: no comparte ciclo de vida con la ventana ni con el sondeo. */
    val toastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Reloj propio del view model de la lista: mide el resaltado de «Ver» y el latido de las
     * novedades. Igual que `toastScope`, no comparte ciclo de vida con la ventana -sobrevive a
     * que se cierre- ni con el sondeo.
     */
    val imagesScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // La duración llega como lambda porque puede cambiar en Ajustes mientras la aplicación
    // corre; `config` se asigna en el init de abajo, pero como aquí solo se captura el acceso
    // -no su valor- basta con que exista antes de que se invoque, no antes de este punto.
    val toasts = ToastState(toastScope, { config.value.toastDuration })
    val service: VersionPollingService
    val controller: PollingController

    /** La configuración vigente. La interfaz la observa para el tema y los ajustes. */
    val config: MutableStateFlow<AppConfig>

    /** Cierto mientras la ventana principal tiene el foco. Lo actualiza la propia ventana. */
    val windowFocused = MutableStateFlow(false)

    /**
     * Arranque al iniciar sesión. El comando es el ejecutable actual: en la instalación es el
     * lanzador de la app; con `gradle run` es la JVM -donde el autostart no tiene sentido, pero
     * tampoco molesta-.
     */
    val autostart: AutostartPort = WindowsAutostart(
        label = "ImageWatch",
        command = listOf(ProcessHandle.current().info().command().orElse("imagewatch"), "--minimized"),
    )

    val sounds = Sounds(
        enabled = { config.value.soundsEnabled },
        volume = { config.value.soundVolume },
        windowFocused = { windowFocused.value },
    )

    init {
        val seed = configFromEnvironment()
        val seedFile = seed.stateFile.toPath()
        configStore = JsonConfigStore(seedFile.sibling("config.json"), seed)
        val loaded = configStore.load()
        // Sin comprobación de "falta la URL": una instalación limpia arranca sin simulación y sin
        // URL, y es un estado válido -lista vacía, el usuario pone la URL en Ajustes-. `sourceFor`
        // devuelve EmptyImageSource mientras no haya URL, así que no se construye un
        // HttpImageSource con cadena vacía.
        config = MutableStateFlow(loaded)
        source = ReloadableImageSource(sourceFor(loaded))
        // La ruta llega como texto -el dominio no sabe de ficheros- y se convierte aqui, que es
        // el lado que si conoce el sistema de ficheros.
        val stateFile = loaded.stateFile.toPath()
        val state = JsonImageStateStore(stateFile)
        trackedImages =
            JsonTrackedImageStore(
                stateFile.sibling("tracked-images.json"),
                loaded.imageNames,
            )
        silencedImages =
            JsonSilencedImageStore(stateFile.sibling("silenced-images.json"))
        service =
            VersionPollingService(
                source,
                state,
                // La configuración se pasa como lambda y no como valor: puede cambiar mientras la
                // aplicación corre, y `config::value` capturaría la propiedad, no su lectura.
                listOf(ToastNotificationPort(toasts, sounds, silencedImages) { config.value }),
                trackedImages,
            )
        controller = PollingController(service, loaded.pollInterval)
        sounds.preload()
    }

    /**
     * Aplica una configuración sobre la aplicación viva y la persiste. Devuelve el mensaje a
     * mostrar, o `null` si todo fue bien.
     *
     * El orden importa: primero se construye el origen —cuyo constructor es quien valida la URL— y
     * después se toca nada más. Así una URL inválida no deja el intervalo cambiado a medias.
     */
    fun applyConfig(candidate: AppConfig): String? {
        val current = config.value
        val sourceChanged =
            candidate.remoteUrl != current.remoteUrl ||
                candidate.simulationMode != current.simulationMode ||
                candidate.ignoreSslErrors != current.ignoreSslErrors
        val intervalChanged = candidate.pollInterval != current.pollInterval
        return runCatching {
            // Se construye —y por tanto se valida la URL— solo si cambió alguno de los tres
            // campos de los que depende el origen. Guardar solo el volumen o el tema no debe
            // tirar un HttpImageSource y un HttpClient nuevos para nada; pero si remoteUrl
            // cambió y sigue sin ser HTTPS, hay que construirlo igual aunque el modo
            // simulación no se haya tocado, para que el usuario vea el mensaje de validación.
            val replacement = if (sourceChanged) sourceFor(candidate) else null
            // updateInterval reinicia el sondeo (stop+start) y start() programa con retardo
            // inicial cero: propagarlo siempre dispararía una consulta de red inmediata al
            // guardar cualquier ajuste, aunque el intervalo no hubiera cambiado. No es una
            // optimización, es evitar que guardar el volumen reinicie el sondeo.
            // El fichero se escribe antes de mutar nada vivo. La URL ya quedó validada al
            // construir el reemplazo, así que adelantarlo no pierde ninguna comprobación; lo que
            // evita es que un fallo de E/S deje al controlador y al origen con la configuración
            // nueva mientras el disco y la pantalla siguen con la vieja.
            configStore.save(candidate)
            if (intervalChanged) {
                controller.updateInterval(candidate.pollInterval)
            }
            replacement?.let { source.swap(it) }
            config.value = candidate
            // Después de publicar: `enabled()` lee la configuración vigente, así que sonar antes
            // consultaba la anterior y activar los sonidos y guardar salía mudo.
            sounds.play(Sound.SUCCESS)
        }.fold(
            onSuccess = { null },
            onFailure = {
                // Aquí caen tanto los rechazos de validación (URL sin HTTPS, intervalo por
                // debajo del mínimo) como un fallo de E/S real al escribir config.json. El
                // mensaje que ve el usuario es el mismo en ambos casos, pero solo el segundo
                // necesita quedar en el registro para poder diagnosticarlo.
                LOG.error("No se pudo aplicar la configuración", it)
                it.message ?: "No se pudo aplicar la configuración"
            },
        )
    }

    /**
     * Ajustes a valores de fábrica. Las imágenes vigiladas y su versión vista no se tocan: solo
     * los campos de configuración. Seguro en caliente -`applyConfig` está pensado para eso-.
     */
    fun resetSettings(): String? {
        val factory = configFromEnvironment()
        return applyConfig(
            config.value.copy(
                remoteUrl = factory.remoteUrl,
                pollInterval = factory.pollInterval,
                ignoreSslErrors = factory.ignoreSslErrors,
                theme = factory.theme,
                toastsEnabled = factory.toastsEnabled,
                toastDuration = factory.toastDuration,
                soundsEnabled = factory.soundsEnabled,
                soundVolume = factory.soundVolume,
                mutedAll = factory.mutedAll,
            ),
        )
    }

    /**
     * Borra los cuatro ficheros JSON que la app guarda en este equipo y para el sondeo. No se
     * recablea `Wiring` en caliente -`service` tiene oyentes y el view model lo referencia-: el
     * llamador cierra la app, y volver a abrirla la siembra de cero desde el entorno, que es el
     * mismo camino que el primer arranque.
     */
    fun wipeLocalData(onDone: () -> Unit) {
        controller.stop()
        val base = configFromEnvironment().stateFile.toPath()
        listOf(
            base,
            base.sibling("config.json"),
            base.sibling("tracked-images.json"),
            base.sibling("silenced-images.json"),
        ).forEach { runCatching { FileSystem.SYSTEM.delete(it, mustExist = false) } }
        onDone()
    }

    private fun sourceFor(config: AppConfig): ImageSource = when {
        config.simulationMode -> SimulatedImageSource(Clock.System, SIMULATED_BUMP_EVERY)

        // Instalación limpia: sin URL todavía. HttpImageSource lanzaría al construirse con "".
        config.remoteUrl.isBlank() -> EmptyImageSource

        else -> HttpImageSource(
            HttpClientFactory.create(config.ignoreSslErrors),
            config.remoteUrl,
        )
    }
}

fun main(args: Array<String>) {
    // `--minimized` lo pone la clave Run del registro (WindowsAutostart): al iniciar sesion la
    // app arranca oculta en la bandeja. Un arranque normal -atajo, `gradlew run`- abre la ventana.
    val startHidden = "--minimized" in args

    val wiring = Wiring()
    wiring.controller.start()

    application {
        var windowVisible by remember { mutableStateOf(!startHidden) }
        // Se incrementa cada vez que algo pide traer la ventana al frente. La ventana lo observa
        // porque poner windowVisible a true no hace nada si ya era true: minimizada seguia
        // minimizada, y "Ver" no traia nada.
        var traerAlFrente by remember { mutableStateOf(0) }
        // Vive aqui y no dentro de MainScreen: el "Ver todas" del resumen de avisos necesita
        // poder devolver a la pantalla de Imagenes aunque el usuario estuviera en Ajustes.
        var screen by remember { mutableStateOf(Screen.IMAGES) }
        val windowState = rememberWindowState(size = DpSize(1120.dp, 720.dp))
        val config by wiring.config.collectAsState()

        // Vive en el scope de la aplicación, no en el de la ventana: el Tray necesita saber si
        // todo está fallando aunque la ventana esté cerrada, y reconstruirlo al abrirla perdería
        // el resaltado que un toast le hubiera puesto.
        val viewModel = remember {
            ImagesViewModel(
                wiring.service,
                wiring.trackedImages,
                wiring.silencedImages,
                wiring.controller,
                wiring.sounds,
                wiring.imagesScope,
                // Reconocer una imagen retira su aviso: lo que decia ya no es cierto. Llega como
                // funcion para que el view model de la lista no dependa de la capa de avisos.
                wiring.toasts::dismissFor,
            )
        }
        val state by viewModel.state.collectAsState()

        // Secuencia de cierre limpio: la usan «Salir» de la bandeja y «Borrar datos locales» de
        // Ajustes -tras borrar los ficheros, la app se cierra y se vuelve a abrir de cero-.
        val cerrarApp: () -> Unit = {
            viewModel.close()
            wiring.controller.close()
            wiring.sounds.close()
            wiring.toastScope.cancel()
            wiring.imagesScope.cancel()
            exitApplication()
        }

        // El Tray vive sin Window: es lo que permite que la aplicación resida en la bandeja y
        // que cerrar la ventana no mate el proceso.
        Tray(
            state = rememberTrayState(),
            icon = if (state.allFailing) AlertIconPainter else AppIconPainter,
            tooltip = if (state.allFailing) "ImageWatch — sin conexión" else "ImageWatch",
            onAction = {
                windowVisible = true
                traerAlFrente++
            },
            menu = {
                Item(
                    "Abrir",
                    onClick = {
                        windowVisible = true
                        traerAlFrente++
                    },
                )
                Item("Salir", onClick = cerrarApp)
            },
        )

        val toasts by wiring.toasts.toasts.collectAsState()

        // Con su propio tema: la capa de toasts vive fuera de la ventana, asi que sin esto
        // resolvia al esquema por defecto de Material -tarjeta gris lila, barra morada- e
        // ignoraba la preferencia de claro u oscuro que el usuario acababa de elegir.
        ImageWatchTheme(config.theme) {
            ToastLayer(
                toasts = toasts,
                onPause = wiring.toasts::pause,
                onResume = wiring.toasts::resume,
                onExitFinished = wiring.toasts::exitFinished,
                onAction = { name, kind ->
                    when (kind) {
                        ToastKind.NUEVA, ToastKind.SALTADAS -> {
                            windowVisible = true
                            name?.let(viewModel::highlight)
                        }

                        ToastKind.ERROR -> name?.let(viewModel::refreshNow)

                        ToastKind.RESUMEN -> {
                            windowVisible = true
                            traerAlFrente++
                            screen = Screen.IMAGES
                        }
                    }
                },
            )
        }

        if (windowVisible) {
            Window(
                state = windowState,
                // Sin decoracion del sistema y con barra propia. Ademas de poder darle nuestro
                // estilo, quita el estado "minimizada": era el unico en el que "Ver" no lograba
                // traer la ventana delante, porque Windows no deja que un proceso que no esta en
                // primer plano se ponga ahi por su cuenta.
                undecorated = true,
                onCloseRequest = {
                    windowVisible = false
                    // El resaltado dice "mira esta fila": cerrar la ventana es haber terminado de
                    // mirarla. Sin esto, reabrir dentro de los Dwell.HIGHLIGHT_MILLIS devuelve la
                    // fila resaltada y la lista se desplaza otra vez hasta ella (H-76).
                    viewModel.clearHighlight()
                    // Sin esto, cerrar la ventana estando enfocada deja windowFocused en true para
                    // siempre: la ventana ya no existe para actualizarlo, y los sonidos se
                    // quedarían mudos con la ventana cerrada, justo cuando más falta hacen.
                    wiring.windowFocused.value = false
                },
                title = "ImageWatch — imágenes monitoreadas",
                icon = AppIconPainter,
            ) {
                val focused = LocalWindowInfo.current.isWindowFocused
                LaunchedEffect(focused) { wiring.windowFocused.value = focused }

                // Desminimizar y pedir el foco: sin lo primero la ventana sigue en la barra de
                // tareas, y sin lo segundo queda detras de la aplicacion que el usuario tenia
                // delante.
                LaunchedEffect(traerAlFrente) {
                    if (traerAlFrente > 0) {
                        // La ventana no puede estar minimizada -no hay boton para ello-, asi
                        // que basta con pedirla delante. El rodeo de "siempre encima" que hacia
                        // falta antes se fue con el estado minimizado.
                        window.toFront()
                        window.requestFocus()
                    }
                }

                ImageWatchTheme(config.theme) {
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            // La X de la barra propia cierra igual que onCloseRequest, asi que
                            // limpia lo mismo: el resaltado y la marca de foco.
                            TitleBar(
                                title = "ImageWatch — imágenes monitoreadas",
                                onClose = {
                                    windowVisible = false
                                    viewModel.clearHighlight()
                                    wiring.windowFocused.value = false
                                },
                            )
                            MainScreen(wiring, viewModel, cerrarApp, screen) { screen = it }
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen { IMAGES, SETTINGS }

@Composable
private fun MainScreen(
    wiring: Wiring,
    viewModel: ImagesViewModel,
    onExit: () -> Unit,
    screen: Screen,
    onScreenChange: (Screen) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val config by wiring.config.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Crossfade(screen, label = "screen") { current ->
        when (current) {
            Screen.IMAGES ->
                ImagesScreen(
                    state = state,
                    mutedAll = config.mutedAll,
                    onSearchChange = viewModel::onSearchChange,
                    onAdd = { adding = true },
                    onAcknowledge = viewModel::acknowledge,
                    onAcknowledgeAll = viewModel::acknowledgeAll,
                    onRefresh = viewModel::refreshNow,
                    onDelete = { deleting = it },
                    onOpenSettings = { onScreenChange(Screen.SETTINGS) },
                    onToggleMuteAll = { wiring.applyConfig(config.copy(mutedAll = !config.mutedAll)) },
                    onToggleSilence = viewModel::toggleSilence,
                    onToggleExpand = viewModel::toggleExpand,
                )

            Screen.SETTINGS ->
                SettingsPane(
                    wiring = wiring,
                    config = config,
                    polling = state.polling,
                    verifying = state.verifying,
                    watchedCount = state.total,
                    onTogglePolling = viewModel::togglePolling,
                    onExit = onExit,
                    onBack = { onScreenChange(Screen.IMAGES) },
                )
        }
    }

    if (adding) {
        NameDialog("Agregar imagen", "", { adding = false }) { viewModel.addImage(it) }
    }
    deleting?.let { name ->
        ConfirmDialog(
            title = "Quitar $name de la lista",
            body = "Deja de vigilarse y desaparece. La imagen seguirá en el registry.",
            lost = listOf("El seguimiento de $name", "La versión vista de $name"),
            confirmLabel = "Quitar de la lista",
            onDismiss = { deleting = null },
            onConfirm = { viewModel.removeImage(name) },
        )
    }
}

@Composable
private fun SettingsPane(
    wiring: Wiring,
    config: AppConfig,
    polling: Boolean,
    verifying: Boolean,
    watchedCount: Int,
    onTogglePolling: () -> Unit,
    onExit: () -> Unit,
    onBack: () -> Unit,
) {
    // Sin clave: el unico que cambia la configuracion vigente es este mismo formulario -aplica al
    // momento-. SettingsPane ya se desmonta y se vuelve a montar al cambiar de pantalla con el
    // Crossfade, asi que al reabrir Ajustes el formulario nace con la configuracion vigente.
    val viewModel = remember { SettingsViewModel(config, wiring::applyConfig, wiring.autostart) }
    val state by viewModel.state.collectAsState()

    SettingsScreen(
        state = state,
        polling = polling,
        verifying = verifying,
        watchedCount = watchedCount,
        onTogglePolling = onTogglePolling,
        onUrlChange = viewModel::onUrlChange,
        onUrlCommit = viewModel::onUrlCommit,
        onIntervalChange = viewModel::onIntervalChange,
        onIntervalCommit = viewModel::onIntervalCommit,
        onIgnoreSslChange = viewModel::onIgnoreSslChange,
        onThemeChange = viewModel::onThemeChange,
        onToastsChange = viewModel::onToastsChange,
        onToastSecondsChange = viewModel::onToastSecondsChange,
        onToastSecondsCommit = viewModel::onToastSecondsCommit,
        onSoundsChange = viewModel::onSoundsChange,
        onVolumeChange = viewModel::onVolumeChange,
        onMutedAllChange = viewModel::onMutedAllChange,
        onAutostartChange = viewModel::onAutostartChange,
        onResetSettings = {
            wiring.resetSettings()
            // El restablecido cambió `wiring.config` por fuera; el formulario, montado, aún
            // muestra lo anterior. Se repuebla desde la config vigente para que los controles
            // reflejen los valores de fábrica y el siguiente toggle no los deshaga.
            viewModel.reload(wiring.config.value)
        },
        onWipeLocalData = { wiring.wipeLocalData(onExit) },
        onBack = onBack,
    )
}
