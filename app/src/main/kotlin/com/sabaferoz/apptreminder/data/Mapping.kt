package com.sabaferoz.apptreminder.data

import com.sabaferoz.apptreminder.core.AppointmentInfo
import com.sabaferoz.apptreminder.core.EventAttendee
import com.sabaferoz.apptreminder.core.PatientProfile
import com.sabaferoz.apptreminder.core.ReminderKey
import com.sabaferoz.apptreminder.core.ReminderKind
import com.sabaferoz.apptreminder.core.ReminderRule
import com.sabaferoz.apptreminder.core.ReminderState
import com.sabaferoz.apptreminder.core.ReminderStatus
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun ms(i: Instant?) = i?.toEpochMilli()
private fun inst(ms: Long?) = ms?.let(Instant::ofEpochMilli)

fun PatientEntity.toCore() = PatientProfile(
    id = id, alias = alias, email = email, phone = phone, timeZoneId = timeZoneId, consent = consent,
    overrideA = overrideAJson?.let { AppJson.decodeFromString<ReminderRule>(it) },
    overrideB = overrideBJson?.let { AppJson.decodeFromString<ReminderRule>(it) },
    templateA = templateA, templateB = templateB,
)

fun ruleToJson(r: ReminderRule?): String? = r?.let { AppJson.encodeToString(ReminderRule.serializer(), it) }

fun AppointmentEntity.toCore() = AppointmentInfo(
    id = id, start = Instant.ofEpochMilli(startMs), end = Instant.ofEpochMilli(endMs), cancelled = cancelled,
    meetLink = meetLink, attendees = AppJson.decodeFromString(ListSerializer(EventAttendee.serializer()), attendeesJson),
    changeNote = changeNote,
)

fun AppointmentInfo.toEntity(isDemo: Boolean, reviewNote: String?) = AppointmentEntity(
    id = id, startMs = start.toEpochMilli(), endMs = end.toEpochMilli(), cancelled = cancelled, meetLink = meetLink,
    attendeesJson = AppJson.encodeToString(ListSerializer(EventAttendee.serializer()), attendees),
    changeNote = changeNote, reviewNote = reviewNote, isDemo = isDemo,
)

fun ReminderEntity.toCore() = ReminderState(
    key = ReminderKey(appointmentId, patientId, ReminderKind.valueOf(kind)),
    status = ReminderStatus.valueOf(status), dueAt = inst(dueAtMs), startSnapshot = Instant.ofEpochMilli(startSnapshotMs),
    reason = reason, locked = locked, draft = draft, createdAt = Instant.ofEpochMilli(createdAtMs),
    readyAt = inst(readyAtMs), notifiedAt = inst(notifiedAtMs), openedAt = inst(openedAtMs),
    confirmedAt = inst(confirmedAtMs), skippedAt = inst(skippedAtMs),
)

fun ReminderState.toEntity() = ReminderEntity(
    appointmentId = key.appointmentId, patientId = key.patientId, kind = key.kind.name, status = status.name,
    dueAtMs = ms(dueAt), startSnapshotMs = startSnapshot.toEpochMilli(), reason = reason, locked = locked, draft = draft,
    createdAtMs = createdAt.toEpochMilli(), readyAtMs = ms(readyAt), notifiedAtMs = ms(notifiedAt), openedAtMs = ms(openedAt),
    confirmedAtMs = ms(confirmedAt), skippedAtMs = ms(skippedAt),
)
