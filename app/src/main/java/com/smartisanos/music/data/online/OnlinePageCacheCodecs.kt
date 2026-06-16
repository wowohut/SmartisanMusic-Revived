package com.smartisanos.music.data.online

import org.json.JSONArray
import org.json.JSONObject

internal object OnlinePageCacheCodecs {
    val Tracks: OnlinePageCacheCodec<List<OnlineTrack>> = listCodec(
        encodeItem = OnlineTrack::toJsonObject,
        decodeItem = ::onlineTrackFromJson,
    )

    val Playlists: OnlinePageCacheCodec<List<OnlinePlaylist>> = listCodec(
        encodeItem = OnlinePlaylist::toJsonObject,
        decodeItem = ::onlinePlaylistFromJson,
    )

    val Albums: OnlinePageCacheCodec<List<OnlineAlbum>> = listCodec(
        encodeItem = OnlineAlbum::toJsonObject,
        decodeItem = ::onlineAlbumFromJson,
    )

    val Artists: OnlinePageCacheCodec<List<OnlineArtist>> = listCodec(
        encodeItem = OnlineArtist::toJsonObject,
        decodeItem = ::onlineArtistFromJson,
    )

    val Banners: OnlinePageCacheCodec<List<OnlineBanner>> = listCodec(
        encodeItem = OnlineBanner::toJsonObject,
        decodeItem = ::onlineBannerFromJson,
    )

    val Radios: OnlinePageCacheCodec<List<OnlineRadio>> = listCodec(
        encodeItem = OnlineRadio::toJsonObject,
        decodeItem = ::onlineRadioFromJson,
    )

    val ArtistIntroductions: OnlinePageCacheCodec<List<OnlineArtistIntroduction>> = listCodec(
        encodeItem = OnlineArtistIntroduction::toJsonObject,
        decodeItem = ::onlineArtistIntroductionFromJson,
    )

    val AccountPlaylists: OnlinePageCacheCodec<List<OnlineAccountPlaylist>> = listCodec(
        encodeItem = OnlineAccountPlaylist::toJsonObject,
        decodeItem = ::onlineAccountPlaylistFromJson,
    )

    val HotKeywords: OnlinePageCacheCodec<List<OnlineSearchHotKeyword>> = listCodec(
        encodeItem = OnlineSearchHotKeyword::toJsonObject,
        decodeItem = ::onlineSearchHotKeywordFromJson,
    )

    val TrackIds: OnlinePageCacheCodec<Set<String>> = OnlinePageCacheCodec(
        encode = { ids ->
            JSONObject().put(
                ItemsKey,
                JSONArray().also { array ->
                    ids.forEach(array::put)
                },
            )
        },
        decode = { root ->
            root.optJSONArray(ItemsKey)
                ?.toStringSet()
                ?: emptySet()
        },
    )

    val MusicHome: OnlinePageCacheCodec<OnlineMusicHome> = OnlinePageCacheCodec(
        encode = OnlineMusicHome::toJsonObject,
        decode = ::onlineMusicHomeFromJson,
    )

    val RadioHome: OnlinePageCacheCodec<OnlineRadioHome> = OnlinePageCacheCodec(
        encode = OnlineRadioHome::toJsonObject,
        decode = ::onlineRadioHomeFromJson,
    )

    val SearchResults: OnlinePageCacheCodec<OnlineSearchResults> = OnlinePageCacheCodec(
        encode = OnlineSearchResults::toJsonObject,
        decode = ::onlineSearchResultsFromJson,
    )
}

private const val ItemsKey = "items"

private fun <T> listCodec(
    encodeItem: (T) -> JSONObject,
    decodeItem: (JSONObject) -> T?,
): OnlinePageCacheCodec<List<T>> {
    return OnlinePageCacheCodec(
        encode = { items ->
            JSONObject().put(
                ItemsKey,
                JSONArray().also { array ->
                    items.forEach { item -> array.put(encodeItem(item)) }
                },
            )
        },
        decode = { root ->
            root.optJSONArray(ItemsKey)
                ?.toJsonObjects()
                ?.mapNotNull(decodeItem)
                ?: emptyList()
        },
    )
}

