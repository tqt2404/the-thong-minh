package data.api

import data.repository.CardRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object RetrofitInstance {
    private const val BASE_URL = "http://localhost:8080"

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    val cardApi: CardApi = CardApiImpl(client, BASE_URL)
    val cardRepository: CardRepository = CardRepository(cardApi)
}