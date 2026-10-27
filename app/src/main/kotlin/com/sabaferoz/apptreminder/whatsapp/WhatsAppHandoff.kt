package com.sabaferoz.apptreminder.whatsapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sabaferoz.apptreminder.core.DeliveryResult
import com.sabaferoz.apptreminder.core.MessageDelivery
import com.sabaferoz.apptreminder.core.OutgoingMessage
import com.sabaferoz.apptreminder.core.WhatsAppApp
import com.sabaferoz.apptreminder.core.WhatsAppLink

/**
 * Version 1 delivery: open the WhatsApp chat with the text prefilled (wa.me deep link) and stop.
 * The manager reviews and presses Send herself. Version 2 can add a `WhatsAppCloudDelivery : MessageDelivery`
 * and swap it in where this class is constructed.
 */
class WhatsAppHandoff(private val ctx: Context, private val app: WhatsAppApp) : MessageDelivery {
    override fun deliver(message: OutgoingMessage): DeliveryResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppLink.build(message.phoneE164, message.text)))
            .setPackage(app.packageName)
        return try {
            ctx.startActivity(intent)
            DeliveryResult.HandedOff
        } catch (e: ActivityNotFoundException) {
            DeliveryResult.AppNotInstalled
        } catch (e: Exception) {
            DeliveryResult.Failed("Could not open ${app.label}.")
        }
    }

    companion object {
        fun isInstalled(ctx: Context, app: WhatsAppApp): Boolean =
            runCatching { ctx.packageManager.getPackageInfo(app.packageName, 0) }.isSuccess
    }
}
