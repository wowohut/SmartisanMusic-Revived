package com.smartisanos.music.ui.shell.cloud

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import com.smartisanos.music.data.online.OnlineAlbum
import com.smartisanos.music.data.online.OnlineArtist
import com.smartisanos.music.data.online.OnlineBanner
import com.smartisanos.music.data.online.OnlineMusicProvider
import com.smartisanos.music.data.online.OnlinePlaylist
import com.smartisanos.music.data.online.OnlinePlaylistKind
import com.smartisanos.music.data.online.OnlineRadio

/**
 * 云音乐模块内部页面路由。
 *
 * 用于替代原先单文件内的 [CloudHomeMode] 状态机，使首页、我的、电台、歌手、歌单广场、
 * 搜索以及各类详情页都能通过页面栈（[CloudMusicTransitionHost]）进行真实的推进/返回转场。
 */
internal sealed class CloudMusicRoute {

    /** 推荐首页 */
    data object Home : CloudMusicRoute()

    /** 我的（账号歌单/专辑/电台） */
    data object Mine : CloudMusicRoute()

    /** 电台首页 */
    data object Radio : CloudMusicRoute()

    /** 电台节目列表页 */
    data object RadioList : CloudMusicRoute()

    /** 电台热播节目页 */
    data object RadioTracks : CloudMusicRoute()

    /** 歌手首页 */
    data object Artists : CloudMusicRoute()

    /** 歌单广场（精选集） */
    data object Collections : CloudMusicRoute()

    /** 搜索页 */
    data object Search : CloudMusicRoute()

    /** 每日推荐歌曲完整列表 */
    data object FeaturedTracks : CloudMusicRoute()

    /** 热门推荐精选集完整列表 */
    data object FeaturedPlaylists : CloudMusicRoute()

    /** 热歌人气榜完整列表 */
    data object FeaturedCharts : CloudMusicRoute()

    /** 新碟上架完整列表 */
    data object FeaturedAlbums : CloudMusicRoute()

    /** 热门歌手完整列表 */
    data object FeaturedArtists : CloudMusicRoute()

    /** Banner 点击后进入的单曲详情 */
    data class BannerTrack(val banner: OnlineBanner) : CloudMusicRoute()

    /** 在线歌单详情 */
    data class OnlinePlaylistTracks(
        val playlist: OnlinePlaylist,
        val returnTo: CloudMusicRoute = Home,
    ) : CloudMusicRoute()

    /** 在线专辑详情 */
    data class OnlineAlbumTracks(
        val album: OnlineAlbum,
        val returnTo: CloudMusicRoute = Home,
    ) : CloudMusicRoute()

    /** 歌手热门歌曲详情 */
    data class ArtistTracks(
        val artist: OnlineArtist,
        val returnTo: CloudMusicRoute = Artists,
    ) : CloudMusicRoute()

    /** 歌手专辑列表 */
    data class ArtistAlbums(
        val artist: OnlineArtist,
        val returnTo: CloudMusicRoute = Artists,
    ) : CloudMusicRoute()

    /** 电台节目列表 */
    data class RadioPrograms(
        val radio: OnlineRadio,
        val returnTo: CloudMusicRoute = Radio,
    ) : CloudMusicRoute()

    companion object {
        /** 参与页面栈转场的详情页类型 */
        fun isDetail(route: CloudMusicRoute): Boolean = when (route) {
            is BannerTrack,
            is OnlinePlaylistTracks,
            is OnlineAlbumTracks,
            is ArtistTracks,
            is ArtistAlbums,
            is RadioPrograms,
            -> true
            else -> false
        }

        /** 用于 [rememberSaveable] 保存当前路由。
         *
         * 详情路由中包含的数据对象全部拆成原始字段存储，因此进程重建后也能恢复。
         */
        val Saver: Saver<CloudMusicRoute, Any> = mapSaver(
            save = { it.toMap() },
            restore = { it.toRoute() },
        )
    }
}

internal fun CloudMusicRoute.primaryRoute(): CloudMusicRoute = when (this) {
    is CloudMusicRoute.OnlinePlaylistTracks -> returnTo.primaryRoute()
    is CloudMusicRoute.OnlineAlbumTracks -> returnTo.primaryRoute()
    is CloudMusicRoute.ArtistTracks -> returnTo.primaryRoute()
    is CloudMusicRoute.ArtistAlbums -> returnTo.primaryRoute()
    is CloudMusicRoute.RadioPrograms -> returnTo.primaryRoute()
    is CloudMusicRoute.BannerTrack -> CloudMusicRoute.Home
    CloudMusicRoute.Search -> CloudMusicRoute.Home
    else -> this
}

