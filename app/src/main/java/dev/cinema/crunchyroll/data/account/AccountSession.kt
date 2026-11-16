package dev.cinema.crunchyroll.data.account

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Serializes refresh/profile/logout with login. Passwords never enter persistent state. */
class AccountSession(private val http: CrunchyrollHttp, private val vault: TokenVault,private val deviceIdentity: DeviceIdentity=MemoryDeviceIdentity(),private val now: ()->Long = System::currentTimeMillis) {
    internal val authenticationEvents get()=http.authenticationEvents
    val state=MutableStateFlow<AccountState>(AccountState.Restoring)
    private val lock=Mutex()
    private var tokens: OAuthTokens?=null
    private var stored: StoredSession?=null
    private var expiresAt=0L
    private var generation=0L
    private var identityEpoch=0L
    private data class AccessLease(val token: String,val version: Long,val identity: Long)

    suspend fun restore() = lock.withLock {
        try {
            val saved=vault.read()
            if(saved==null) {state.value=AccountState.SignedOut();return@withLock}
            stored=saved
            refreshLocked()
            state.value=AccountState.SignedIn(summaryLocked())
        } catch(e: CancellationException) {state.value=AccountState.SignedOut();throw e
        } catch(e: Exception) {
            val needsUpgrade=stored?.protocolVersion==0 && e is ServiceException && e.requiresLogin
            if(e is ServiceException && e.requiresLogin) {vault.clear();stored=null}
            tokens=null
            state.value=AccountState.SignedOut(if(needsUpgrade) "Please sign in again to enable native playback in this update." else if(stored!=null) "Couldn’t restore your account. Retry the connection or sign in again." else e.accountMessage())
        }
    }

    suspend fun login(email: String,password: String) = lock.withLock {
        state.value=AccountState.SigningIn
        try {
            // A fresh password login must never inherit another account's retained profile or refresh token.
            tokens=null;stored=null;expiresAt=0;generation++;identityEpoch++;vault.clear()
            val deviceId=deviceIdentity.getOrCreate()
            val response=http.token(mapOf("grant_type" to "password","username" to email.trim(),"password" to password),deviceId)
            acceptLocked(response,deviceId)
            val summary=summaryLocked()
            state.value=AccountState.SignedIn(summary)
        } catch(e: CancellationException) {
            tokens=null;stored=null;withContext(NonCancellable) {vault.clear()};state.value=AccountState.SignedOut();throw e
        } catch(e: Exception) {
            // A valid token remains usable if optional profile discovery fails; summaryLocked handles that.
            tokens=null;stored=null;vault.clear();state.value=AccountState.SignedOut(e.accountMessage())
        }
    }

    private suspend fun acceptLocked(response: OAuthTokens,deviceId: String,allowProfileChange: Boolean=false) {
        val old=stored
        val refresh=response.refreshToken ?: old?.refreshToken ?: throw ServiceException(200,"Crunchyroll didn’t return a renewable account session. Please retry.")
        val account=response.accountId ?: old?.accountId ?: throw ServiceException(200,"Crunchyroll didn’t return an account. Please retry.")
        if(old!=null && (old.accountId!=account || (!allowProfileChange && old.profileId!=null && response.profileId!=null && old.profileId!=response.profileId)))
            throw ServiceException(401,"The saved account session changed. Please sign in again.",true)
        require(response.accessToken.isNotBlank() && refresh.isNotBlank() && account.isNotBlank())
        val next=StoredSession(refresh,deviceId,account,response.profileId ?: old?.profileId,1)
        vault.write(next) // Never show signed-in success before secure persistence completes.
        stored=next;tokens=response;expiresAt=now()+response.expiresIn.coerceAtLeast(0)*1000;generation++
    }

    private suspend fun refreshLocked() {
        val saved=stored ?: throw ServiceException(401,"Please sign in to Crunchyroll.",true)
        val fields=buildMap {
            put("refresh_token",saved.refreshToken)
            put("grant_type",if(saved.profileId!=null) "refresh_token_profile_id" else "refresh_token")
            saved.profileId?.let {put("profile_id",it)}
        }
        acceptLocked(http.token(fields,saved.deviceId),saved.deviceId)
    }

    private suspend fun access(): AccessLease = lock.withLock {
        if(tokens==null || now()>=expiresAt-60_000) refreshForRequestLocked()
        AccessLease(tokens!!.accessToken,generation,identityEpoch)
    }

    private suspend fun refreshForRequestLocked() {
        try {refreshLocked()} catch(failure: ServiceException) {
            if(failure.requiresLogin) {
                tokens=null;stored=null;expiresAt=0;generation++
                state.value=AccountState.SignedOut(failure.reason)
                withContext(NonCancellable) {vault.clear()}
            }
            throw failure
        }
    }

