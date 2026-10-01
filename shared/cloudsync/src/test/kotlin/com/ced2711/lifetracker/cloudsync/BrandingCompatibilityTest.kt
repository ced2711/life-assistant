package com.ced2711.lifetracker.cloudsync

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandingCompatibilityTest {
    @Test fun displayRenameDoesNotCreateANewCloudNamespace() {
        assertEquals("life-tracker-sync-v1", CLOUD_SYNC_PROTOCOL)
        assertEquals("life-tracker-sync-", CLOUD_BACKUP_FILE_PREFIX)
        assertEquals("application/vnd.ced2711.life-tracker-backup", CLOUD_BACKUP_MIME_TYPE)
    }
}
