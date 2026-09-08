package io.github.shizukajiku.imagewatch.application

import io.github.shizukajiku.imagewatch.domain.PollSnapshot

/**
 * Recibe el resultado de cada ciclo. Es el puerto que permite a la interfaz actualizarse sola, sin
 * sondear al servicio.
 *
 * Las implementaciones se invocan en el hilo del planificador: si tocan la interfaz, deben saltar
 * ellas mismas al hilo de despacho de eventos.
 */
interface PollListener {
    fun onSnapshot(snapshot: PollSnapshot)

    /**
     * Aviso de que un ciclo acaba de empezar. Vacío por defecto: la mayoría de oyentes solo quiere
     * el resultado, y obligarles a implementar esto convertiría cada uno en una clase anónima.
     *
     * Es lo que permite a la interfaz distinguir «todavía no se ha comprobado» de «se está
     * comprobando ahora mismo», que para el usuario son dos cosas distintas.
     */
    fun onPollStarted() {}
}