private fun OnlineTrack.toJsonObject(): JSONObject {
    return JSONObject()
        .put("source", source)
        .put("trackId", trackId)
        .put("title", title)
        .put("artist", artist)
        .putNullable("album", album)
        .put("durationMs", durationMs)
        .putNullable("artworkUrl", artworkUrl)
}

private fun onlineTrackFromJson(root: JSONObject): OnlineTrack? {
    val source = root.optCacheString("source") ?: return null
    val trackId = root.optCacheString("trackId") ?: return null
    val title = root.optCacheString("title") ?: return null
    return OnlineTrack(
        source = source,
        trackId = trackId,
        title = title,
        artist = root.optCacheString("artist").orEmpty(),
        album = root.optCacheString("album"),
        durationMs = root.optLong("durationMs", 0L).coerceAtLeast(0L),
        artworkUrl = root.optCacheString("artworkUrl"),
    )
}

private fun OnlinePlaylist.toJsonObject(): JSONObject {
    return JSONObject()
        .put("provider", provider.sourceId)
        .put("playlistId", playlistId)
        .put("title", title)
        .putNullable("subtitle", subtitle)
        .putNullable("artworkUrl", artworkUrl)
        .put("trackCount", trackCount)
        .put("playCount", playCount)
        .put("kind", kind.name)
}

private fun onlinePlaylistFromJson(root: JSONObject): OnlinePlaylist? {
    val provider = root.optProvider() ?: return null
    val playlistId = root.optCacheString("playlistId") ?: return null
    val title = root.optCacheString("title") ?: return null
    return OnlinePlaylist(
        provider = provider,
        playlistId = playlistId,
        title = title,
        subtitle = root.optCacheString("subtitle"),
        artworkUrl = root.optCacheString("artworkUrl"),
        trackCount = root.optInt("trackCount", 0).coerceAtLeast(0),
        playCount = root.optLong("playCount", 0L).coerceAtLeast(0L),
        kind = root.optCacheString("kind")
            ?.let { kind -> runCatching { OnlinePlaylistKind.valueOf(kind) }.getOrNull() }
            ?: OnlinePlaylistKind.Featured,
    )
}

private fun OnlineAlbum.toJsonObject(): JSONObject {
    return JSONObject()
        .put("provider", provider.sourceId)
        .put("albumId", albumId)
        .put("title", title)
        .putNullable("artist", artist)
        .putNullable("artworkUrl", artworkUrl)
        .put("trackCount", trackCount)
        .put("publishTimeMs", publishTimeMs)
}

private fun onlineAlbumFromJson(root: JSONObject): OnlineAlbum? {
    val provider = root.optProvider() ?: return null
    val albumId = root.optCacheString("albumId") ?: return null
    val title = root.optCacheString("title") ?: return null
    return OnlineAlbum(
        provider = provider,
        albumId = albumId,
        title = title,
        artist = root.optCacheString("artist"),
        artworkUrl = root.optCacheString("artworkUrl"),
        trackCount = root.optInt("trackCount", 0).coerceAtLeast(0),
        publishTimeMs = root.optLong("publishTimeMs", 0L).coerceAtLeast(0L),
    )
}

private fun OnlineArtist.toJsonObject(): JSONObject {
    return JSONObject()
        .put("provider", provider.sourceId)
        .put("artistId", artistId)
        .put("name", name)
        .putNullable("subtitle", subtitle)
        .putNullable("artworkUrl", artworkUrl)
        .put("trackCount", trackCount)
        .put("albumCount", albumCount)
}

private fun onlineArtistFromJson(root: JSONObject): OnlineArtist? {
    val provider = root.optProvider() ?: return null
    val artistId = root.optCacheString("artistId") ?: return null
    val name = root.optCacheString("name") ?: return null
    return OnlineArtist(
        provider = provider,
        artistId = artistId,
        name = name,
        subtitle = root.optCacheString("subtitle"),
        artworkUrl = root.optCacheString("artworkUrl"),
        trackCount = root.optInt("trackCount", 0).coerceAtLeast(0),
        albumCount = root.optInt("albumCount", 0).coerceAtLeast(0),
    )
}

