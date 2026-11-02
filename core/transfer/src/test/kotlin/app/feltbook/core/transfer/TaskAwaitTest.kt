package app.feltbook.core.transfer

import android.os.Looper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Задачи ConnectionsClient → [TransportException]; отмена — тоже исключение транспорта, не CancellationException. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskAwaitTest {
    private var lastResult: Result<Unit>? = null

    private fun awaitResult(task: Task<Void>): Result<Unit> {
        runTest(UnconfinedTestDispatcher()) {
            val result = async { runCatching { task.awaitNearby() } }
            shadowOf(Looper.getMainLooper()).idle()
            lastResult = result.await()
        }
        return lastResult!!
    }

    @Test
    fun `успех — Unit`() {
        assertTrue(awaitResult(Tasks.forResult(null)).isSuccess)
    }

    @Test
    fun `отмена — TransportException CANCELED`() {
        val error = awaitResult(Tasks.forCanceled()).exceptionOrNull()
        assertTrue(error is TransportException)
        assertEquals(CommonStatusCodes.CANCELED, (error as TransportException).statusCode)
    }

    @Test
    fun `ошибка API — её код, «уже рекламируем» — успех`() {
        val radio = ConnectionsStatusCodes.STATUS_RADIO_ERROR
        val failed = awaitResult(Tasks.forException(ApiException(Status(radio))))
        assertEquals(radio, (failed.exceptionOrNull() as TransportException).statusCode)
        val already = Tasks.forException<Void>(ApiException(Status(ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING)))
        assertTrue(awaitResult(already).isSuccess)
    }
}
