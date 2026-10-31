package com.karelisio.trame.ui.local

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.db.LocalBookDao
import com.karelisio.trame.data.db.LocalBookEntity
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.components.cascadeIn
import kotlinx.coroutines.launch

/** Fichiers locaux : CBZ/ZIP et dossiers d'images ouverts via SAF. */
@Composable
fun LocalLibraryScreen(dao: LocalBookDao, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    var menu by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun register(uri: Uri, isFolder: Boolean) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = (if (isFolder) DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri))
            ?.name?.substringBeforeLast('.') ?: "Sans titre"
        scope.launch {
            val existing = dao.get(uri.toString())
            dao.upsert(
                existing?.copy(lastOpenedAt = System.currentTimeMillis())
                    ?: LocalBookEntity(uri.toString(), name, isFolder, lastOpenedAt = System.currentTimeMillis()),
            )
            onOpen(uri.toString())
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { register(it, false) }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { register(it, true) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Fichiers locaux") },
                subtitle = { Text("CBZ, ZIP et dossiers d'images") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = menu,
                button = {
                    ToggleFloatingActionButton(
                        checked = menu,
                        onCheckedChange = { menu = it },
                        modifier = Modifier.animateFloatingActionButton(visible = true, alignment = Alignment.BottomEnd),
                    ) {
                        Icon(if (menu) Icons.Rounded.Close else Icons.Rounded.Add, "Ouvrir")
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        menu = false
                        pickFile.launch(arrayOf("application/zip", "application/vnd.comicbook+zip", "application/x-cbz", "application/octet-stream"))
                    },
                    icon = { Icon(Icons.Rounded.FolderZip, null) },
                    text = { Text("Fichier CBZ / ZIP") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { menu = false; pickFolder.launch(null) },
                    icon = { Icon(Icons.Rounded.Folder, null) },
                    text = { Text("Dossier d'images") },
                )
            }
        },
    ) { padding ->
        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "Ouvre un fichier CBZ/ZIP ou un dossier d'images avec le bouton +.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(books, key = { _, b -> b.uri }) { i, b ->
                LocalBookRow(
                    b,
                    onClick = { onOpen(b.uri) },
                    onRemove = { scope.launch { dao.delete(b.uri) } },
                    modifier = Modifier.animateItem().cascadeIn(i, b.uri),
                )
            }
        }
    }
}

@Composable
private fun LocalBookRow(b: LocalBookEntity, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(b.coverUri, b.name, Modifier.width(56.dp).height(80.dp).clip(MaterialTheme.shapes.medium))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (b.isFolder) "Dossier" else "Archive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (b.pageCount > 0) {
                    Text("Page ${b.lastPage + 1} / ${b.pageCount}", style = MaterialTheme.typography.bodySmall)
                    LinearWavyProgressIndicator(
                        progress = { (b.lastPage + 1f) / b.pageCount },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Retirer de la liste") }
        }
    }
}
