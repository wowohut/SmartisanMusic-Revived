package com.smartisanos.music.ui.shell.cloud

import android.content.Context
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import com.smartisanos.music.R
import com.smartisanos.music.data.online.OnlineAccountPlaylist
import com.smartisanos.music.data.online.OnlineAlbum
import com.smartisanos.music.data.online.OnlineArtist
import com.smartisanos.music.data.online.OnlineMusicHome
import com.smartisanos.music.data.online.OnlinePlaylist
import com.smartisanos.music.data.online.OnlineRadio

internal val CloudAccentColor = ComposeColor(0xFFE65C53)
internal val CloudSecondaryTextColor = ComposeColor(0x80000000)

internal val CloudSearchBarHeight = 50.dp
internal val CloudSearchCategoryBarHeight = 42.dp
internal val CloudBannerHeight = 142.dp
internal val CloudHomeCoverCardWidth = 96.dp
internal val CloudHomeTrackPreviewRowHeight = 48.dp
internal val CloudSearchCoverRowHeight = 72.dp
internal val CloudSearchCoverArtworkSize = 48.dp
internal val CloudDetailHeaderHeight = 88.dp
internal val CloudDetailHeaderArtworkSize = 64.dp
internal val CloudAccountPlaylistActionBarHeight = 48.dp
internal val CloudHomeEntryRowHeight = 78.dp
internal val CloudSectionTitleHeight = 39.dp

internal const val CloudSearchDebounceMs = 350L
internal const val CloudBannerAutoScrollMs = 5_000L
internal const val CloudBannerMaxCount = 6
internal const val CloudBannerArtworkWidthPx = 900
internal const val CloudBannerArtworkHeightPx = 320
internal const val CloudCoverArtworkSizePx = 260
internal const val CloudHomeTrackPreviewCount = 5
internal const val CloudHomeCoverPreviewCount = 6

internal fun OnlineMusicHome.isEmpty(): Boolean {
    return tracks.isEmpty() &&
        playlists.isEmpty() &&
        charts.isEmpty() &&
        albums.isEmpty() &&
        artists.isEmpty()
}

internal fun OnlinePlaylist.homeSubtitle(context: Context): String? {
    return subtitle?.takeIf(String::isNotBlank)
        ?: when {
            trackCount > 0 -> context.getString(R.string.cloud_music_playlist_track_count, trackCount)
            playCount >= 10_000L ->
                context.getString(R.string.cloud_music_play_count_wan, playCount / 10_000L)
            playCount > 0L -> context.getString(R.string.cloud_music_play_count, playCount)
            else -> null
        }
}

internal fun OnlineAlbum.albumSubtitle(context: Context): String? {
    val artistText = artist?.takeIf(String::isNotBlank)
    val countText = trackCount
        .takeIf { count -> count > 0 }
        ?.let { count -> context.getString(R.string.cloud_music_playlist_track_count, count) }
    return listOfNotNull(artistText, countText)
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
        ?: context.getString(R.string.cloud_music_album_provider_netease)
}

internal fun OnlineArtist.subtitleText(context: Context): String {
    val aliasText = subtitle?.takeIf(String::isNotBlank)
    val countText = when {
        trackCount > 0 && albumCount > 0 ->
            context.getString(R.string.cloud_music_artist_track_album_count, trackCount, albumCount)
        trackCount > 0 -> context.getString(R.string.cloud_music_artist_track_count, trackCount)
        albumCount > 0 -> context.getString(R.string.cloud_music_artist_album_count, albumCount)
        else -> null
    }
    return listOfNotNull(aliasText, countText)
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
        ?: context.getString(R.string.cloud_music_artist_provider_netease)
}

internal fun OnlineRadio.cardSubtitle(context: Context): String? {
    return subtitle?.takeIf(String::isNotBlank)
        ?: category?.takeIf(String::isNotBlank)
        ?: creator?.takeIf(String::isNotBlank)
        ?: programCount.takeIf { count -> count > 0 }?.let { count ->
            context.getString(R.string.cloud_music_radio_program_count, count)
        }
}

internal fun OnlineRadio.subtitleText(context: Context): String {
    val categoryText = category?.takeIf(String::isNotBlank)
    val programText = programCount.takeIf { count -> count > 0 }?.let { count ->
        context.getString(R.string.cloud_music_radio_program_count, count)
    }
    val playText = playCount.takeIf { count -> count > 0L }?.let { count ->
        context.getString(R.string.cloud_music_play_count, count)
    }
    return listOfNotNull(categoryText, programText, playText)
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
        ?: subtitle
        ?: context.getString(R.string.cloud_music_radio_provider_netease)
}

internal fun Context.dpPx(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

internal fun OnlineAccountPlaylist.displayTitle(context: Context): String {
    return if (isLikedSongs) {
        context.getString(R.string.cloud_music_liked_songs_entry)
    } else {
        title
    }
}
