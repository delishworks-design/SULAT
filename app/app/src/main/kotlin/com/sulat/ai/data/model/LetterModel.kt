package com.sulat.ai.data.model

data class Recipient(
    val name: String,
    val position: String,
    val organization: String
)

data class LetterDate(
    val date: java.util.Date,
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
    val id: String,
    val recipient: Recipient,
    val dates: List<LetterDate>,
    val sender: SenderProfile,
    val body: String,
    val createdTime: Long,
    val modifiedTime: Long,
    var isGenerated: Boolean = false
)