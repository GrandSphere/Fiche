# Fiche JSON lists
* Used to compile a list of shows for import, without tracking completion or rating.
* Import via **Settings → Backup → Import database**, or share the file to Fiche.
* In JSON, Always set `"kind": "share"`. 

## Catalogues

Defaults:

| Type | Catalogue | `url` |
| --- | --- | --- |
| Series | [TVMaze](https://www.tvmaze.com) | `https://www.tvmaze.com/shows/{id}` |
| Movies | [TMDB](https://www.themoviedb.org) | `https://www.themoviedb.org/movie/{id}` |
| Books | [Open Library](https://openlibrary.org) | `https://openlibrary.org/works/OL103936W` |
| Games | [RAWG](https://rawg.io) | `https://rawg.io/games/{slug}` |
| Anime | TVMaze | `https://www.tvmaze.com/shows/{id}` |

Also supported: 
* Series on TMDB (`/tv/{id}`); 
* Anime on TMDB, [Jikan](https://jikan.moe), or [MyAnimeList](https://myanimelist.net) (`/anime/{id}`).

## File description


| Field | Required | Notes |
| --- | --- | --- |
| `title` | yes | Display name. |
| `category` | yes | Type |
| `url` |  | Item url from accepted catalogue above|
| `author` |  | Author of the book; Game developer. |
| `firstDate` |  | `YYYY-MM-DD`. Premiere / release. |
| `lastDate` |  | `YYYY-MM-DD`. For Series and anime only. |
| `genres` |  | Comma-separated string, e.g. `"Drama, Crime"`. |
| `overview` |  | Plot or summary. |

`category` type options:
* `SERIES`
* `MOVIE`
* `ANIME`
* `BOOK`
* `GAME`.

Duplicate `url`, or `title` in a specific `category`, is skipped.

## Example

```json
{
  "kind": "share",
  "titles": [
    {
      "title": "Breaking Bad",
      "category": "SERIES",
      "url": "https://www.tvmaze.com/shows/169",
      "firstDate": "2008-01-20",
      "lastDate": "2013-09-29",
      "genres": "Drama, Crime, Thriller",
      "overview": "A chemistry teacher turned meth cook."
    },
    {
      "title": "The A-Team",
      "category": "SERIES",
      "url": "https://www.tvmaze.com/shows/1058",
      "firstDate": "1983-01-23",
      "lastDate": "1987-03-08",
      "genres": "Action, Adventure"
    },
    {
      "title": "Kino no Tabi",
      "category": "ANIME",
      "url": "https://www.tvmaze.com/shows/11841",
      "firstDate": "2003-04-08",
      "lastDate": "2003-07-08",
      "genres": "Adventure, Anime, Drama"
    },
    {
      "title": "Rush Hour",
      "category": "MOVIE",
      "url": "https://www.themoviedb.org/movie/2109",
      "firstDate": "1998-09-18",
      "genres": "Action, Comedy, Crime"
    },
    {
      "title": "Rush Hour 2",
      "category": "MOVIE",
      "url": "https://www.themoviedb.org/movie/5175",
      "firstDate": "2001-08-03",
      "genres": "Action, Comedy, Crime"
    },
    {
      "title": "How to Solve It",
      "category": "BOOK",
      "author": "George Pólya",
      "url": "https://openlibrary.org/works/OL4569830W",
      "firstDate": "1945-01-01",
      "genres": "Mathematics, Science"
    },
    {
      "title": "Nineteen Eighty-Four",
      "category": "BOOK",
      "author": "George Orwell",
      "url": "https://openlibrary.org/works/OL103936W",
      "firstDate": "1949-06-08",
      "genres": "Dystopia, Science Fiction"
    },
    {
      "title": "Hades",
      "category": "GAME",
      "author": "Supergiant Games",
      "url": "https://rawg.io/games/hades",
      "firstDate": "2020-09-17",
      "genres": "Action, Indie, RPG"
    }
  ]
}
```
