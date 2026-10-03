package org.webrtsp.monitor.restreamer

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.webrtsp.monitor.IoDispatcher
import javax.inject.Inject

@Serializable
private data class CredentialsRequest(
    @SerialName("client_id") val clientId: String,
)

@Serializable
private data class CredentialsResponse(
    @SerialName("agent_id") val agentId: String,
    @SerialName("access_token") val accessToken: String
) {
    fun toCredentials(): Credentials {
        return Credentials(agentId, accessToken)
    }
}

class CredentialsDataSource @Inject constructor(
    private val _httpClient: HttpClient,
    @param:IoDispatcher private val _dispatcher: CoroutineDispatcher
) {
    companion object {
        const val TAG = "CredentialsDataSource"
    }

    suspend fun registerClient(clientId: String): Credentials? {
        return withContext(_dispatcher) {
            try {
                val httpResponse = _httpClient.post("credentials") {
                    setBody(CredentialsRequest(clientId))
                }

                return@withContext httpResponse.body<CredentialsResponse>().toCredentials()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch credentials: ${e.message}")
            }

            return@withContext null
        }
    }
}
