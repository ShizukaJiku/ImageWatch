package io.github.shizukajiku.imagewatch.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Gobierna el calendario del sondeo: cuándo se consulta, cada cuánto, y cómo se cuela una
 * consulta inmediata sin alterar ese calendario.
 *
 * Los métodos de ciclo de vida —[start], [stop], [updateInterval] y [close]— se llaman desde el
 * hilo de la interfaz. El original los sincronizaba; aquí el estado compartido se reduce a dos
 * campos que solo toca ese hilo, y el trabajo real lo serializa la cola. Si algún día se invocan
 * desde varios hilos, hará falta un mutex propio.
 */
class PollingController(private val service: VersionPollingService, interval: Duration) : AutoCloseable {
    enum class Status {
        STOPPED,
        RUNNING,
    }

    // La validación va antes que ninguna otra inicialización, y por eso este bloque se declara
    // primero: los inicializadores corren en orden de declaración. Un intervalo inválido no debe
    // llegar a reservar el scope ni la cola.
    init {
        validateInterval(interval)
    }

    private val currentInterval = MutableStateFlow(interval)
    private val currentStatus = MutableStateFlow(Status.STOPPED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Cola de un solo consumidor. Sustituye al executor de un solo hilo del original y le da la
     * misma garantía: un ciclo programado y un refresco manual nunca corren a la vez.
     */
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private var loop: Job? = null
    private var closed = false

    init {
        scope.launch {
            for (command in work) {
                command()
            }
        }
    }

    fun start() {
        if (closed || currentStatus.value == Status.RUNNING) {
            return
        }
        currentStatus.value = Status.RUNNING
        // Consulta inmediata y luego una espera completa tras cada ciclo, que es lo que hacía
        // scheduleWithFixedDelay con retardo inicial cero. Encolar en vez de ejecutar aquí es lo
        // que impide que un ciclo largo se solape con el siguiente.
        loop =
            scope.launch {
                while (isActive) {
                    work.send { service.poll() }
                    delay(currentInterval.value)
                }
            }
    }

    /**
     * Lanza una consulta inmediata sin tocar el calendario del sondeo. Se ejecuta en la misma cola
     * que los ciclos programados, y no en el hilo de quien pulsa: consultar la red desde el hilo
     * de la interfaz la congelaría durante toda la consulta.
     *
     * Funciona con el sondeo parado, que es justo cuando más falta hace.
     *
     * @param name la imagen a consultar, o `null` para consultarlas todas.
     */
    fun refreshNow(name: String?) {
        if (closed) {
            return
        }
        work.trySend { if (name == null) service.poll() else service.pollOne(name) }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        currentStatus.value = Status.STOPPED
    }

    fun updateInterval(newInterval: Duration) {
        validateInterval(newInterval)
        currentInterval.value = newInterval
        if (currentStatus.value == Status.RUNNING) {
            stop()
            start()
        }
    }

    fun interval(): Duration = currentInterval.value

    fun status(): Status = currentStatus.value

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        stop()
        work.close()
        scope.cancel()
    }

    private fun validateInterval(value: Duration) {
        require(value >= 1.seconds) { "El intervalo debe ser de al menos 1 segundo" }
    }
}
