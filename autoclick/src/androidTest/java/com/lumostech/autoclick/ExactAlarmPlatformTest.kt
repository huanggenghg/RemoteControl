package com.lumostech.autoclick

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ExactAlarmPlatformTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val platform = AndroidClickAlarmPlatform(context)
    private val event = ClickAlarmOccurrence("platform-epoch", "platform-schedule", System.currentTimeMillis() + 86400000)
    @Test fun explicitImmutableEventIdentityRoundTripsAndRejectsForeignInput() {
        val intent = platform.eventIntent(event)
        assertEquals(ClickAlarmReceiver::class.java.name, intent.component!!.className)
        assertEquals(event, platform.parse(intent))
        assertFalse(intent.filterEquals(platform.eventIntent(event.copy(taskId = "other"))))
        assertFalse(intent.filterEquals(platform.eventIntent(event.copy(scheduledAt = event.scheduledAt + 1))))
        assertNull(platform.parse(Intent(intent).setAction("foreign")))
        assertNull(platform.parse(Intent(intent).setData(Uri.parse("autoclick://other/2/a/b/123"))))
        assertNull(platform.parse(Intent(intent).setData(Uri.parse("autoclick://${context.packageName}/9/a/b/123"))))
    }
    @Test fun cancelDoesNotCreateAndRegistrationRemainsCancelable() {
        platform.cancel(event)
        fun existing() = PendingIntent.getBroadcast(context, 0, platform.eventIntent(event),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertNull(existing())
        assertTrue(platform.canSchedule())
        try { platform.schedule(event); assertNotNull(existing()) }
        finally { platform.cancel(event) }
        assertNull(existing())
    }
}
