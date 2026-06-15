package com.smartisanos.music.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisanos.music.ui.album.AlbumSummary
import com.smartisanos.music.ui.artist.ArtistSummary
import com.smartisanos.music.ui.shell.titlebar.LegacyPortTitleBarTransition

internal data class LegacySelectedArtistState(
    val artist: ArtistSummary,
    val target: LegacyArtistTarget,
    val albums: List<AlbumSummary>,
)

@Composable
internal fun LegacyPortArtistTitleStack(
    selectedTarget: LegacyArtistTarget?,
    rootPredictiveBackProgress: Float? = null,
    rootPredictiveBackExitConsumed: Boolean = false,
    onRootPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    nestedPredictiveBackProgress: Float? = null,
    nestedPredictiveBackExitConsumed: Boolean = false,
    onNestedPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (LegacyArtistTarget?, Modifier) -> Unit,
) {
    val titleEntry = selectedTarget?.toTitleStackEntry()
    LegacyPortTitleBarTransition(
        secondaryKey = titleEntry,
        modifier = modifier,
        label = "legacy artist title transition",
        predictiveBackProgress = rootPredictiveBackProgress,
        predictiveBackExitConsumed = rootPredictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onRootPredictiveBackExitConsumedReset,
        primaryContent = {
            content(null, Modifier.fillMaxSize())
        },
        secondaryContent = { entry ->
            when (entry) {
                is LegacyArtistTitleStackEntry.Direct -> {
                    content(entry.target, Modifier.fillMaxSize())
                }
                is LegacyArtistTitleStackEntry.ArtistRoot -> {
                    val nestedTarget = selectedTarget?.takeIf { target ->
                        target.artistId == entry.artistId && target !is LegacyArtistTarget.Albums
                    }
                    LegacyPortTitleBarTransition(
                        secondaryKey = nestedTarget,
                        modifier = Modifier.fillMaxSize(),
                        label = "legacy artist nested title transition",
                        predictiveBackProgress = nestedPredictiveBackProgress,
                        predictiveBackExitConsumed = nestedPredictiveBackExitConsumed,
                        onPredictiveBackExitConsumedReset = onNestedPredictiveBackExitConsumedReset,
                        primaryContent = {
                            content(
                                LegacyArtistTarget.Albums(
                                    artistId = entry.artistId,
                                    artistName = entry.artistName,
                                ),
                                Modifier.fillMaxSize(),
                            )
                        },
                        secondaryContent = { target ->
                            content(target, Modifier.fillMaxSize())
                        },
                    )
                }
            }
        },
    )
}

private sealed interface LegacyArtistTitleStackEntry {
    data class ArtistRoot(
        val artistId: String,
        val artistName: String,
    ) : LegacyArtistTitleStackEntry

    data class Direct(
        val target: LegacyArtistTarget.Album,
    ) : LegacyArtistTitleStackEntry
}

private fun LegacyArtistTarget.toTitleStackEntry(): LegacyArtistTitleStackEntry {
    return when (this) {
        is LegacyArtistTarget.Album -> if (fromArtistAlbums) {
            LegacyArtistTitleStackEntry.ArtistRoot(
                artistId = artistId,
                artistName = artistName,
            )
        } else {
            LegacyArtistTitleStackEntry.Direct(this)
        }
        else -> LegacyArtistTitleStackEntry.ArtistRoot(
            artistId = artistId,
            artistName = artistName,
        )
    }
}
