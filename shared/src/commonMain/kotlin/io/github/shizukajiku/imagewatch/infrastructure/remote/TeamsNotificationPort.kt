package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.github.shizukajiku.imagewatch.Log
import io.github.shizukajiku.imagewatch.application.PollListener
import io.github.shizukajiku.imagewatch.application.SilencedImageStore
import io.github.shizukajiku.imagewatch.application.TeamsNotifiedStore
import io.github.shizukajiku.imagewatch.config.AppConfig
import io.github.shizukajiku.imagewatch.domain.PollSnapshot
import io.github.shizukajiku.imagewatch.domain.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adaptador hacia un webhook de Teams. A propósito **no** es un [io.github.shizukajiku.imagewatch.application.NotificationPort]
 * como `ToastNotificationPort`, sino un [PollListener]: el toast del escritorio "espera
 * confirmación" -la fila se queda con su insignia de pendiente hasta que el usuario la reconoce, y
 * esa misma acción es la que decide cuándo deja de avisar-, pero Teams nunca tiene esa
 * confirmación. Este puerto lleva su **propia** línea base en [seenStore], un fichero aparte de
 * `images.json` -el que sí mueve `acknowledge()`-: compara la versión remota de cada ciclo contra
 * lo último que Teams avisó, nunca contra lo que el escritorio tiene reconocido. Dar por visto una
 * imagen en la app no tiene ningún efecto aquí, ni lo tiene al revés.
 *
 * La primera vez que ve una imagen -[seenStore] no tiene entrada para ella todavía, sea porque es
 * la primera vez que corre la app o porque la imagen se acaba de agregar- establece la línea base
 * en silencio, igual que hace el escritorio en su primer ciclo: si no lo hiciera, activar el
 * interruptor con varias imágenes ya pendientes desde antes mandaría una ráfaga de una sola vez.
 *
 * El envío corre en [scope] y no bloquea al llamador: `onSnapshot` lo invoca
 * `VersionPollingService` desde el ciclo de sondeo, y una petición HTTP lenta o caída no puede
 * retrasar ese ciclo. El fallo se registra dentro del propio `launch`, porque llega después de que
 * `onSnapshot` ya ha vuelto.
 *
 * La configuración llega como función y no como valor por el mismo motivo que en
 * `ToastNotificationPort`: puede cambiar mientras la aplicación corre.
 */
class TeamsNotificationPort(
    private val client: TeamsWebhookClient,
    private val seenStore: TeamsNotifiedStore,
    private val silencedImages: SilencedImageStore,
    private val scope: CoroutineScope,
    private val config: () -> AppConfig,
) : PollListener {
    override fun onSnapshot(snapshot: PollSnapshot) {
        val current = config()
        if (!current.teamsEnabled || current.teamsWebhookUrl.isBlank() || current.mutedAll) {
            return
        }
        val silenciadas = silencedImages.findAll()
        val baseline = mutableMapOf<String, String>()
        val toNotify = mutableListOf<TeamsVersionUpdate>()
        val notified = mutableMapOf<String, String>()

        for (image in snapshot.images) {
            if (image.name in silenciadas) continue
            val remote = image.remote ?: continue
            val lastNotified = seenStore.find(image.name)?.let(::parseOrNull)
            when {
                // Primera vez que Teams ve esta imagen: línea base en silencio, no se avisa.
                lastNotified == null -> baseline[image.name] = remote.value

                remote > lastNotified -> {
                    // "de" es lo último que Teams avisó, no `image.local` -el campo del escritorio,
                    // que no se mueve si el usuario nunca da «Marcar como visto»-. Sin esto, dos
                    // avisos seguidos sin reconocer en el escritorio repetirían el mismo "desde"
                    // aunque Teams ya hubiera avisado de una versión intermedia.
                    toNotify.add(TeamsVersionUpdate(image.name, lastNotified.value, remote.value))
                    notified[image.name] = remote.value
                }

                // Ya se avisó de esta versión, o de una más nueva: nada que hacer.
                else -> Unit
            }
        }

        if (baseline.isNotEmpty()) {
            seenStore.save(baseline)
        }
        if (toNotify.isEmpty()) {
            return
        }
        val webhookUrl = current.teamsWebhookUrl
        scope.launch {
            // Se marca como avisada solo si el envío confirma un 200 -nunca antes-: si se marcara
            // al decidir, un fallo de red, un webhook caído o la app cerrándose a mitad del envío
            // dejarían la línea base avanzada sin que el mensaje hubiera salido nunca, y el
            // siguiente aviso real mostraría un «desde» que no se corresponde con lo último que de
            // verdad llegó a Teams -el hueco que se reportó entre dos avisos consecutivos-.
            client.sendUpdates(webhookUrl, toNotify)
                .onSuccess { seenStore.save(notified) }
                .onFailure { log.warn("No se pudo enviar el aviso a Teams", it) }
        }
    }

    private fun parseOrNull(value: String) = runCatching { Version(value) }.getOrNull()

    private companion object {
        private val log = Log("io.github.shizukajiku.imagewatch.infrastructure.remote.TeamsNotificationPort")
    }
}
