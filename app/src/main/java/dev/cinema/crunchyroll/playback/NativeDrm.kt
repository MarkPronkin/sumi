@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll.playback

import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.*
import dev.cinema.crunchyroll.data.account.NativeClientProtocol
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.*

/** The Android CDM owns provisioning, licenses and decryption. Only the service's transport envelope is unwrapped. */
class NativeWidevineCallback(stream: NativeStream) : MediaDrmCallback {
    private val delegate=HttpMediaDrmCallback(stream.licenseUrl,true,
        DefaultHttpDataSource.Factory().setUserAgent(NativeClientProtocol.USER_AGENT)).apply {
        setKeyRequestProperty("Authorization","Bearer ${stream.authorization.accessToken}")
        setKeyRequestProperty("X-Cr-Content-Id",stream.contentId)
        setKeyRequestProperty("X-Cr-Video-Token",stream.videoToken)
        setKeyRequestProperty("Content-Type","application/octet-stream")
    }
    override fun executeProvisionRequest(uuid: UUID,request: ExoMediaDrm.ProvisionRequest): MediaDrmCallback.Response=delegate.executeProvisionRequest(uuid,request)
    override fun executeKeyRequest(uuid: UUID,request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response=
        MediaDrmCallback.Response(decodeLicenseEnvelope(delegate.executeKeyRequest(uuid,request).data))

    companion object {
        internal fun decodeLicenseEnvelope(bytes: ByteArray): ByteArray {
            if(bytes.firstOrNull {it.toInt().toChar() !in listOf(' ','\n','\r','\t')}?.toInt()?.toChar()!='{') return bytes
            try {
                val value=Json.parseToJsonElement(bytes.decodeToString()).jsonObject["license"]?.jsonPrimitive?.content
                    ?: throw IOException("License response was missing a platform license.")
                return Base64.getDecoder().decode(value).also {if(it.isEmpty()) throw IOException("Empty platform license response.")}
            } catch(_: Exception) {throw IOException("The license service returned an invalid platform license response.")}
        }
    }
}

/** Requests only a genuine supported CDM level. Never changes decoder/output enforcement flags. */
fun acquireDisplayDrm(uuid: UUID,onLevel: (String)->Unit,onUnavailable: ()->Unit): ExoMediaDrm {
    var drm: FrameworkMediaDrm?=null
    return try {
        val acquired=FrameworkMediaDrm.newInstance(uuid)
        drm=acquired
        acquired.setPropertyString("securityLevel","L3")
        if(acquired.getPropertyString("securityLevel")!="L3") throw IllegalStateException("Software session unavailable")
        onLevel(runCatching {acquired.getPropertyString("securityLevel")}.getOrDefault("unknown"))
        acquired
    } catch(_: Exception) {
        drm?.release();onUnavailable();DummyExoMediaDrm.getInstance()
    }
}
