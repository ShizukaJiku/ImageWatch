package io.github.shizukajiku.imagewatch.infrastructure.persistence

/**
 * Exclusión mutua para los almacenes de fichero.
 *
 * Sustituye al `@Synchronized` que llevaban cuando vivían en el lado JVM. No puede ser un
 * `kotlinx.coroutines.sync.Mutex`: eso obligaría a que `load`, `save` y `findAll` fueran `suspend`,
 * y los llaman el hilo del planificador y el de Compose, ninguno desde una corrutina. Cambiar la
 * firma de los puertos por un detalle de la implementación es justo lo que la arquitectura evita.
 *
 * Lo que protege es un ciclo leer-modificar-escribir sobre un fichero: sin él, dos guardados
 * simultáneos —uno del planificador, otro de la interfaz— pueden perder uno de los dos.
 */
internal expect class Lock() {
    fun <T> withLock(block: () -> T): T
}
