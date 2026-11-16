package dev.cinema.crunchyroll.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Commits before reporting success. A failed write leaves the displayed list unchanged. */
class WatchlistActions(private val store: UserStore) {
    private val mutation = Mutex()
    suspend fun set(profileId: String, showId: String, included: Boolean) = mutation.withLock {
        store.update { state ->
            state.updateProfile(profileId) { profile ->
                profile.copy(watchlist = if (included) profile.watchlist + showId else profile.watchlist - showId)
            }
        }
    }
}