    suspend fun request(path: String,query: Map<String,String> = emptyMap(),method: String="GET",body: JsonElement?=null,expectedIdentity: Long?=null): JsonElement {
        return authorizedRequest(expectedIdentity) {http.api(path,it,query,method,body)}
    }
    suspend fun playbackRequest(path: String,method: String="GET",body: JsonElement?=null,expectedIdentity: Long?=null): JsonElement =
        authorizedRequest(expectedIdentity) {http.playback(path,it,method,body)}
    suspend fun releasePlayback(path: String,authorization: PlaybackAuthorization) {http.playback(path,authorization.accessToken,"DELETE")}
    suspend fun renewPlayback(path: String,positionMs: Long,identity: Long) =
        authorizedRequest(identity) {http.keepAlive(path,it,positionMs)}

    suspend fun playbackAuthorization(): PlaybackAuthorization = lock.withLock {
        if(tokens==null || now()>=expiresAt-60_000) refreshForRequestLocked()
        val saved=stored ?: throw ServiceException(401,"Please sign in again.",true)
        PlaybackAuthorization(tokens!!.accessToken,saved.accountId,identityEpoch)
    }

    private suspend fun authorizedRequest(expectedIdentity: Long?=null,operation: suspend (String)->JsonElement): JsonElement {
        val access=access()
        if(expectedIdentity!=null && expectedIdentity!=access.identity) throw ServiceException(409,"The active account changed. Open the episode again.")
        try {return operation(access.token)} catch(e: ServiceException) {
            if(e.status!=401) throw e
            // Concurrent 401s share a refresh; a logout/profile switch can never revive an old request.
            val refreshed=lock.withLock {
                if(stored==null) throw ServiceException(401,"Please sign in again.",true)
                if(identityEpoch!=access.identity) throw ServiceException(409,"Your active account or profile changed. Please retry this action.")
                if(generation==access.version) {
                    refreshForRequestLocked()
                }
                tokens!!.accessToken
            }
            return operation(refreshed)
        }
    }

    suspend fun switchProfile(id: String) = lock.withLock {
        val saved=stored ?: throw ServiceException(401,"Please sign in again.",true)
        val summary=(state.value as? AccountState.SignedIn)?.account ?: return@withLock
        require(summary.profiles.any {it.id==id})
        val response=http.token(mapOf("grant_type" to "refresh_token_profile_id","refresh_token" to saved.refreshToken,"profile_id" to id),saved.deviceId)
        acceptLocked(response,saved.deviceId,allowProfileChange=true)
        // Some clients omit profile_id from the response. Retain the profile explicitly selected.
        stored=StoredSession(stored!!.refreshToken,saved.deviceId,saved.accountId,id,1).also {vault.write(it)}
        identityEpoch++
        state.value=AccountState.SignedIn(summary.copy(profileId=id,name=summary.profiles.first {it.id==id}.name))
    }

    suspend fun logout() = lock.withLock {
        vault.clear();tokens=null;stored=null;expiresAt=0;generation++;identityEpoch++
        state.value=AccountState.SignedOut()
    }

    private suspend fun summaryLocked(): AccountSummary {
        val current=stored!!
        var name="Your account"
        var profileId=current.profileId
        val profiles=mutableListOf<AccountProfile>()
        try {
            val data=http.api("accounts/v1/me/profile",tokens!!.accessToken).objectOrEmpty()
            name=data.text("profile_name").ifBlank {data.text("username")}.ifBlank {name}
            profileId=data.text("profile_id").takeIf {it.isNotBlank()} ?: profileId
        } catch(e: CancellationException) {throw e} catch(_: Exception) { /* Account auth remains valid if profile metadata is unavailable. */ }
        try {
            val data=http.api("accounts/v1/me/multiprofile",tokens!!.accessToken).objectOrEmpty()
            profiles+=data.array("profiles").mapNotNull { element ->
                val p=element.objectOrEmpty()
                val id=p.text("profile_id").ifBlank {p.text("id")}
                if(id.isBlank()) null else AccountProfile(id,p.text("profile_name").ifBlank {p.text("name")}.ifBlank {"Profile"},(p["is_primary"] as? JsonPrimitive)?.booleanOrNull ?: false)
            }
        } catch(e: CancellationException) {throw e} catch(_: Exception) { /* Do not invent extra service profiles. */ }
        if(profileId!=null && current.profileId!=profileId) {
            stored=StoredSession(current.refreshToken,current.deviceId,current.accountId,profileId,1).also {vault.write(it)}
        }
        return AccountSummary(current.accountId,profileId,name,profiles)
    }
}
