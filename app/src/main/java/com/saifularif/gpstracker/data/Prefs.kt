package com.saifularif.gpstracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

class Prefs(private val context: Context) {
    private val onboardingDone = booleanPreferencesKey("onboarding_done")
    private val guideHidden = booleanPreferencesKey("guide_hidden")
    private val followHint = booleanPreferencesKey("follow_hint_seen")
    private val friendsHint = booleanPreferencesKey("friends_hint_seen")
    private val eventsSeen = longPreferencesKey("events_seen_ts")

    val onboardingDoneFlow: Flow<Boolean> = context.store.data.map { it[onboardingDone] ?: false }
    val guideHiddenFlow: Flow<Boolean> = context.store.data.map { it[guideHidden] ?: false }
    val followHintFlow: Flow<Boolean> = context.store.data.map { it[followHint] ?: false }
    val friendsHintFlow: Flow<Boolean> = context.store.data.map { it[friendsHint] ?: false }

    val eventsSeenFlow: Flow<Long> = context.store.data.map { it[eventsSeen] ?: 0L }

    suspend fun setEventsSeen(ts: Long) = context.store.edit { it[eventsSeen] = ts }
    suspend fun setOnboardingDone() = context.store.edit { it[onboardingDone] = true }
    suspend fun setGuideHidden(v: Boolean) = context.store.edit { it[guideHidden] = v }
    suspend fun setFollowHintSeen() = context.store.edit { it[followHint] = true }
    suspend fun setFriendsHintSeen() = context.store.edit { it[friendsHint] = true }
}
