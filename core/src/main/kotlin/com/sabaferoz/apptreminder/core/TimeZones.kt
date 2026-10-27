package com.sabaferoz.apptreminder.core

import java.time.ZoneId
import java.util.Locale

data class Country(val iso: String, val name: String)
data class RegionOption(val label: String, val zoneId: String)

sealed interface ZoneResolution {
    data class Single(val zoneId: String) : ZoneResolution
    /** Country spans several zones: ask for state/region/city. */
    data class NeedsRegion(val options: List<RegionOption>) : ZoneResolution
    /** No data: use the searchable IANA list. */
    data object Unknown : ZoneResolution
}

/** Supplies zone IDs per country for countries not curated below (Android: ICU; tests: fake). */
fun interface CountryZoneSource { fun zoneIds(iso: String): List<String> }

class TimeZoneResolver(private val fallback: CountryZoneSource = CountryZoneSource { emptyList() }) {

    fun countries(): List<Country> =
        Locale.getISOCountries().map { Country(it, Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH)) }
            .filter { it.name.isNotBlank() && it.name != it.iso }
            .sortedBy { it.name }

    fun resolve(iso: String): ZoneResolution {
        val code = iso.uppercase()
        Curated.regions[code]?.let { return ZoneResolution.NeedsRegion(it) }
        Curated.single[code]?.let { return ZoneResolution.Single(it) }
        val ids = fallback.zoneIds(code).filter(::valid).distinct()
        return when {
            ids.isEmpty() -> ZoneResolution.Unknown
            ids.size == 1 -> ZoneResolution.Single(ids[0])
            else -> ZoneResolution.NeedsRegion(ids.map { RegionOption(prettyCity(it), it) }.sortedBy { it.label })
        }
    }

    fun allIanaZones(): List<String> = ZoneId.getAvailableZoneIds()
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && !it.startsWith("US/") }
        .sorted()

    companion object {
        fun valid(id: String) = runCatching { ZoneId.of(id) }.isSuccess
        fun prettyCity(id: String) = id.substringAfterLast('/').replace('_', ' ') + " (" + id.substringBefore('/') + ")"
        fun search(query: String, zones: List<String>): List<String> {
            val q = query.trim().lowercase().replace(' ', '_')
            return if (q.isEmpty()) zones else zones.filter { it.lowercase().contains(q) }
        }
    }
}

internal object Curated {
    val single: Map<String, String> = mapOf(
        "PK" to "Asia/Karachi", "FI" to "Europe/Helsinki", "GB" to "Europe/London", "IE" to "Europe/Dublin",
        "DE" to "Europe/Berlin", "FR" to "Europe/Paris", "SE" to "Europe/Stockholm", "NO" to "Europe/Oslo",
        "DK" to "Europe/Copenhagen", "NL" to "Europe/Amsterdam", "BE" to "Europe/Brussels", "CH" to "Europe/Zurich",
        "AT" to "Europe/Vienna", "IT" to "Europe/Rome", "PL" to "Europe/Warsaw", "EE" to "Europe/Tallinn",
        "LV" to "Europe/Riga", "LT" to "Europe/Vilnius", "GR" to "Europe/Athens", "RO" to "Europe/Bucharest",
        "BG" to "Europe/Sofia", "HU" to "Europe/Budapest", "CZ" to "Europe/Prague", "SK" to "Europe/Bratislava",
        "UA" to "Europe/Kyiv", "IS" to "Atlantic/Reykjavik", "TR" to "Europe/Istanbul",
        "AE" to "Asia/Dubai", "SA" to "Asia/Riyadh", "QA" to "Asia/Qatar", "KW" to "Asia/Kuwait", "OM" to "Asia/Muscat",
        "BH" to "Asia/Bahrain", "IQ" to "Asia/Baghdad", "IR" to "Asia/Tehran", "AF" to "Asia/Kabul", "JO" to "Asia/Amman",
        "IL" to "Asia/Jerusalem", "IN" to "Asia/Kolkata", "BD" to "Asia/Dhaka", "LK" to "Asia/Colombo", "NP" to "Asia/Kathmandu",
        "SG" to "Asia/Singapore", "MY" to "Asia/Kuala_Lumpur", "TH" to "Asia/Bangkok", "VN" to "Asia/Ho_Chi_Minh",
        "PH" to "Asia/Manila", "JP" to "Asia/Tokyo", "KR" to "Asia/Seoul", "CN" to "Asia/Shanghai", "HK" to "Asia/Hong_Kong",
        "EG" to "Africa/Cairo", "ZA" to "Africa/Johannesburg", "NG" to "Africa/Lagos", "KE" to "Africa/Nairobi",
        "NZ" to "Pacific/Auckland", "AR" to "America/Argentina/Buenos_Aires", "CO" to "America/Bogota", "PE" to "America/Lima",
    )

