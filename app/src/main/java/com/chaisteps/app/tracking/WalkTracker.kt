package com.chaisteps.app.tracking

import com.chaisteps.app.data.Repository
import com.chaisteps.app.data.WalkSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Holds the walk in progress (owned by [WalkService]) and its on-disk checkpoint, so a walk
 * interrupted by the system killing the app is recovered instead of lost.
 */
class WalkTracker(dir: File) {
    private val checkpointFile = File(dir, "active_walk.json")
    private val _active = MutableStateFlow<ActiveWalk?>(null)
    val active: StateFlow<ActiveWalk?> = _active.asStateFlow()

    /** The most recently finished walk, so the UI can show its summary. */
    private val _lastFinished = MutableStateFlow<WalkSession?>(null)
    val lastFinished: StateFlow<WalkSession?> = _lastFinished.asStateFlow()

    fun publish(walk: ActiveWalk?) {
        _active.value = walk
    }

    fun finished(session: WalkSession) {
        _lastFinished.value = session
    }

    fun consumeFinished() {
        _lastFinished.value = null
    }

    fun writeCheckpoint(c: WalkCheckpoint) {
        runCatching {
            val tmp = File(checkpointFile.parentFile, checkpointFile.name + ".tmp")
            tmp.writeText(Repository.json.encodeToString(WalkCheckpoint.serializer(), c))
            if (!tmp.renameTo(checkpointFile)) {
                checkpointFile.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun readCheckpoint(): WalkCheckpoint? {
        if (!checkpointFile.exists()) return null
        return runCatching { Repository.json.decodeFromString(WalkCheckpoint.serializer(), checkpointFile.readText()) }.getOrNull()
    }

    fun clearCheckpoint() {
        checkpointFile.delete()
    }

    /** Saves a walk left behind by a killed process. Call only when no walk is running. */
    fun recoverInterrupted(repo: Repository, weightKg: Double): WalkSession? {
        if (_active.value != null) return null
        val c = readCheckpoint() ?: return null
        clearCheckpoint()
        val (session, route) = WalkRecorder.recover(c, weightKg)
        if (session.distanceM < 10 && session.activeMillis < 60_000) return null
        repo.addWalk(session, route)
        return session
    }
}
