package id.waspadai.app.core.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PendingTriggerStoreTest {
    @Test
    fun `take returns a trigger only once`() {
        val store = PendingTriggerStore()
        val trigger = PendingVerificationTrigger(
            contexts = listOf(CapturedContext.Text("teks mencurigakan", source = TriggerSource.SHARE_SHEET))
        )

        store.put(trigger)

        assertSame(trigger, store.take())
        assertNull(store.take())
    }

    @Test
    fun `clear wipes pending image bytes`() {
        val bytes = byteArrayOf(1, 2, 3)
        val store = PendingTriggerStore()
        store.put(
            PendingVerificationTrigger(
                contexts = listOf(
                    CapturedContext.Image(bytes, "image/png", "capture.png", TriggerSource.QUICK_SETTINGS)
                )
            )
        )

        store.clear()

        assertEquals(listOf<Byte>(0, 0, 0), bytes.toList())
    }
}