    private const val NY = "America/New_York"
    private const val CHI = "America/Chicago"
    private const val DEN = "America/Denver"
    private const val LA = "America/Los_Angeles"

    private val us = listOf(
        "Alabama (Central)" to CHI, "Alaska" to "America/Anchorage", "Arizona (no DST) – Phoenix" to "America/Phoenix",
        "Arkansas (Central)" to CHI, "California – Los Angeles, San Francisco (Pacific)" to LA, "Colorado – Denver (Mountain)" to DEN,
        "Connecticut (Eastern)" to NY, "Delaware (Eastern)" to NY, "District of Columbia – Washington (Eastern)" to NY,
        "Florida – Miami, Orlando (Eastern)" to NY, "Florida panhandle – Pensacola (Central)" to CHI,
        "Georgia – Atlanta (Eastern)" to NY, "Hawaii – Honolulu" to "Pacific/Honolulu", "Idaho – Boise (Mountain)" to DEN,
        "Idaho north – Coeur d'Alene (Pacific)" to LA, "Illinois – Chicago (Central)" to CHI, "Indiana – Indianapolis (Eastern)" to NY,
        "Iowa (Central)" to CHI, "Kansas (Central)" to CHI, "Kentucky – Louisville (Eastern)" to NY, "Kentucky west (Central)" to CHI,
        "Louisiana – New Orleans (Central)" to CHI, "Maine (Eastern)" to NY, "Maryland – Baltimore (Eastern)" to NY,
        "Massachusetts – Boston (Eastern)" to NY, "Michigan – Detroit (Eastern)" to NY, "Minnesota (Central)" to CHI,
        "Mississippi (Central)" to CHI, "Missouri (Central)" to CHI, "Montana (Mountain)" to DEN, "Nebraska (Central)" to CHI,
        "Nevada – Las Vegas (Pacific)" to LA, "New Hampshire (Eastern)" to NY, "New Jersey (Eastern)" to NY,
        "New Mexico (Mountain)" to DEN, "New York – New York City (Eastern)" to NY, "North Carolina (Eastern)" to NY,
        "North Dakota (Central)" to CHI, "Ohio (Eastern)" to NY, "Oklahoma (Central)" to CHI, "Oregon – Portland (Pacific)" to LA,
        "Pennsylvania – Philadelphia (Eastern)" to NY, "Rhode Island (Eastern)" to NY, "South Carolina (Eastern)" to NY,
        "South Dakota (Central)" to CHI, "Tennessee – Nashville (Central)" to CHI, "Tennessee east – Knoxville (Eastern)" to NY,
        "Texas – Houston, Dallas (Central)" to CHI, "Texas – El Paso (Mountain)" to DEN, "Utah (Mountain)" to DEN,
        "Vermont (Eastern)" to NY, "Virginia (Eastern)" to NY, "Washington state – Seattle (Pacific)" to LA,
        "West Virginia (Eastern)" to NY, "Wisconsin (Central)" to CHI, "Wyoming (Mountain)" to DEN,
    )

    private val ca = listOf(
        "Alberta – Calgary, Edmonton" to "America/Edmonton", "British Columbia – Vancouver" to "America/Vancouver",
        "Manitoba – Winnipeg" to "America/Winnipeg", "New Brunswick" to "America/Moncton",
        "Newfoundland and Labrador – St. John's" to "America/St_Johns", "Northwest Territories" to "America/Yellowknife",
        "Nova Scotia – Halifax" to "America/Halifax", "Nunavut – Iqaluit" to "America/Iqaluit",
        "Ontario – Toronto, Ottawa" to "America/Toronto", "Prince Edward Island" to "America/Halifax",
        "Quebec – Montreal" to "America/Toronto", "Saskatchewan – Regina" to "America/Regina", "Yukon – Whitehorse" to "America/Whitehorse",
    )

    private val au = listOf(
        "Australian Capital Territory – Canberra" to "Australia/Sydney", "New South Wales – Sydney" to "Australia/Sydney",
        "Northern Territory – Darwin" to "Australia/Darwin", "Queensland – Brisbane" to "Australia/Brisbane",
        "South Australia – Adelaide" to "Australia/Adelaide", "Tasmania – Hobart" to "Australia/Hobart",
        "Victoria – Melbourne" to "Australia/Melbourne", "Western Australia – Perth" to "Australia/Perth",
    )

    private val es = listOf("Spain – Madrid, Barcelona" to "Europe/Madrid", "Canary Islands" to "Atlantic/Canary")

    val regions: Map<String, List<RegionOption>> = mapOf("US" to us, "CA" to ca, "AU" to au, "ES" to es)
        .mapValues { (_, v) -> v.map { RegionOption(it.first, it.second) } }
}
