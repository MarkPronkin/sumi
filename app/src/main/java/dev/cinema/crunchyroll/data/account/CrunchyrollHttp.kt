package dev.cinema.crunchyroll.data.account

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class CrunchyrollHttp(
    val base: HttpUrl = "https://beta-api.crunchyroll.com/".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS)
        .readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val playbackBase: HttpUrl = "https://cr-play-service.prd.crunchyrollsvc.com/".toHttpUrl(),
    private val staticBase: HttpUrl = "https://static.crunchyroll.com/".toHttpUrl(),
) {
    val json=Json {ignoreUnknownKeys=true;coerceInputValues=true}
    internal val authenticationEvents=MutableStateFlow<List<AuthenticationEvent>>(emptyList())

    fun url(path: String,query: Map<String,String> = emptyMap()): HttpUrl = base.newBuilder().addPathSegments(path.removePrefix("/"))
        .apply {query.forEach {(key,value)->addQueryParameter(key,value)}}.build()

    suspend fun token(fields: Map<String,String>,deviceId: String): OAuthTokens {
        val form=FormBody.Builder().apply {
            fields.forEach {(k,v)->add(k,v)}
            add("scope","offline_access");add("device_id",deviceId)
            add("device_type","android");add("device_name","Sumi")
        }.build()
        val request=Request.Builder().url(url("auth/v1/token"))
            .header("Authorization",CLIENT_AUTHORIZATION).header("ETP-Anonymous-ID",deviceId)
            .apply {if(fields["grant_type"]=="password") header("User-Agent",NativeClientProtocol.SIGN_IN_USER_AGENT)}.post(form).build()
        val grant=fields["grant_type"].takeIf {it in setOf("password","refresh_token","refresh_token_profile_id")} ?: "other"
        try {
            val tokens=json.decodeFromJsonElement<OAuthTokens>(execute(request))
            authenticationEvents.update {(it+AuthenticationEvent(grant,200,expiresIn=tokens.expiresIn)).takeLast(10)}
            return tokens
        } catch(e: ServiceException) {
            authenticationEvents.update {(it+AuthenticationEvent(grant,e.status,e.providerCode)).takeLast(10)}
            if(e.requiresLogin) throw ServiceException(e.status,when(grant) {
                "password" -> "Crunchyroll rejected the sign-in request. Check your details and try again."
                "refresh_token_profile_id" -> "The selected profile session was rejected. Please sign in again."
                else -> "Your saved session was rejected. Please sign in again."
            },true,e.providerCode)
            throw e
        }
    }

    suspend fun api(path: String,accessToken: String,query: Map<String,String> = emptyMap(),method: String="GET",body: JsonElement?=null): JsonElement {
        return authorized(url(path,query),accessToken,method,body)
    }
    suspend fun playback(path: String,accessToken: String,method: String="GET",body: JsonElement?=null): JsonElement =
        authorized(playbackBase.newBuilder().addPathSegments(path.removePrefix("/")).build(),accessToken,method,body)
    /** Skip markers are public metadata. Never attach account authorization to CDN requests. */
    suspend fun skipEvents(episodeId: String,legacy: Boolean=false): JsonElement {
        val path=if(legacy) "datalab-intro-v2" else "skip-events/production"
        val url=staticBase.newBuilder().addPathSegments(path).addPathSegment("$episodeId.json").build()
        return execute(Request.Builder().url(url).build(),timeoutSeconds=5)
    }
    suspend fun keepAlive(path: String,accessToken: String,positionMs: Long): JsonElement {
        val seconds=(positionMs.coerceAtLeast(0)/1000).toString()
        val address=playbackBase.newBuilder().addPathSegments(path.removePrefix("/")).addQueryParameter("playhead",seconds).build()
        val form=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("position",seconds).build()
        return execute(Request.Builder().url(address).header("Authorization","Bearer $accessToken").patch(form).build())
    }

    private suspend fun authorized(url: HttpUrl,accessToken: String,method: String,body: JsonElement?): JsonElement {
        val request=Request.Builder().url(url).header("Authorization","Bearer $accessToken")
        if(method!="GET") request.method(method,if(method=="DELETE" && body==null) null else (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()))
        return execute(request.build())
    }

    private suspend fun execute(raw: Request,timeoutSeconds: Long?=null): JsonElement {
        val request=raw.newBuilder().header("Accept","application/json").header("User-Agent",raw.header("User-Agent") ?: NativeClientProtocol.USER_AGENT).build()
        val call=client.newCall(request)
        timeoutSeconds?.let {call.timeout().timeout(it,TimeUnit.SECONDS)}
        return suspendCancellableCoroutine<JsonElement> {continuation ->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object: Callback {
                override fun onFailure(call: Call,e: IOException) {if(continuation.isActive) continuation.resumeWithException(e)}
                override fun onResponse(call: Call,response: Response) {
                    // Read and parse on OkHttp's dispatcher; cancellation remains linked until the body is consumed.
                    val result=runCatching {
                        response.use {
                            val text=it.body.string()
                            if(!it.isSuccessful) throw serviceError(it.code,text,it.header("cf-mitigated")=="challenge",raw.url.encodedPath.contains("/auth/"))
                            if(text.isBlank()) JsonObject(emptyMap()) else try {json.parseToJsonElement(text)}
                            catch(_: Exception) {throw ServiceException(it.code,"Crunchyroll returned an unexpected response. Please retry.")}
                        }
                    }
                    if(continuation.isActive) result.fold({continuation.resume(it) {_,_,_->}},{continuation.resumeWithException(it)})
                }
            })
        }
    }

    companion object {
        // Public OAuth client authorization used by the reference project, not a user credential.
        // Protocol identifier only; no upstream application code is copied.
        internal const val CLIENT_AUTHORIZATION=NativeClientProtocol.AUTHORIZATION
        internal fun serviceError(status: Int,body: String,challenge: Boolean=false,auth: Boolean=false): ServiceException {
            val data=runCatching {Json.parseToJsonElement(body).jsonObject}.getOrNull()
            val error=(data?.get("error") as? JsonPrimitive)?.contentOrNull
            val code=(data?.get("code") as? JsonPrimitive)?.contentOrNull?.takeIf {it.startsWith("auth.") && it.length<100 && it.matches(Regex("[A-Za-z0-9_.-]+"))}
            return when {
                error=="3002" -> ServiceException(status,"Too many active streams. Stop playback on another device and retry.")
                error=="40016" -> ServiceException(status,"Crunchyroll rejected this playback client. Sign in again, or install an updated build.")
                challenge || (status==403 && body.trimStart().startsWith("<")) -> ServiceException(status,"Crunchyroll requires a browser security check for this connection. Try another network or sign in on crunchyroll.com, then retry here.")
                auth && status in listOf(400,401) && error=="invalid_grant" -> ServiceException(status,"Your sign-in details or session were rejected. Check your email and password and try again.",true,code)
                auth && error=="invalid_client" -> ServiceException(status,"Crunchyroll no longer accepts this client. An app update is needed.")
                status==401 -> ServiceException(status,"Your session has expired. Please sign in again.",true)
                status==403 -> ServiceException(status,"Crunchyroll denied this request. Check your account’s access and region, then retry.")
                status==429 -> ServiceException(status,"Too many requests. Wait a moment before trying again.")
                status>=500 -> ServiceException(status,"Crunchyroll is temporarily unavailable. Please retry shortly.")
                else -> ServiceException(status,if(auth) "Sign-in couldn’t finish (HTTP $status). Check your details and retry." else "This request couldn’t finish (HTTP $status). Please retry.")
            }
        }
    }
}

internal fun JsonElement.objectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
