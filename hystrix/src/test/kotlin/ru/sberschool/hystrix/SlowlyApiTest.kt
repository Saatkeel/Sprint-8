package ru.sberschool.hystrix

import feign.Request
import feign.httpclient.ApacheHttpClient
import feign.hystrix.HystrixFeign
import feign.jackson.JacksonDecoder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.client.server.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SlowlyApiTest {
    val client = HystrixFeign.builder()
        .client(ApacheHttpClient())
        .decoder(JacksonDecoder())
        // для удобства тестирования задаем таймауты на 1 секунду
        .options(Request.Options(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS, true))
        .target(SlowlyApi::class.java, "http://127.0.0.1:18080", FallbackSlowlyApi())

    val clientPokeapi = HystrixFeign.builder()
        .client(ApacheHttpClient())
        .decoder(JacksonDecoder())
        .options(Request.Options(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS, true))
        .target(SlowlyApi::class.java, "https://pokeapi.co/api/v2/", FallbackSlowlyApi())

    lateinit var mockServer: ClientAndServer

    @BeforeEach
    fun setup() {
        // запускаем мок сервер для тестирования клиента
        mockServer = ClientAndServer.startClientAndServer(18080)
    }

    @AfterEach
    fun shutdown() {
        mockServer.stop()
    }

    @Test
    fun `getSomething() should return fallback value`() {
        // given
        MockServerClient("127.0.0.1", 18080)
            .`when`(
                // задаем матчер для нашего запроса
                HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/")
            )
            .respond(
                // наш запрос попадает на таймаут
                HttpResponse.response()
                    .withStatusCode(400)
                    .withDelay(TimeUnit.SECONDS, 30) //
            )

        // expect
        assertEquals("fallback", client.getPokemon().pokemonName)
    }

    @Test
    fun `getSomething() should return right pokemon name`() {
        // given
        MockServerClient("127.0.0.1", 18080)
            .`when`(
                // задаем матчер для нашего запроса
                HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/pokemon/slowpoke")
            )
            .respond(
                // наш запрос попадает на таймаут
                HttpResponse.response()
                    .withStatusCode(200)
                    .withBody(
                        "{\n" +
                                "  \"name\": \"slowpoke\",\n" +
                                "  \"count\": 7\n" +
                                "}"
                    )
            )

        // expect
        assertEquals("slowpoke", client.getPokemon().pokemonName)
    }

    @Test
    fun `should return wrong pokemon name using api`() {
        assertNotEquals("pikachu", clientPokeapi.getPokemon().pokemonName)
    }

    @Test
    fun `should return right pokemon name using api`() {
        assertEquals("slowpoke", clientPokeapi.getPokemon().pokemonName)
    }
}
