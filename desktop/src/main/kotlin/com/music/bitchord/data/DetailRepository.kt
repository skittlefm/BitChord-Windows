package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

object DetailRepository {

    data class SongPage(
        val songs: List<Song>,
        val continuation: String?,
        val suggested: List<Song> = emptyList(),
        val library: LibraryState? = null,
        val owned: Boolean? = null,
        val header: InnertubeParser.BrowseHeader? = null,
        val description: String? = null,
    )

    suspend fun browseSongs(browseId: String): SongPage =
        withContext(Dispatchers.IO) {
            val response = Innertube.browse(browseId)
            val metadata = pageOf(response)

            // Alguns álbuns mostram apenas uma prévia.
            // A playlist indicada pelo álbum contém suas faixas.
            val playlistId = if (browseId.startsWith("MPREb")) {
                InnertubeParser.parseAlbumPlaylistId(response)
            } else {
                null
            }

            val page = if (playlistId != null) {
                val playlistBrowseId =
                    "VL${playlistId.removePrefix("VL")}"

                val tracks = pageOf(
                    Innertube.browse(playlistBrowseId),
                )

                tracks.copy(
                    header = metadata.header,
                    description = metadata.description,
                    library = metadata.library,
                )
            } else {
                metadata
            }

            if (browseId.startsWith("VL")) {
                page.copy(
                    owned = InnertubeParser.parsePlaylistOwned(response),
                )
            } else {
                page
            }
        }

    suspend fun moreSongs(token: String): SongPage =
        withContext(Dispatchers.IO) {
            pageOf(Innertube.browseContinuation(token))
        }

    private fun pageOf(response: JsonObject): SongPage {
        val shelf = InnertubeParser.parsePlaylistShelf(response)

        return SongPage(
            songs = shelf?.songs
                ?: InnertubeParser.collectSongsDeep(response)
                    .distinctBy { it.videoId },

            // Uma playlist sem continuação chegou ao fim.
            // Nesse caso, não buscamos tokens em outras seções.
            continuation = if (shelf != null) {
                shelf.continuation
            } else {
                InnertubeParser.continuationToken(response)
            },

            suggested = shelf?.suggested.orEmpty(),
            library = InnertubeParser.parseLibraryState(response),
            header = InnertubeParser.parseBrowseHeader(response),
            description = InnertubeParser.parseDescription(response),
        )
    }
}