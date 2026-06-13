package app.xwd.data

import android.content.Context
import app.xwd.sources.PuzzleSource
import app.xwd.sources.PuzzleSources

/** The runtime set of feeds: built-in sources plus the user's custom feeds. */
object SourceRegistry {

    fun resolved(context: Context): List<PuzzleSource> =
        PuzzleSources.all + Settings.getCustomFeeds(context).map(PuzzleSources::fromCustom)

    fun byId(context: Context, id: String): PuzzleSource? =
        resolved(context).firstOrNull { it.id == id }
}