private fun CloudMusicRoute.toMap(): Map<String, Any?> = when (this) {
    is CloudMusicRoute.Home -> mapOf("type" to "Home")
    is CloudMusicRoute.Mine -> mapOf("type" to "Mine")
    is CloudMusicRoute.Radio -> mapOf("type" to "Radio")
    is CloudMusicRoute.RadioList -> mapOf("type" to "RadioList")
    is CloudMusicRoute.RadioTracks -> mapOf("type" to "RadioTracks")
    is CloudMusicRoute.Artists -> mapOf("type" to "Artists")
    is CloudMusicRoute.Collections -> mapOf("type" to "Collections")
    is CloudMusicRoute.Search -> mapOf("type" to "Search")
    is CloudMusicRoute.FeaturedTracks -> mapOf("type" to "FeaturedTracks")
    is CloudMusicRoute.FeaturedPlaylists -> mapOf("type" to "FeaturedPlaylists")
    is CloudMusicRoute.FeaturedCharts -> mapOf("type" to "FeaturedCharts")
    is CloudMusicRoute.FeaturedAlbums -> mapOf("type" to "FeaturedAlbums")
    is CloudMusicRoute.FeaturedArtists -> mapOf("type" to "FeaturedArtists")
    is CloudMusicRoute.BannerTrack -> mapOf(
        "type" to "BannerTrack",
        "banner" to banner.toMap(),
    )
    is CloudMusicRoute.OnlinePlaylistTracks -> mapOf(
        "type" to "OnlinePlaylistTracks",
        "playlist" to playlist.toMap(),
        "returnTo" to returnTo.toMap(),
    )
    is CloudMusicRoute.OnlineAlbumTracks -> mapOf(
        "type" to "OnlineAlbumTracks",
        "album" to album.toMap(),
        "returnTo" to returnTo.toMap(),
    )
    is CloudMusicRoute.ArtistTracks -> mapOf(
        "type" to "ArtistTracks",
        "artist" to artist.toMap(),
        "returnTo" to returnTo.toMap(),
    )
    is CloudMusicRoute.ArtistAlbums -> mapOf(
        "type" to "ArtistAlbums",
        "artist" to artist.toMap(),
        "returnTo" to returnTo.toMap(),
    )
    is CloudMusicRoute.RadioPrograms -> mapOf(
        "type" to "RadioPrograms",
        "radio" to radio.toMap(),
        "returnTo" to returnTo.toMap(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toRoute(): CloudMusicRoute = when (this["type"] as? String) {
    "Home" -> CloudMusicRoute.Home
    "Mine" -> CloudMusicRoute.Mine
    "Radio" -> CloudMusicRoute.Radio
    "RadioList" -> CloudMusicRoute.RadioList
    "RadioTracks" -> CloudMusicRoute.RadioTracks
    "Artists" -> CloudMusicRoute.Artists
    "Collections" -> CloudMusicRoute.Collections
    "Search" -> CloudMusicRoute.Search
    "FeaturedTracks" -> CloudMusicRoute.FeaturedTracks
    "FeaturedPlaylists" -> CloudMusicRoute.FeaturedPlaylists
    "FeaturedCharts" -> CloudMusicRoute.FeaturedCharts
    "FeaturedAlbums" -> CloudMusicRoute.FeaturedAlbums
    "FeaturedArtists" -> CloudMusicRoute.FeaturedArtists
    "BannerTrack" -> CloudMusicRoute.BannerTrack(
        banner = (this["banner"] as Map<String, Any?>).toBanner(),
    )
    "OnlinePlaylistTracks" -> CloudMusicRoute.OnlinePlaylistTracks(
        playlist = (this["playlist"] as Map<String, Any?>).toOnlinePlaylist(),
        returnTo = (this["returnTo"] as Map<String, Any?>).toRoute(),
    )
    "OnlineAlbumTracks" -> CloudMusicRoute.OnlineAlbumTracks(
        album = (this["album"] as Map<String, Any?>).toOnlineAlbum(),
        returnTo = (this["returnTo"] as Map<String, Any?>).toRoute(),
    )
    "ArtistTracks" -> CloudMusicRoute.ArtistTracks(
        artist = (this["artist"] as Map<String, Any?>).toOnlineArtist(),
        returnTo = (this["returnTo"] as Map<String, Any?>).toRoute(),
    )
    "ArtistAlbums" -> CloudMusicRoute.ArtistAlbums(
        artist = (this["artist"] as Map<String, Any?>).toOnlineArtist(),
        returnTo = (this["returnTo"] as Map<String, Any?>).toRoute(),
    )
    "RadioPrograms" -> CloudMusicRoute.RadioPrograms(
        radio = (this["radio"] as Map<String, Any?>).toOnlineRadio(),
        returnTo = (this["returnTo"] as Map<String, Any?>).toRoute(),
    )
    else -> CloudMusicRoute.Home
}

private fun OnlineMusicProvider.toMap(): Map<String, Any?> = mapOf("name" to name)

private fun Map<String, Any?>.toOnlineMusicProvider(): OnlineMusicProvider =
    OnlineMusicProvider.valueOf(this["name"] as String)

private fun OnlinePlaylist.toMap(): Map<String, Any?> = mapOf(
    "provider" to provider.toMap(),
    "playlistId" to playlistId,
    "title" to title,
    "subtitle" to subtitle,
    "artworkUrl" to artworkUrl,
    "trackCount" to trackCount,
    "playCount" to playCount,
    "kind" to kind.name,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toOnlinePlaylist(): OnlinePlaylist = OnlinePlaylist(
    provider = (this["provider"] as Map<String, Any?>).toOnlineMusicProvider(),
    playlistId = this["playlistId"] as String,
    title = this["title"] as String,
    subtitle = this["subtitle"] as? String,
    artworkUrl = this["artworkUrl"] as? String,
    trackCount = this["trackCount"] as? Int ?: 0,
    playCount = this["playCount"] as? Long ?: 0L,
    kind = (this["kind"] as? String)?.let { OnlinePlaylistKind.valueOf(it) }
        ?: OnlinePlaylistKind.Featured,
)

private fun OnlineAlbum.toMap(): Map<String, Any?> = mapOf(
    "provider" to provider.toMap(),
    "albumId" to albumId,
    "title" to title,
    "artist" to artist,
    "artworkUrl" to artworkUrl,
    "trackCount" to trackCount,
    "publishTimeMs" to publishTimeMs,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toOnlineAlbum(): OnlineAlbum = OnlineAlbum(
    provider = (this["provider"] as Map<String, Any?>).toOnlineMusicProvider(),
    albumId = this["albumId"] as String,
    title = this["title"] as String,
    artist = this["artist"] as? String,
    artworkUrl = this["artworkUrl"] as? String,
    trackCount = this["trackCount"] as? Int ?: 0,
    publishTimeMs = this["publishTimeMs"] as? Long ?: 0L,
)

private fun OnlineArtist.toMap(): Map<String, Any?> = mapOf(
    "provider" to provider.toMap(),
    "artistId" to artistId,
    "name" to name,
    "subtitle" to subtitle,
    "artworkUrl" to artworkUrl,
    "trackCount" to trackCount,
    "albumCount" to albumCount,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toOnlineArtist(): OnlineArtist = OnlineArtist(
    provider = (this["provider"] as Map<String, Any?>).toOnlineMusicProvider(),
    artistId = this["artistId"] as String,
    name = this["name"] as String,
    subtitle = this["subtitle"] as? String,
    artworkUrl = this["artworkUrl"] as? String,
    trackCount = this["trackCount"] as? Int ?: 0,
    albumCount = this["albumCount"] as? Int ?: 0,
)

private fun OnlineRadio.toMap(): Map<String, Any?> = mapOf(
    "provider" to provider.toMap(),
    "radioId" to radioId,
    "title" to title,
    "subtitle" to subtitle,
    "category" to category,
    "creator" to creator,
    "artworkUrl" to artworkUrl,
    "programCount" to programCount,
    "playCount" to playCount,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toOnlineRadio(): OnlineRadio = OnlineRadio(
    provider = (this["provider"] as Map<String, Any?>).toOnlineMusicProvider(),
    radioId = this["radioId"] as String,
    title = this["title"] as String,
    subtitle = this["subtitle"] as? String,
    category = this["category"] as? String,
    creator = this["creator"] as? String,
    artworkUrl = this["artworkUrl"] as? String,
    programCount = this["programCount"] as? Int ?: 0,
    playCount = this["playCount"] as? Long ?: 0L,
)

private fun OnlineBanner.toMap(): Map<String, Any?> = mapOf(
    "provider" to provider.toMap(),
    "bannerId" to bannerId,
    "title" to title,
    "subtitle" to subtitle,
    "imageUrl" to imageUrl,
    "targetTrackId" to targetTrackId,
    "targetAlbumId" to targetAlbumId,
    "targetPlaylistId" to targetPlaylistId,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toBanner(): OnlineBanner = OnlineBanner(
    provider = (this["provider"] as Map<String, Any?>).toOnlineMusicProvider(),
    bannerId = this["bannerId"] as String,
    title = this["title"] as String,
    subtitle = this["subtitle"] as? String,
    imageUrl = this["imageUrl"] as? String,
    targetTrackId = this["targetTrackId"] as? String,
    targetAlbumId = this["targetAlbumId"] as? String,
    targetPlaylistId = this["targetPlaylistId"] as? String,
)
