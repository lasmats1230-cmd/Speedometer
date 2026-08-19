package com.lasse.speedometer

import com.lasse.speedometer.data.io.PhotoGrants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGrantsTest {

    @Test
    fun `a grant nothing points at is stale`() {
        val stale = PhotoGrants.stale(
            persisted = listOf("content://media/1", "content://media/2"),
            referenced = listOf("content://media/1"),
        )

        assertEquals(listOf("content://media/2"), stale)
    }

    @Test
    fun `a picture still on a trip keeps its grant`() {
        val uris = listOf("content://media/1", "content://media/2")

        assertTrue(PhotoGrants.stale(persisted = uris, referenced = uris).isEmpty())
    }

    @Test
    fun `the same picture on two trips is still referenced once it is on one`() {
        val stale = PhotoGrants.stale(
            persisted = listOf("content://media/1"),
            referenced = listOf("content://media/1", "content://media/1"),
        )

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `holding nothing means nothing to hand back`() {
        assertTrue(PhotoGrants.stale(emptyList(), listOf("content://media/1")).isEmpty())
        assertTrue(PhotoGrants.stale(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `history wiped clean releases everything`() {
        val persisted = listOf("content://media/1", "content://media/2")

        assertEquals(persisted, PhotoGrants.stale(persisted, emptyList()))
    }
}
