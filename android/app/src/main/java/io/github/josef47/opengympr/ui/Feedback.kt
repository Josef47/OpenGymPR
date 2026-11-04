package io.github.josef47.opengympr.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.josef47.opengympr.BuildConfig

/*
 * Feedback without the INTERNET permission: the app only hands a draft to the user's own mail app
 * (or browser for GitHub). The user reads it and presses send themselves; the app never sends anything.
 */

const val FEEDBACK_EMAIL = "info@m56software.com"
const val ISSUES_URL = "https://github.com/Josef47/OpenGymPR/issues/new"

/** Opens a mail draft. Returns false when there is no mail app (caller shows the address instead). */
fun sendFeedbackMail(ctx: Context): Boolean {
    val body = "\n\n\n---\nOpenGymPR ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
        "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "OpenGymPR feedback (v${BuildConfig.VERSION_NAME})")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try { ctx.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
}

fun openIssues(ctx: Context): Boolean =
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL))); true } catch (_: ActivityNotFoundException) { false }

/** The one-time "Do you have feedback?" card shown on the Log screen. */
@Composable
fun FeedbackPrompt(days: Int, onSend: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Do you have feedback?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("You've trained $days day${if (days == 1) "" else "s"} with OpenGymPR. What's missing, what's annoying? Your mail app opens with a draft — you decide what to send.",
                color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.primary)) { Text("Send feedback", fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onLater) { Text("Later", color = c.onSurfaceVariant) }
                TextButton(onClick = onNever) { Text("No thanks", color = c.onSurfaceVariant) }
            }
        }
    }
}
