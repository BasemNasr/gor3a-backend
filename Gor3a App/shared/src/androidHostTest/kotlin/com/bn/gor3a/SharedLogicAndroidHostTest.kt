package com.bn.gor3a

import com.bn.gor3a.data.remote.Gor3aApiClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class SharedLogicAndroidHostTest {

    @Test
    fun testPlatformHttpClientReachesBackend() = runBlocking {
        val client = Gor3aApiClient()
        val health = client.checkHealth()
        println("Health check response: status=${health.status}, service=${health.service}")
        assertTrue(health.status == "healthy")

        val search = client.searchDrugs(query = "panadol", pageSize = 5)
        println("Search response: count=${search.results.size}, total=${search.total}")
        assertTrue(search.results.isNotEmpty())
    }
}