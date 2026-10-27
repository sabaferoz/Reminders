package com.sabaferoz.apptreminder.core

import java.net.URLEncoder

object PhoneValidator {
    /** Returns E.164 ("+358401234567") or null if the input is not a plausible international number. */
    fun normalize(raw: String): String? {
        var s = raw.trim().replace(Regex("[\\s\\-().]"), "")
        if (s.startsWith("00")) s = "+" + s.substring(2)
        if (!s.startsWith("+")) return null
        val digits = s.substring(1)
        if (!digits.all { it.isDigit() } || digits.length !in 8..15 || digits.first() == '0') return null
        return "+$digits"
    }

    fun error(raw: String): String? = when {
        raw.isBlank() -> "WhatsApp number is required."
        normalize(raw) == null -> "Use international format with country code, e.g. +358 40 123 4567."
        else -> null
    }

    fun masked(e164: String): String = if (e164.length <= 6) "••••" else e164.take(3) + "•••••" + e164.takeLast(3)
}

object WhatsAppLink {
    /** https://wa.me/<digits>?text=<encoded>; opened with the chosen app package so personal/Business is explicit. */
    fun build(phoneE164: String, text: String): String {
        val digits = phoneE164.removePrefix("+")
        val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        return "https://wa.me/$digits?text=$encoded"
    }
}
