package io.github.shizukajiku.imagewatch.infrastructure.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Una imagen a incluir en el mensaje, con las dos versiones que le importan a Teams: [from] es la
 * última de la que Teams ya avisó -no la que el escritorio tiene reconocida-, y [to] la que motiva
 * este aviso. Un `ImageState` no sirve para esto: su `local` es el campo del escritorio, que se
 * queda quieto si el usuario nunca pulsa «Marcar como visto», y usarlo aquí haría que el mensaje
 * siguiera anunciando el mismo «desde» ciclo tras ciclo aunque Teams ya hubiera avisado de versiones
 * intermedias.
 */
data class TeamsVersionUpdate(val name: String, val from: String, val to: String)

/**
 * Envía mensajes al flujo de Power Automate que crea la plantilla «Publicar en un canal cuando se
 * reciba una solicitud web» de Workflows para Teams. Ese disparador espera el mensaje como una
 * Adaptive Card envuelta en el sobre `type: "message"` -no el `MessageCard` de los conectores O365
 * clásicos, que es un formato distinto y ya en retirada-.
 *
 * El encabezado «Flujos de trabajo publicó un mensaje» que Teams pinta alrededor del mensaje no
 * sale de aquí -lo decide el paso «Publicar tarjeta» del flujo, no el payload que mandamos-; lo
 * único que gobierna este cliente es el contenido de la tarjeta misma.
 */
class TeamsWebhookClient(private val client: HttpClient) {

    suspend fun sendUpdates(webhookUrl: String, updates: List<TeamsVersionUpdate>): Result<Unit> =
        send(webhookUrl, updatesCard(updates))

    /** Lo dispara el botón «Probar webhook» de Ajustes, sin esperar a que haya una versión nueva. */
    suspend fun sendTest(webhookUrl: String): Result<Unit> = send(webhookUrl, testCard())

    private suspend fun send(webhookUrl: String, card: JsonObject): Result<Unit> = runCatching {
        require(isValidWebhookUrl(webhookUrl)) { "La URL del webhook debe empezar por https://" }
        val response = client.post(webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(card.toString())
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
    }

    private fun updatesCard(updates: List<TeamsVersionUpdate>): JsonObject {
        val word = if (updates.size == 1) "imagen" else "imágenes"
        val title = "Hay ${updates.size} $word con versión nueva"
        val rows = updates.map { update -> versionRow(update.name, update.from, update.to) }
        return card(header(title), rows)
    }

    private fun testCard(): JsonObject =
        card(header("Mensaje de prueba"), listOf(noteRow("El webhook está bien configurado.")))

    private fun header(title: String): JsonObject = textBlock(title, weight = "Bolder", size = "Medium", wrap = true)

    /**
     * Una fila por imagen: el nombre a la izquierda, el «desde → hasta» a la derecha en
     * monoespaciada para que las versiones se lean alineadas igual que en una tabla. `separator`
     * traza la línea fina entre filas, incluida la primera, que la separa del encabezado.
     */
    private fun versionRow(name: String, from: String, to: String): JsonObject = columnSet(
        column("stretch", textBlock(name, weight = "Bolder", wrap = true, spacing = "None")),
        column(
            "auto",
            textBlock("$from → $to", fontType = "Monospace", color = "Accent", wrap = false, spacing = "None"),
        ),
        separator = true,
    )

    private fun noteRow(text: String): JsonObject = buildJsonObject {
        put("type", "Container")
        put("separator", true)
        put("spacing", "Medium")
        put("items", buildJsonArray { add(textBlock(text, wrap = true, isSubtle = true)) })
    }

    private fun card(header: JsonObject, rows: List<JsonObject>): JsonObject = buildJsonObject {
        put("type", "message")
        put(
            "attachments",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("contentType", "application/vnd.microsoft.card.adaptive")
                        put(
                            "content",
                            buildJsonObject {
                                put("\$schema", "http://adaptivecards.io/schemas/adaptive-card.json")
                                put("type", "AdaptiveCard")
                                put("version", "1.4")
                                put(
                                    "body",
                                    buildJsonArray {
                                        add(header)
                                        rows.forEach { add(it) }
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    private fun textBlock(
        text: String,
        weight: String? = null,
        size: String? = null,
        color: String? = null,
        fontType: String? = null,
        wrap: Boolean? = null,
        spacing: String? = null,
        isSubtle: Boolean? = null,
    ): JsonObject = buildJsonObject {
        put("type", "TextBlock")
        put("text", text)
        weight?.let { put("weight", it) }
        size?.let { put("size", it) }
        color?.let { put("color", it) }
        fontType?.let { put("fontType", it) }
        wrap?.let { put("wrap", it) }
        spacing?.let { put("spacing", it) }
        isSubtle?.let { put("isSubtle", it) }
    }

    private fun column(width: String, vararg items: JsonObject): JsonObject = buildJsonObject {
        put("type", "Column")
        put("width", width)
        put("verticalContentAlignment", "Center")
        put("items", buildJsonArray { items.forEach { add(it) } })
    }

    private fun columnSet(vararg columns: JsonObject, separator: Boolean = false): JsonObject = buildJsonObject {
        put("type", "ColumnSet")
        if (separator) {
            put("separator", true)
            put("spacing", "Medium")
        }
        put("columns", buildJsonArray { columns.forEach { add(it) } })
    }

    private companion object {
        private fun isValidWebhookUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true)
    }
}
