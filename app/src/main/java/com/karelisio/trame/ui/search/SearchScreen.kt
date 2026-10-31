package com.karelisio.trame.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.trame.data.db.SeriesSource
import com.karelisio.trame.data.repo.SeriesSummary
import com.karelisio.trame.ui.components.Cover
import com.karelisio.trame.ui.components.MorphShape
import com.karelisio.trame.ui.components.cascadeIn
import com.karelisio.trame.ui.components.sharedBounds
import com.karelisio.trame.ui.components.sharedElement
import com.karelisio.trame.ui.components.statusLabel
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .sharedBounds("search-container"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                TextField(
                    value = state.query,
                    onValueChange = vm::onQuery,
                    placeholder = { Text("One Piece, Dandadan, Frieren…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Rounded.Close, "Effacer") }
                }
            }
        }

        AnimatedContent(
            targetState = when {
                state.loading -> 0
                state.error != null -> 1
                else -> 2
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "search",
            modifier = Modifier.fillMaxSize(),
        ) { mode ->
            when (mode) {
                0 -> Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                1 -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.results.isNotEmpty()) item {
                        Text(
                            if (state.source == SeriesSource.MANGADEX) "Résultats MangaDex"
                            else "MangaDex indisponible · résultats MangaUpdates (secours)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    itemsIndexed(state.results, key = { _, s -> s.id }) { i, s ->
                        ResultRow(
                            s,
                            favorite = s.id in favorites,
                            onToggle = { vm.toggleFavorite(s) },
                            onClick = { scope.launch { onOpen(vm.prepareOpen(s)) } },
                            modifier = Modifier.animateItem().cascadeIn(i, s.id),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    s: SeriesSummary,
    favorite: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(
                s.coverUrl, s.title,
                Modifier.sharedElement("cover-${s.id}").width(64.dp).height(92.dp).clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(statusLabel(s.status), s.year?.toString()).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                s.description?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FavoriteMorphButton(favorite, onToggle)
        }
    }
}

/** Bouton favori : cercle → cookie (morphing M3 Expressive), ressort + haptique. */
@Composable
fun FavoriteMorphButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val progress by animateFloatAsState(
        if (favorite) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "morph",
    )
    val shape = remember(progress) { MorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, progress) }
    val bg = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (favorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier
            .size(48.dp)
            .clip(shape)
            .background(bg)
            .clickable {
                haptics.performHapticFeedback(if (favorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (favorite) Icons.Rounded.Check else Icons.Rounded.Add,
            if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = fg,
        )
    }
}
