package io.github.shizukajiku.imagewatch.tools

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.shizukajiku.imagewatch.ui.AppIconPainter
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Image as SkiaImage

/**
 * Genera `desktopApp/icons/ImageWatch.ico` a partir de [AppIconPainter], la misma marca que pintan
 * la ventana y la bandeja. No entra en el build: se ejecuta a mano con `./gradlew
 * :desktopApp:generateIcon` cuando cambia la marca, y el `.ico` resultante se commitea.
 *
 * El `.ico` lleva PNG embebido (soportado desde Windows Vista) a siete tamaños. El contenedor ICO
 * es little-endian: 6 bytes de cabecera, 16 por entrada de directorio, y luego los PNG en crudo.
 */
private val SIZES = intArrayOf(16, 24, 32, 48, 64, 128, 256)

private fun renderPng(px: Int): ByteArray {
    val bitmap = ImageBitmap(px, px)
    val size = Size(px.toFloat(), px.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
        with(AppIconPainter) { draw(size) }
    }
    val data = SkiaImage.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
        ?: error("Fallo al codificar PNG de $px px")
    return data.bytes
}

private fun buildIco(pngs: List<ByteArray>): ByteArray {
    val dirSize = 6 + 16 * pngs.size
    val dir = ByteBuffer.allocate(dirSize).order(ByteOrder.LITTLE_ENDIAN)
    dir.putShort(0) // reservado
    dir.putShort(1) // tipo: 1 = icono
    dir.putShort(pngs.size.toShort())
    var offset = dirSize
    pngs.forEachIndexed { i, png ->
        val dim = (SIZES[i] and 0xFF).toByte() // 256 -> 0, como exige el formato
        dir.put(dim) // ancho
        dir.put(dim) // alto
        dir.put(0) // colores de paleta
        dir.put(0) // reservado
        dir.putShort(1) // planos de color
        dir.putShort(32) // bits por pixel
        dir.putInt(png.size)
        dir.putInt(offset)
        offset += png.size
    }
    val out = ByteArrayOutputStream()
    out.write(dir.array())
    pngs.forEach(out::write)
    return out.toByteArray()
}

fun main() {
    val pngs = SIZES.map(::renderPng)
    val target = File("icons/ImageWatch.ico")
    target.parentFile.mkdirs()
    target.writeBytes(buildIco(pngs))
    println("Escrito ${target.absolutePath} (${target.length()} bytes; ${SIZES.joinToString()} px)")
}
