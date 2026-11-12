# Fiche tracked JSON lists

* Used to compile a list of shows for import, including tracking completion or rating.
* Import via **Settings → Backup → Import database**, or share the file to Fiche.
* In JSON, always set `"kind": "tracked"`.
* Same `mediaType` + `remoteId` **overwrites** that title (rating, completed, and the other fields below). Titles not in the file are left alone.
* `completed` ticks the whole title. There is no per-episode watched flag in this format, and episode lists are not imported.

## Catalogues

Defaults:

| Type | Catalogue | `remoteId` | `catalogUrl` |
| --- | --- | --- | --- |
| Series | [TVMaze](https://www.tvmaze.com) | show id | `https://www.tvmaze.com/shows/{id}` |
| Movies | [TMDB](https://www.themoviedb.org) | movie id | `https://www.themoviedb.org/movie/{id}` |
| Books | [Open Library](https://openlibrary.org) | book id | `https://openlibrary.org/works/OL103936W` |
| Games | [RAWG](https://rawg.io) | slug | `https://rawg.io/games/{slug}` |
| Anime | TVMaze | show id | `https://www.tvmaze.com/shows/{id}` |

Also supported:
* Series on TMDB (`/tv/{id}`);
* Anime on TMDB, [Jikan](https://jikan.moe), or [MyAnimeList](https://myanimelist.net) (`/anime/{id}`).

## File description

| Field | Required | Notes |
| --- | --- | --- |
| `title` | yes | Display name. |
| `mediaType` | yes | Type |
| `remoteId` | yes | Catalogue id from the table above. |
| `catalogUrl` |  | Item page on that catalogue. |
| `author` |  | Author of the book; Game developer. |
| `firstDate` |  | `YYYY-MM-DD`. Premiere / release. |
| `lastDate` |  | `YYYY-MM-DD`. For series and anime only. |
| `year` |  | Integer. |
| `genres` |  | Comma-separated string, e.g. `"Drama, Crime"`. |
| `overview` |  | Plot or summary. |
| `posterUrl` |  | Image URL. |
| `userRating` |  | Omit if unrated. `0` = zero, `1`–`10` normal, `11` = masterpiece. |
| `completed` |  | `true` marks the title watched / read / beaten. |
| `pageCount` |  | Books. |
| `watchedUnits` |  | Books: pages read. |

`mediaType` options:
* `SERIES`
* `MOVIE`
* `ANIME`
* `BOOK`
* `GAME`

## Example

```json
{
  "kind": "tracked",
  "titles": [
    {
      "title": "Breaking Bad",
      "mediaType": "SERIES",
      "remoteId": "169",
      "catalogUrl": "https://www.tvmaze.com/shows/169",
      "firstDate": "2008-01-20",
      "lastDate": "2013-09-29",
      "year": 2008,
      "genres": "Drama, Crime, Thriller",
      "overview": "A chemistry teacher turned meth cook.",
      "userRating": 10,
      "completed": true
    },
    {
      "title": "The A-Team",
      "mediaType": "SERIES",
      "remoteId": "1058",
      "catalogUrl": "https://www.tvmaze.com/shows/1058",
      "firstDate": "1983-01-23",
      "lastDate": "1987-03-08",
      "year": 1983,
      "genres": "Action, Adventure",
      "completed": false
    },
    {
      "title": "Kino no Tabi",
      "mediaType": "ANIME",
      "remoteId": "11841",
      "catalogUrl": "https://www.tvmaze.com/shows/11841",
      "firstDate": "2003-04-08",
      "lastDate": "2003-07-08",
      "year": 2003,
      "genres": "Adventure, Anime, Drama",
      "userRating": 8,
      "completed": true
    },
    {
      "title": "Rush Hour",
      "mediaType": "MOVIE",
      "remoteId": "2109",
      "catalogUrl": "https://www.themoviedb.org/movie/2109",
      "firstDate": "1998-09-18",
      "year": 1998,
      "genres": "Action, Comedy, Crime",
      "userRating": 9,
      "completed": true
    },
    {
      "title": "Rush Hour 2",
      "mediaType": "MOVIE",
      "remoteId": "5175",
      "catalogUrl": "https://www.themoviedb.org/movie/5175",
      "firstDate": "2001-08-03",
      "year": 2001,
      "genres": "Action, Comedy, Crime",
      "completed": false
    },
    {
      "title": "How to Solve It",
      "mediaType": "BOOK",
      "remoteId": "OL4569830W",
      "catalogUrl": "https://openlibrary.org/works/OL4569830W",
      "author": "George Pólya",
      "firstDate": "1945-01-01",
      "year": 1945,
      "genres": "Mathematics, Science",
      "pageCount": 253,
      "watchedUnits": 253,
      "completed": true
    },
    {
      "title": "Nineteen Eighty-Four",
      "mediaType": "BOOK",
      "remoteId": "OL103936W",
      "catalogUrl": "https://openlibrary.org/works/OL103936W",
      "author": "George Orwell",
      "firstDate": "1949-06-08",
      "year": 1949,
      "genres": "Dystopia, Science Fiction",
      "pageCount": 328,
      "watchedUnits": 40,
      "completed": false
    },
    {
      "title": "Hades",
      "mediaType": "GAME",
      "remoteId": "hades",
      "catalogUrl": "https://rawg.io/games/hades",
      "author": "Supergiant Games",
      "firstDate": "2020-09-17",
      "year": 2020,
      "genres": "Action, Indie, RPG",
      "userRating": 10,
      "completed": true
    }
  ]
}
```
