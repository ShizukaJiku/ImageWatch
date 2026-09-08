package io.github.shizukajiku.imagewatch.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.shizukajiku.imagewatch.ui.theme.Space

/**
 * Alta y edicion comparten dialogo.
 *
 * `onConfirm` devuelve el mensaje de error o `null`: el dialogo solo se cierra cuando la
 * validacion pasa, y esa validacion vive en el view model, donde ya tiene tests. Aqui no se
 * duplica ninguna regla.
 */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> String?) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text("Nombre de imagen") },
                    isError = error != null,
                    singleLine = true,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton({
                error = onConfirm(value)
                if (error == null) onDismiss()
            }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun DeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar imagen") },
        text = {
            Text(
                "Se dejará de monitorear $name.\n" +
                    "Esta acción no elimina el historial ya notificado.",
            )
        },
        confirmButton = {
            TextButton({
                onConfirm()
                onDismiss()
            }) {
                Text("Eliminar")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
