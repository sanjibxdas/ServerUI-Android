package com.example

import com.example.data.model.FileEntry
import com.example.data.model.ServerPublic
import com.example.ui.components.formatBytes
import com.example.ui.components.formatRate
import com.example.ui.components.formatUptime
import com.example.ui.screens.cleanAnsi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("2.5 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testFormatRate() {
        assertEquals("1.0 MB/s", formatRate(1024 * 1024))
    }

    @Test
    fun testFormatUptime() {
        assertEquals("1h 0m", formatUptime(3600))
        assertEquals("1d 2h 3m", formatUptime(86400 + 7200 + 180))
    }

    @Test
    fun testCleanAnsi() {
        val raw = "\u001B[31mError message\u001B[0m"
        val cleaned = cleanAnsi(raw)
        assertEquals("Error message", cleaned)
    }

    @Test
    fun testServerModelSerialization() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(ServerPublic::class.java)

        val json = """
            {
                "id": "srv_123",
                "name": "Production Server",
                "host": "192.168.1.100",
                "port": 22,
                "username": "ubuntu",
                "authType": "password",
                "status": "online",
                "connected": true
            }
        """.trimIndent()

        val server = adapter.fromJson(json)
        org.junit.Assert.assertNotNull(server)
        assertEquals("srv_123", server?.id)
        assertEquals("Production Server", server?.name)
        assertEquals("192.168.1.100", server?.host)
        assertEquals(22, server?.port)
        assertEquals("ubuntu", server?.username)
        assertEquals("online", server?.status)
        assertTrue(server?.connected == true)
    }

    @Test
    fun testFileEntrySerialization() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(FileEntry::class.java)

        val json = """
            {
                "name": "docker-compose.yml",
                "path": "/opt/app/docker-compose.yml",
                "type": "file",
                "size": 2048,
                "mode": "-rw-r--r--",
                "modified": "2026-10-17T12:00:00Z",
                "mime": "text/yaml"
            }
        """.trimIndent()

        val file = adapter.fromJson(json)
        org.junit.Assert.assertNotNull(file)
        assertEquals("docker-compose.yml", file?.name)
        assertEquals("/opt/app/docker-compose.yml", file?.path)
        assertEquals("file", file?.type)
        assertEquals(2048L, file?.size)
    }
}
