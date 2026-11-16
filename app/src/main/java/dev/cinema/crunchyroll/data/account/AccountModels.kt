package dev.cinema.crunchyroll.data.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class OAuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
) { override fun toString() = "OAuthTokens([redacted])" }

@Serializable
class StoredSession(val refreshToken: String, val deviceId: String, val accountId: String, val profileId: String? = null,val protocolVersion: Int = 0) {
    override fun toString() = "StoredSession([redacted])"
}

data class AccountProfile(val id: String, val name: String, val isPrimary: Boolean = false)
data class AccountSummary(val accountId: String, val profileId: String?, val name: String, val profiles: List<AccountProfile>)

sealed interface AccountState {
    data object Restoring : AccountState
    data class SignedOut(val message: String? = null) : AccountState
    data object SigningIn : AccountState
    data class SignedIn(val account: AccountSummary) : AccountState
}

class ServiceException(val status: Int, val reason: String, val requiresLogin: Boolean = false,val providerCode: String?=null) : Exception(reason)

/** Bounded, credential-free diagnostics for authentication regression tests. */
data class AuthenticationEvent(val grant: String,val status: Int,val providerCode: String?=null,val expiresIn: Long?=null)

class PlaybackAuthorization(val accessToken: String,val accountId: String,val identity: Long) {
    override fun toString()="PlaybackAuthorization([redacted])"
}

fun Throwable.accountMessage(): String = when(this) {
    is ServiceException -> reason
    is java.net.UnknownHostException -> "Can’t reach Crunchyroll. Check your internet connection and retry."
    is java.net.SocketTimeoutException -> "Crunchyroll took too long to respond. Please retry."
    is javax.net.ssl.SSLException -> "A secure connection couldn’t be established. Check your device’s date and network."
    else -> "The request couldn’t finish. Please try again."
}
