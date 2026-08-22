package com.lasse.speedometer.data.io

/**
 * Which of the pictures the app still holds a read grant for are no longer
 * attached to anything.
 *
 * Attaching a photo takes a persistable grant so the thumbnail survives a
 * restart. Nothing ever gave one back: removing a photo, deleting the trip it
 * was on, or wiping history all dropped the rows and left the grant. Android
 * caps how many an app may hold, and past the cap taking a new one fails — so
 * the failure this prevents is not a leak anyone would notice, it is photos
 * quietly stopping working on a phone that has been used for a while.
 *
 * Pure so the rule can be tested without a ContentResolver: the caller reads
 * the two sets and applies the answer.
 */
object PhotoGrants {

    fun stale(persisted: Collection<String>, referenced: Collection<String>): List<String> {
        if (persisted.isEmpty()) return emptyList()
        val kept = referenced.toHashSet()
        return persisted.filterNot { it in kept }
    }
}
