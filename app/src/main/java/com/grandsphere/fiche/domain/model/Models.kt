package com.grandsphere.fiche.domain.model

import com.grandsphere.fiche.data.local.entity.TitleEntity

enum class MediaType(val label: String, val libraryLabel: String) {
    SERIES("Series", "Series"),
    MOVIE("Movies", "Movies"),
    ANIME("Anime", "Anime"),
    BOOK("Books", "Books"),
    GAME("Games", "Games");

    val tracksEpisodes: Boolean get() = this == SERIES || this == ANIME

    fun next(among: List<MediaType> = entries): MediaType? {
        val list = among.ifEmpty { entries }
        val index = list.indexOf(this)
        if (index < 0) return list.firstOrNull()
        return list.getOrNull(index + 1)
    }

    fun previous(among: List<MediaType> = entries): MediaType? {
        val list = among.ifEmpty { entries }
        val index = list.indexOf(this)
        if (index < 0) return list.firstOrNull()
        return if (index <= 0) null else list.getOrNull(index - 1)
    }

    companion object {
        fun parseEnabled(raw: String?): List<MediaType> {
            val parsed = raw.orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { name -> entries.firstOrNull { it.name == name } }
                .distinct()
            return parsed.ifEmpty { entries.toList() }
        }

        fun encodeEnabled(types: List<MediaType>): String =
            entries.filter { it in types }.joinToString(",") { it.name }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object AppearanceDefaults {
    const val DARK_BACKGROUND = 0xFF000000.toInt()
    const val DARK_GROUP = 0xFF121212.toInt()
    const val DARK_ACTION = 0xFF3A675F.toInt()
    const val DARK_ALTERNATE = 0xFF2C2C2C.toInt()
    const val LIGHT_BACKGROUND = 0xFFE7E0D0.toInt()
    const val LIGHT_GROUP = 0xFFD4CBB8.toInt()
    const val LIGHT_ACTION = 0xFF0F766E.toInt()
    const val LIGHT_ALTERNATE = 0xFFC9C0AD.toInt()

    fun background(light: Boolean): Int = if (light) LIGHT_BACKGROUND else DARK_BACKGROUND
    fun group(light: Boolean): Int = if (light) LIGHT_GROUP else DARK_GROUP
    fun action(light: Boolean): Int = if (light) LIGHT_ACTION else DARK_ACTION
    fun alternate(light: Boolean): Int = if (light) LIGHT_ALTERNATE else DARK_ALTERNATE
    fun isLight(mode: ThemeMode): Boolean = mode == ThemeMode.LIGHT
}

enum class RelationType { SEQUEL, PREQUEL, SPIN_OFF, COLLECTION, SERIES }

enum class SortOption(val label: String) {
    TITLE("Title"),
    YEAR("Year"),
    RATING("Rating"),
    PROGRESS("Progress"),
    DATE_ADDED("Date added"),
    COMPLETED_FIRST("Completed first"),
    INCOMPLETE_FIRST("Not completed first")
}

data class LibraryFilter(
    val query: String = "",
    val genres: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val completed: Boolean? = null,
    val cancelled: Boolean? = null,
    val moreOfThis: Boolean? = null,
    val ratedOnly: Boolean? = null,
    val recommended: Boolean? = null,
    val showHidden: Boolean = false,
    val hideCompleted: Boolean = false,
    val hideIncomplete: Boolean = false,
    val sort: SortOption = SortOption.RATING,
    val sorts: List<SortOption> = emptyList()
) {
    fun sortChain(): List<SortOption> = sorts.ifEmpty { listOf(sort) }
}

data class DiscoverFilter(
    val query: String = "",
    val genres: Set<String> = emptySet(),
    val disallowGenres: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val yearFromText: String = "",
    val yearToText: String = "",
    val newReleases: Boolean = false,
    val endedOnly: Boolean = false,
    val actor: String = "",
    val author: String = "",
    val producer: String = "",
    val developer: String = "",
    val publisher: String = ""
)

enum class DiscoverCriterion(val label: String) {
    GENRE("Genre"),
    DISALLOW_GENRE("Disallow genre"),
    YEAR("Date"),
    ACTOR("Actor"),
    AUTHOR("Author"),
    PRODUCER("Producer"),
    DEVELOPER("Developer"),
    PUBLISHER("Publisher"),
    NEW_RELEASES("New releases"),
    ENDED("Ended only")
}

object GenreCatalog {
    /** TVMaze genre tags used for series/anime discovery. */
    val tvmaze = listOf(
        "Action", "Adventure", "Animation", "Anime", "Children", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "Food", "Game Show",
        "Home and Garden", "Horror", "Mystery", "News", "Reality", "Romance",
        "Science-Fiction", "Soap", "Talk Show", "Thriller", "Western"
    )
    val screen = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music",
        "Mystery", "Romance", "Science Fiction", "Thriller", "War", "Western"
    )
    val books = listOf(
        "Fiction", "Nonfiction", "Fantasy", "Science Fiction", "Mystery", "Thriller",
        "Romance", "History", "Biography", "Memoir", "Young Adult", "Children",
        "Horror", "Poetry", "Classics", "Philosophy", "Science", "Self-Help",
        "Business", "Travel", "Comics", "Graphic Novels", "Crime", "Adventure",
        "Historical Fiction", "Contemporary", "Dystopia", "Religion"
    )

    val games = listOf(
        "Action", "Adventure", "RPG", "Shooter", "Strategy", "Simulation", "Sports",
        "Racing", "Puzzle", "Platformer", "Indie", "Arcade", "Fighting", "Horror",
        "Survival", "Massively Multiplayer", "Family", "Card", "Educational"
    )

    fun forType(type: MediaType): List<String> = when (type) {
        MediaType.BOOK -> books
        MediaType.GAME -> games
        MediaType.SERIES, MediaType.ANIME -> tvmaze
        MediaType.MOVIE -> screen
    }
}

fun TitleEntity.showsFranchiseProgress(): Boolean =
    mediaType == MediaType.MOVIE.name && countSequelsInCompletion && totalUnits > 1

fun TitleEntity.displayCompleted(): Boolean =
    if (showsFranchiseProgress()) watchedUnits >= totalUnits else completed

fun TitleEntity.displayWatching(): Boolean =
    if (showsFranchiseProgress()) watchedUnits > 0 && watchedUnits < totalUnits
    else watchedUnits > 0 && !completed
