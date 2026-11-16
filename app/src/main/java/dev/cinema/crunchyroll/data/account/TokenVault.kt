package dev.cinema.crunchyroll.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface TokenVault {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession)
    suspend fun clear()
}

/** Only refresh credentials are persisted. The AES key never leaves Android Keystore. */
class KeystoreTokenVault(context: Context,namespace: String="account") : TokenVault {
    init {require(namespace.matches(Regex("[a-zA-Z0-9_-]+")))}
    private val file=AtomicFile(File(context.noBackupFilesDir,"crunchyroll-$namespace-session.aes"))
    private val alias="cinema.$namespace.session.v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override suspend fun read(): StoredSession? = withContext(Dispatchers.IO) {
        if(!file.baseFile.exists()) return@withContext null
        val bytes=file.readFully()
        require(bytes.size>17 && bytes[0]==1.toByte())
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)))
        val plain=cipher.doFinal(bytes.copyOfRange(13,bytes.size))
        try {Json.decodeFromString<StoredSession>(plain.decodeToString())} finally {plain.fill(0)}
    }
    override suspend fun write(session: StoredSession) = withContext(Dispatchers.IO) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val plain=Json.encodeToString(session).encodeToByteArray()
        val encrypted=try {cipher.doFinal(plain)} finally {plain.fill(0)}
        val bytes=byteArrayOf(1)+cipher.iv+encrypted
        val output=file.startWrite()
        try {output.write(bytes);file.finishWrite(output)} catch(e: Exception) {file.failWrite(output);throw e}
    }
    override suspend fun clear() = withContext(Dispatchers.IO) {file.delete()}
}