private fun OnlineArtistIntroduction.toJsonObject(): JSONObject {
    return JSONObject()
        .put("title", title)
        .put("text", text)
}

private fun onlineArtistIntroductionFromJson(root: JSONObject): OnlineArtistIntroduction? {
    val title = root.optCacheString("title") ?: return null
    val text = root.optCacheString("text") ?: return null
    return OnlineArtistIntroduction(
        title = title,
        text = text,
    )
}

private fun OnlineBanner.toJsonObject(): JSONObject {
    return JSONObject()
        .put("provider", provider.sourceId)
        .put("bannerId", bannerId)
        .put("title", title)
        .putNullable("subtitle", subtitle)
        .putNullable("imageUrl", imageUrl)
        .putNullable("targetTrackId", targetTrackId)
        .putNullable("targetAlbumId", targetAlbumId)
        .putNullable("targetPlaylistId", targetPlaylistId)
}

private fun onlineBannerFromJson(root: JSONObject): OnlineBanner? {
    val provider = root.optProvider() ?: return null
    val bannerId = root.optCacheString("bannerId") ?: return null
    val title = root.optCacheString("title") ?: return null
    return OnlineBanner(
        provider = provider,
        bannerId = bannerId,
        title = title,
        subtitle = root.optCacheString("subtitle"),
        imageUrl = root.optCacheString("imageUrl"),
        targetTrackId = root.optCacheString("targetTrackId"),
        targetAlbumId = root.optCacheString("targetAlbumId"),
        targetPlaylistId = root.optCacheString("targetPlaylistId"),
    )
}

private fun OnlineRadio.toJsonObject(): JSONObject {
    return JSONObject()
        .put("provider", provider.sourceId)
        .put("radioId", radioId)
        .put("title", title)
        .putNullable("subtitle", subtitle)
        .putNullable("category", category)
        .putNullable("creator", creator)
        .putNullable("artworkUrl", artworkUrl)
        .put("programCount", programCount)
        .put("playCount", playCount)
}

private fun onlineRadioFromJson(root: JSONObject): OnlineRadio? {
    val provider = root.optProvider() ?: return null
    val radioId = root.optCacheString("radioId") ?: return null
    val title = root.optCacheString("title") ?: return null
    return OnlineRadio(
        provider = provider,
        radioId = radioId,
        title = title,
        subtitle = root.optCacheString("subtitle"),
        category = root.optCacheString("category"),
        creator = root.optCacheString("creator"),
        artworkUrl = root.optCacheString("artworkUrl"),
        programCount = root.optInt("programCount", 0).coerceAtLeast(0),
        playCount = root.optLong("playCount", 0L).coerceAtLeast(0L),
    )
}

private fun OnlineAccountPlaylist.toJsonObject(): JSONObject {
    return JSONObject()
        .put("provider", provider.sourceId)
        .put("playlistId", playlistId)
        .put("title", title)
        .put("trackCount", trackCount)
        .put("isLikedSongs", isLikedSongs)
        .put("isEditable", isEditable)
        .putNullable("subtitle", subtitle)
        .putNullable("artworkUrl", artworkUrl)
}

private fun onlineAccountPlaylistFromJson(root: JSONObject): OnlineAccountPlaylist? {
    val provider = root.optProvider() ?: return null
    val playlistId = root.optCacheString("playlistId") ?: return null
    val title = root.optCacheString("title") ?: return null
    return OnlineAccountPlaylist(
        provider = provider,
        playlistId = playlistId,
        title = title,
        trackCount = root.optInt("trackCount", 0).coerceAtLeast(0),
        isLikedSongs = root.optBoolean("isLikedSongs", false),
        isEditable = root.optBoolean("isEditable", false),
        subtitle = root.optCacheString("subtitle"),
        artworkUrl = root.optCacheString("artworkUrl"),
    )
}

private fun OnlineSearchHotKeyword.toJsonObject(): JSONObject {
    return JSONObject()
        .put("keyword", keyword)
        .putNullable("subtitle", subtitle)
        .put("score", score)
}

