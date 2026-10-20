package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ServerUI", appName)
  }

  @Test
  fun `test load font resource`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val value = android.util.TypedValue()
    context.resources.getValue(R.font.inter, value, true)
    println("Font resource string: ${value.string}, type: ${value.type}")
    try {
      val tf = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter)
      println("Typeface: $tf")
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  @Test
  fun `local server store persist and retrieve`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.data.local.LocalServerStore(context)
    val server = store.saveServer(
      com.example.data.model.ServerInput(
        name = "test-ec2",
        host = "35.154.77.95",
        port = 22,
        username = "ubuntu",
        authType = "password"
      )
    )
    val retrieved = store.getServer(server.id)
    org.junit.Assert.assertNotNull(retrieved)
    assertEquals("35.154.77.95", retrieved?.host)
    store.deleteServer(server.id)
  }

  @Test
  fun `repository is direct standalone on android`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.repository.ServerUIRepository(context)
    org.junit.Assert.assertTrue("Repository must always be in direct standalone mode", repo.isDirectMode())
  }
}
