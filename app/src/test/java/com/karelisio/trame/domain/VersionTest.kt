package com.karelisio.trame.domain

import com.karelisio.trame.data.update.UpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun compare() {
        assertTrue(UpdateManager.isNewer("v1.10.0", "1.9.3"))
        assertTrue(UpdateManager.isNewer("0.2.0", "0.1.9"))
        assertFalse(UpdateManager.isNewer("v0.1.0", "0.1.0"))
        assertFalse(UpdateManager.isNewer("0.1.0", "0.2.0"))
        assertTrue(UpdateManager.isNewer("1.0", "0.9.9"))
    }
}
