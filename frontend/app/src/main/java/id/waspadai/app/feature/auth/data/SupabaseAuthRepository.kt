package id.waspadai.app.feature.auth.data

import id.waspadai.app.feature.verification.data.AccessTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SupabaseAuthRepository(
    private val client: HttpClient,
    private val supabaseUrl: String,
    private val publishableKey: String,
    initialAccessToken: String = "",
    private val sessionStore: AuthSessionStore = NoOpAuthSessionStore,
) : AccessTokenProvider {
    private val restoredSession = sessionStore.load()
    private var accessToken: String = restoredSession?.accessToken ?: initialAccessToken.trim()
    private var refreshToken: String = restoredSession?.refreshToken.orEmpty()
    private val refreshMutex = Mutex()

    override suspend fun currentAccessToken(): String? {
        if (accessToken.isBlank()) return null
        if (!accessToken.expiresSoon()) return accessToken
        return runCatching { refreshAccessToken() }.getOrNull() ?: accessToken
    }

    fun hasSession(): Boolean = accessToken.isNotBlank() || refreshToken.isNotBlank()

    override suspend fun refreshAccessToken(): String? = refreshMutex.withLock {
        val token = refreshToken.takeIf(String::isNotBlank)
            ?: return@withLock accessToken.takeIf(String::isNotBlank)
        val session = requestSession(
            path = "token?grant_type=refresh_token",
            body = RefreshRequestDto(refreshToken = token),
        )
        saveSession(session)
        accessToken.takeIf(String::isNotBlank)
    }

    suspend fun signIn(email: String, password: String) {
        val session = requestSession(
            path = "token?grant_type=password",
            body = EmailPasswordRequestDto(email = email.trim(), password = password),
        )
        saveSession(session)
    }

    suspend fun signUp(email: String, password: String, fullName: String) {
        val normalizedFullName = fullName.trim().replace(Regex("\\s+"), " ")
        if (normalizedFullName.length !in 2..80) {
            throw SupabaseAuthException("Nama lengkap harus terdiri dari 2 sampai 80 karakter.")
        }
        val session = requestSession(
            path = "signup",
            body = SignUpRequestDto(
                email = email.trim(),
                password = password,
                data = UserMetadataDto(fullName = normalizedFullName),
            ),
        )
        saveSession(session)
    }

    suspend fun requestPasswordReset(email: String) {
        ensureConfigured()
        val response = client.post("${supabaseUrl.trimEnd('/')}/auth/v1/recover") {
            authHeaders(useSession = false)
            setBody(RecoveryEmailRequestDto(email = email.trim()))
        }
        if (!response.status.isSuccess()) {
            throw SupabaseAuthException(response.safeMessage())
        }
    }

    suspend fun verifyPasswordResetCode(email: String, code: String) {
        val session = requestSession(
            path = "verify",
            body = RecoveryVerifyRequestDto(
                email = email.trim(),
                token = code.trim(),
                type = "recovery",
            ),
        )
        saveSession(session)
    }

    suspend fun updatePassword(newPassword: String) {
        ensureConfigured()
        val recoveryAccessToken = accessToken.takeIf(String::isNotBlank)
            ?: throw SupabaseAuthException("Kode pemulihan belum diverifikasi.")
        val response = client.put("${supabaseUrl.trimEnd('/')}/auth/v1/user") {
            authHeaders(useSession = true, sessionToken = recoveryAccessToken)
            setBody(UpdatePasswordRequestDto(password = newPassword))
        }
        if (!response.status.isSuccess()) {
            throw SupabaseAuthException(response.safeMessage())
        }
    }

    suspend fun signOut() {
        val sessionToken = accessToken
        try {
            if (sessionToken.isNotBlank() && supabaseUrl.isNotBlank() && publishableKey.isNotBlank()) {
                client.post("${supabaseUrl.trimEnd('/')}/auth/v1/logout") {
                    authHeaders(useSession = true, sessionToken = sessionToken)
                }
            }
        } finally {
            clearSession()
        }
    }

    fun clearSession() {
        accessToken = ""
        refreshToken = ""
        sessionStore.clear()
    }

    private suspend inline fun <reified T> requestSession(path: String, body: T): SupabaseSessionDto {
        ensureConfigured()
        val response = client.post("${supabaseUrl.trimEnd('/')}/auth/v1/$path") {
            authHeaders(useSession = false)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw SupabaseAuthException(response.safeMessage())
        }
        return response.body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(
        useSession: Boolean,
        sessionToken: String = accessToken,
    ) {
        headers {
            append("apikey", publishableKey)
            append(
                HttpHeaders.Authorization,
                "Bearer ${if (useSession) sessionToken else publishableKey}",
            )
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        accept(ContentType.Application.Json)
    }

    private fun saveSession(session: SupabaseSessionDto) {
        accessToken = session.accessToken.orEmpty()
        refreshToken = session.refreshToken.orEmpty()
        if (accessToken.isBlank()) {
            throw SupabaseAuthException(
                "Login berhasil dibuat, tetapi Supabase belum mengembalikan sesi. Periksa email konfirmasi lalu masuk kembali."
            )
        }
        sessionStore.save(StoredAuthSession(accessToken, refreshToken))
    }

    private fun ensureConfigured() {
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) {
            throw SupabaseAuthException(
                "Konfigurasi Supabase Android belum tersedia. Isi SUPABASE_URL dan SUPABASE_PUBLISHABLE_KEY."
            )
        }
    }

    private suspend fun io.ktor.client.statement.HttpResponse.safeMessage(): String {
        val raw = runCatching { bodyAsText() }.getOrNull().orEmpty()
        return when {
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                "Email atau kata sandi tidak sesuai."
            raw.contains("User already registered", ignoreCase = true) ->
                "Email sudah terdaftar. Gunakan mode Masuk."
            raw.contains("Email not confirmed", ignoreCase = true) ->
                "Email belum dikonfirmasi. Periksa kotak masuk lalu coba lagi."
            raw.contains("expired", ignoreCase = true) || raw.contains("invalid", ignoreCase = true) ->
                "Kode verifikasi salah atau sudah kedaluwarsa."
            raw.contains("same password", ignoreCase = true) ->
                "Gunakan kata sandi baru yang berbeda dari sebelumnya."
            else -> "Autentikasi Supabase belum berhasil. Coba lagi."
        }
    }
}

class SupabaseAuthException(message: String) : RuntimeException(message)

private fun String.expiresSoon(): Boolean = runCatching {
    val payload = split('.').getOrNull(1) ?: return@runCatching false
    val decoded = String(Base64.getUrlDecoder().decode(payload))
    val expiresAtSeconds = Json.parseToJsonElement(decoded)
        .jsonObject["exp"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
    expiresAtSeconds <= System.currentTimeMillis() / 1000L + 60L
}.getOrDefault(false)

@Serializable
private data class EmailPasswordRequestDto(
    val email: String,
    val password: String,
)

@Serializable
private data class SignUpRequestDto(
    val email: String,
    val password: String,
    val data: UserMetadataDto,
)

@Serializable
private data class UserMetadataDto(
    @SerialName("full_name") val fullName: String,
)

@Serializable
private data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
private data class RecoveryEmailRequestDto(
    val email: String,
)

@Serializable
private data class RecoveryVerifyRequestDto(
    val email: String,
    val token: String,
    val type: String,
)

@Serializable
private data class UpdatePasswordRequestDto(
    val password: String,
)

@Serializable
private data class SupabaseSessionDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)
