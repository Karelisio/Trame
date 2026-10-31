package com.karelisio.trame.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif")
private fun String.isImage() = substringAfterLast('.', "").lowercase() in IMAGE_EXT

/** Tri "naturel" : page2 avant page10. */
val NaturalOrder: Comparator<String> = Comparator { a, b ->
    val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
    val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]; val y = rb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
        } else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ra.size.compareTo(rb.size)
}

/** Source de pages : chaque page est un modèle chargeable par Coil (File ou Uri). */
interface PageSource : Closeable {
    val count: Int
    suspend fun page(index: Int): Any
}

/** CBZ/ZIP : copié une fois en cache puis lu en accès aléatoire, page par page. */
class ZipPageSource private constructor(private val zip: ZipFile, private val outDir: File) : PageSource {
    private val entries = zip.entries().asSequence()
        .filter { !it.isDirectory && it.name.isImage() && !it.name.startsWith("__MACOSX") && !it.name.substringAfterLast('/').startsWith(".") }
        .sortedWith(compareBy(NaturalOrder) { it.name })
        .toList()

    override val count = entries.size

    override suspend fun page(index: Int): Any = withContext(Dispatchers.IO) {
        val entry = entries[index]
        val out = File(outDir, "$index.${entry.name.substringAfterLast('.')}")
        if (!out.exists()) {
            val tmp = File(outDir, "${UUID.randomUUID()}.tmp")
            zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(out)) tmp.delete()
        }
        out
    }

    override fun close() = zip.close()

    companion object {
        suspend fun open(context: Context, uri: Uri): ZipPageSource = withContext(Dispatchers.IO) {
            val root = File(context.cacheDir, "local")
            val dir = File(root, uri.toString().sha1()).apply { mkdirs() }
            dir.setLastModified(System.currentTimeMillis())
            val book = File(dir, "book.zip")
            val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
            } ?: -1L
            if (!book.exists() || (size > 0 && book.length() != size)) {
                dir.listFiles()?.forEach { it.delete() }
                val tmp = File(dir, "book.tmp")
                context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(book)
            }
            // On garde en cache les 4 derniers livres ouverts.
            root.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.deleteRecursively() }
            val pages = File(dir, "pages").apply { mkdirs() }
            ZipPageSource(ZipFile(book), pages)
        }
    }
}

/** Dossier d'images (arborescence SAF), sous-dossiers inclus (un niveau). */
class FolderPageSource private constructor(private val uris: List<Uri>) : PageSource {
    override val count = uris.size
    override suspend fun page(index: Int): Any = uris[index]
    override fun close() {}

    companion object {
        suspend fun open(context: Context, uri: Uri): FolderPageSource = withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, uri) ?: error("Dossier illisible")
            val files = mutableListOf<Pair<String, Uri>>()
            fun walk(dir: DocumentFile, prefix: String, depth: Int) {
                dir.listFiles().forEach { f ->
                    val name = f.name ?: return@forEach
                    when {
                        f.isDirectory && depth < 2 -> walk(f, "$prefix$name/", depth + 1)
                        f.isFile && name.isImage() -> files += "$prefix$name" to f.uri
                    }
                }
            }
            walk(root, "", 0)
            FolderPageSource(files.sortedWith(compareBy(NaturalOrder) { it.first }).map { it.second })
        }
    }
}

private fun String.sha1(): String =
    MessageDigest.getInstance("SHA-1").digest(toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
