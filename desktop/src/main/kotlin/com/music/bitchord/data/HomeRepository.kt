package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HomeRepository {
    suspend fun home(): HomeFeed = withContext(Dispatchers.IO) {
        val response = Innertube.browse("FEmusic_home")

        HomeFeed(
            shelves = InnertubeParser.parseHome(response),
            continuation = InnertubeParser.continuationToken(response),
        )
    }
}