package com.sasayaki.domain.provider

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.api.InvalidServerAddressException
import com.sasayaki.data.api.ServerAddressProblem
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ConnectionErrorsTest {
    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    private fun describe(error: Throwable) = describeConnectionError(error, resources)

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody()))

    @Test
    fun `network failures name the likely cause`() {
        assertEquals("Can't find that server. Check the address.", describe(UnknownHostException("x")))
        assertEquals("The server took too long to answer.", describe(SocketTimeoutException()))
    }

    @Test
    fun `auth failures point at the key`() {
        assertEquals("The server rejected the API key.", describe(http(401)))
        assertEquals("The server rejected the API key.", describe(http(403)))
    }

    @Test
    fun `other http errors keep the status code`() {
        assertEquals("The server had an error (502).", describe(http(502)))
        assertEquals("The server answered with error 418.", describe(http(418)))
    }

    @Test
    fun `invalid addresses say what is wrong with them`() {
        assertEquals(
            "Use an https:// address. Plain http:// only works for servers on your local network.",
            describe(InvalidServerAddressException(ServerAddressProblem.CLEARTEXT_OUTSIDE_LOCAL_NETWORK))
        )
        assertEquals("That address is not valid.", describe(IllegalArgumentException("unexpected port")))
    }
}
