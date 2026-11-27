package com.intelliverse.croman.ui.contacts

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.intelliverse.croman.R
import com.intelliverse.croman.domain.Contact
import com.intelliverse.croman.domain.PhoneIssue
import com.intelliverse.croman.domain.PhoneLinks
import com.intelliverse.croman.domain.PhoneNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ChannelAction(
    val id: String,
    val description: Int,
    val icon: ImageVector? = null,
    val drawable: Int? = null,
    val url: String?,
    val hint: Int?,
)

@Composable
internal fun LinkedContactActions(
    contactId: String,
    country: String,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
) {
    val container = com.intelliverse.croman.di.LocalAppContainer.current
    val contact by container.store.observeContact(contactId).collectAsStateWithLifecycle(initialValue = null)
    val person = contact ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    if (person.email.isNotBlank()) {
        androidx.compose.material3.Text(person.email, style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
    }
    if (person.telegram.isNotBlank()) {
        androidx.compose.material3.Text(person.telegram, style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
    }
    ContactActionRow(person, country, "", scope, snackbar, context) { }
}

internal fun channelActions(contact: Contact, country: String, draft: String): List<ChannelAction> {
    val phone = PhoneLinks.normalize(contact.phone, country)
    val whatsApp = PhoneLinks.whatsAppUrl(contact.phone, draft, country)
    val chat = PhoneLinks.telegramChatUrl(contact.telegram)
    val mail = contact.email.trim().takeIf { it.contains('@') }?.let { address ->
        val body = Uri.encode(draft)
        if (body.isNullOrBlank()) "mailto:$address" else "mailto:$address?body=$body"
    }
    val sms = PhoneLinks.dialUrl(contact.phone)?.removePrefix("tel:")?.let { number ->
        val body = Uri.encode(draft)
        if (body.isNullOrBlank()) "sms:$number" else "sms:$number?body=$body"
    }
    return listOf(
        ChannelAction("call", R.string.call, icon = Icons.Filled.Call, url = PhoneLinks.dialUrl(contact.phone), hint = R.string.phone_missing),
        ChannelAction("sms", R.string.cd_sms, icon = Icons.Filled.Sms, url = sms, hint = R.string.phone_missing),
        ChannelAction(
            "whatsapp",
            R.string.whatsapp,
            drawable = R.drawable.ic_whatsapp,
            url = whatsApp,
            hint = if (phone is PhoneNumber.Rejected && phone.issue == PhoneIssue.AMBIGUOUS) R.string.phone_ambiguous else R.string.phone_missing,
        ),
        ChannelAction("telegram", R.string.telegram_open, icon = Icons.AutoMirrored.Filled.Send, url = chat, hint = R.string.telegram_need_username),
        ChannelAction("email", R.string.cd_email, icon = Icons.Filled.Email, url = mail, hint = R.string.email_missing),
    )
}

@Composable
internal fun ContactActionRow(
    contact: Contact,
    country: String,
    draft: String,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    context: android.content.Context,
    onOpened: suspend (ChannelAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        channelActions(contact, country, draft).forEach { action ->
            val description = stringResource(action.description)
            IconButton(onClick = {
                val url = action.url
                if (url == null) {
                    val hint = action.hint ?: return@IconButton
                    scope.launch { snackbar.showSnackbar(context.getString(hint)) }
                } else {
                    openMessenger(context, url, scope, snackbar) { onOpened(action) }
                }
            }) {
                val fade = if (action.url == null) 0.38f else 1f
                if (action.drawable != null) {
                    Icon(painterResource(action.drawable), description, Modifier.graphicsLayer { alpha = fade })
                } else if (action.icon != null) {
                    Icon(action.icon, description, Modifier.graphicsLayer { alpha = fade })
                }
            }
        }
    }
}
