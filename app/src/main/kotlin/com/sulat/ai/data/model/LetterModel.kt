package com.sulat.ai.data.model

import java.util.Date
import java.util.UUID

data class Recipient(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val position: String = "",
    val organization: String = "",
    val address: String = "",
    val optionalInfo: String = ""
)

data class LetterDate(
    val date: Date,
    val label: String
)

data class SenderProfile(
    val name: String = "",
    val address: String = "",
    val lokal: String = "",
    val distrito: String = "",
    val contactNumber: String = "",
    val signature: String = ""
)

data class PersonalExperience(
    val id: String,
    val happened: String,
    val whatDidYouDo: String,
    val whatDidYouLearn: String,
    val whatChanged: String,
    val whatYouWantToExpress: String,
    val createdTime: Long = System.currentTimeMillis()
)

data class LetterDraft(
    val id: String = UUID.randomUUID().toString(),
    val recipients: List<Recipient> = emptyList(),
    val dates: List<LetterDate> = emptyList(),
    val sender: SenderProfile = SenderProfile(),
    val body: String = "",
    val subject: String = "",
    val greeting: String = "",
    val createdTime: Long = System.currentTimeMillis(),
    val modifiedTime: Long = System.currentTimeMillis(),
    val isGenerated: Boolean = false
)
