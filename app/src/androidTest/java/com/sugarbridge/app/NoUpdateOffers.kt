package com.sugarbridge.app

import android.content.Context
import com.sugarbridge.app.libre.HttpResponse
import com.sugarbridge.app.libre.HttpTransport
import com.sugarbridge.app.update.Updater

/**
 * Tests that open the app get a GitHub without releases: a newer version than the build under test would
 * otherwise be offered in a window over their screens. Forgets any version found by an earlier check too.
 */
fun withoutUpdateOffers(context: Context) {
    context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().clear().commit()
    Updater.transportForTests = HttpTransport { _, _, _, _ -> HttpResponse(404, "") }
}

/** The real GitHub again, after [withoutUpdateOffers]. */
fun withUpdateOffers(context: Context) {
    Updater.transportForTests = null
    context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().clear().commit()
}
