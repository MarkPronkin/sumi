package dev.cinema.crunchyroll

import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.data.account.*
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TokenVaultTest {
    @Test fun refreshTokenIsEncryptedAtRestAndLogoutRemovesIt() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vault=KeystoreTokenVault(context,"instrumentation")
        try {
            val session=StoredSession("refresh-test-fixture-only","device-fixture","account-fixture","profile-fixture")
            vault.write(session)
            val bytes=File(context.noBackupFilesDir,"crunchyroll-instrumentation-session.aes").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("refresh-test-fixture-only"))
            assertEquals(session.refreshToken,vault.read()?.refreshToken)
            assertEquals(session.profileId,vault.read()?.profileId)
            vault.clear();assertNull(vault.read())
        } finally {
            vault.clear()
            KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry("cinema.instrumentation.session.v1")}
        }
    }
}
