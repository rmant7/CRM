package com.intelliverse.croman.data.messenger

import com.intelliverse.croman.domain.PhoneLinks

/**
 * Country adapters decide which messengers a workspace may offer.
 * Gateways know how to open one app with a prefilled message. The core
 * never names WhatsApp, Telegram, or MAX except through these ports.
 * Nothing here reads replies.
 */
interface MessengerGateway {
    val id: String
    fun shareUrl(phone: String, message: String): String?
}

interface CountryAdapter {
    val code: String
    fun allows(messengerId: String): Boolean
}

object WhatsAppGateway : MessengerGateway {
    override val id = "whatsapp"
    override fun shareUrl(phone: String, message: String): String? = PhoneLinks.whatsAppUrl(phone, message)
}

object TelegramGateway : MessengerGateway {
    override val id = "telegram"
    override fun shareUrl(phone: String, message: String): String = PhoneLinks.telegramUrl(message)
}

object MaxGateway : MessengerGateway {
    override val id = "max"
    override fun shareUrl(phone: String, message: String): String = PhoneLinks.maxUrl(message)
}

object MessengerRegistry {
    private val gateways = listOf(WhatsAppGateway, TelegramGateway, MaxGateway)
    private val countries = listOf(
        object : CountryAdapter {
            override val code = "IL"
            override fun allows(messengerId: String) = messengerId == "whatsapp" || messengerId == "telegram"
        },
        object : CountryAdapter {
            override val code = "RU"
            override fun allows(messengerId: String) = messengerId == "whatsapp" || messengerId == "telegram" || messengerId == "max"
        },
        object : CountryAdapter {
            override val code = "*"
            override fun allows(messengerId: String) = true
        },
    )

    fun forWorkspace(country: String, requested: List<String>): List<MessengerGateway> {
        val adapter = countries.firstOrNull { it.code == country } ?: countries.first { it.code == "*" }
        return requested.mapNotNull { id -> gateways.firstOrNull { it.id == id } }
            .filter { adapter.allows(it.id) }
    }
}
