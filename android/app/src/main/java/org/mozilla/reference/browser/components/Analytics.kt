/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.components

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.lib.crash.Crash
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.lib.crash.service.CrashReporterService
import org.mozilla.reference.browser.BrowserApplication
import org.mozilla.reference.browser.BuildConfig
import org.mozilla.reference.browser.R

/** Component group for all functionality related to analytics e.g. crash reporting and telemetry. */
class Analytics(private val context: Context) {
    /** The upstream component requires a service even while disabled. This service never sends data. */
    val crashReporter: CrashReporter by lazy {
        val services = listOf(object : CrashReporterService {
            override val id = "anibrowser-disabled"
            override val name = "Reporting disabled"
            override fun createCrashReportUrl(identifier: String): String? = null
            override fun report(crash: Crash.UncaughtExceptionCrash): String? = null
            override fun report(crash: Crash.NativeCodeCrash): String? = null
            override fun report(throwable: Throwable, breadcrumbs: ArrayList<Breadcrumb>): String? = null
        })

        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        CrashReporter(
            context = context,
            services = services,
            telemetryServices = emptyList(),
            shouldPrompt = CrashReporter.Prompt.ALWAYS,
            promptConfiguration =
                CrashReporter.PromptConfiguration(
                    appName = context.getString(R.string.app_name),
                    organizationName = "AniBrowser",
                ),
            nonFatalCrashIntent =
                PendingIntent.getBroadcast(context, 0, Intent(BrowserApplication.NON_FATAL_CRASH_BROADCAST), flags),
            enabled = false,
        )
    }
}

fun isSentryEnabled() = !BuildConfig.SENTRY_TOKEN.isNullOrEmpty()