private fun onlineSearchHotKeywordFromJson(root: JSONObject): OnlineSearchHotKeyword? {
    val keyword = root.optCacheString("keyword") ?: return null
    return OnlineSearchHotKeyword(
        keyword = keyword,
        subtitle = root.optCacheString("subtitle"),
        score = root.optLong("score", 0L).coerceAtLeast(0L),
    )
}

private fun OnlineMusicHome.toJsonObject(): JSONObject {
    return JSONObject()
        .put("tracks", OnlinePageCacheCodecs.Tracks.encode(tracks))
        .put("playlists", OnlinePageCacheCodecs.Playlists.encode(playlists))
        .put("charts", OnlinePageCacheCodecs.Playlists.encode(charts))
        .put("albums", OnlinePageCacheCodecs.Albums.encode(albums))
        .put("artists", OnlinePageCacheCodecs.Artists.encode(artists))
}

private fun onlineMusicHomeFromJson(root: JSONObject): OnlineMusicHome {
    return OnlineMusicHome(
        tracks = root.optJSONObject("tracks")?.let(OnlinePageCacheCodecs.Tracks.decode).orEmpty(),
        playlists = root.optJSONObject("playlists")?.let(OnlinePageCacheCodecs.Playlists.decode).orEmpty(),
        charts = root.optJSONObject("charts")?.let(OnlinePageCacheCodecs.Playlists.decode).orEmpty(),
        albums = root.optJSONObject("albums")?.let(OnlinePageCacheCodecs.Albums.decode).orEmpty(),
        artists = root.optJSONObject("artists")?.let(OnlinePageCacheCodecs.Artists.decode).orEmpty(),
    )
}

private fun OnlineRadioHome.toJsonObject(): JSONObject {
    return JSONObject()
        .put("tracks", OnlinePageCacheCodecs.Tracks.encode(tracks))
        .put("radios", OnlinePageCacheCodecs.Radios.encode(radios))
}

private fun onlineRadioHomeFromJson(root: JSONObject): OnlineRadioHome {
    return OnlineRadioHome(
        tracks = root.optJSONObject("tracks")?.let(OnlinePageCacheCodecs.Tracks.decode).orEmpty(),
        radios = root.optJSONObject("radios")?.let(OnlinePageCacheCodecs.Radios.decode).orEmpty(),
    )
}

private fun OnlineSearchResults.toJsonObject(): JSONObject {
    return JSONObject()
        .put("query", query)
        .put("tracks", OnlinePageCacheCodecs.Tracks.encode(tracks))
        .put("artists", OnlinePageCacheCodecs.Artists.encode(artists))
        .put("albums", OnlinePageCacheCodecs.Albums.encode(albums))
        .put("playlists", OnlinePageCacheCodecs.Playlists.encode(playlists))
}

private fun onlineSearchResultsFromJson(root: JSONObject): OnlineSearchResults? {
    val query = root.optCacheString("query") ?: return null
    return OnlineSearchResults(
        query = query,
        tracks = root.optJSONObject("tracks")?.let(OnlinePageCacheCodecs.Tracks.decode).orEmpty(),
        artists = root.optJSONObject("artists")?.let(OnlinePageCacheCodecs.Artists.decode).orEmpty(),
        albums = root.optJSONObject("albums")?.let(OnlinePageCacheCodecs.Albums.decode).orEmpty(),
        playlists = root.optJSONObject("playlists")?.let(OnlinePageCacheCodecs.Playlists.decode).orEmpty(),
    )
}

private fun JSONObject.optProvider(): OnlineMusicProvider? {
    val sourceId = optCacheString("provider") ?: return null
    return OnlineMusicProvider.entries.firstOrNull { provider -> provider.sourceId == sourceId }
}

private fun JSONObject.putNullable(
    name: String,
    value: String?,
): JSONObject {
    return if (value.isNullOrBlank()) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
}

private fun JSONObject.optCacheString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name)
        .takeIf(String::isNotBlank)
        ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
}

private fun JSONArray.toJsonObjects(): List<JSONObject> {
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}

private fun JSONArray.toStringSet(): Set<String> {
    return buildSet {
        for (index in 0 until length()) {
            optString(index)
                .takeIf(String::isNotBlank)
                ?.let(::add)
        }
    }
}
