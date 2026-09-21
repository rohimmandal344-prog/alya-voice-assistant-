package com.example.domain.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ContactEntry(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

data class CallPlacementResult(
    val success: Boolean,
    val recipientName: String,
    val phoneNumber: String,
    val isDirectCall: Boolean,
    val message: String
)

class ContactManager(private val context: Context) {

    fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun syncContactsToMemory(memoryDao: com.example.data.local.dao.MemoryDao) {
        if (!hasReadContactsPermission()) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val contentResolver: ContentResolver = context.contentResolver
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )
                
                var syncedCount = 0
                cursor?.use {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                        val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                        if (name.isNotBlank() && number.isNotBlank()) {
                            val cleanNumber = number.filter { char -> char.isDigit() || char == '+' }
                            memoryDao.insertMemory(com.example.data.local.entity.MemoryEntity(
                                category = "Contact",
                                key = name,
                                content = "Phone number: $cleanNumber"
                            ))
                            syncedCount++
                        }
                    }
                }
                Log.i(TAG, "Synced $syncedCount contacts to Alya's memory.")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing contacts to memory: ${e.message}", e)
            }
        }
    }

    /**
     * Searches device contacts by name or phone number.
     * Ranks exact matches first, then prefix matches, then substring matches.
     */
    fun searchContacts(query: String, maxResults: Int = 10): List<ContactEntry> {
        if (!hasReadContactsPermission()) {
            Log.w(TAG, "READ_CONTACTS permission not granted.")
            return emptyList()
        }

        val rawClean = query.trim().lowercase()
        if (rawClean.isBlank()) return emptyList()

        // Strip common voice noise/prefixes/suffixes
        val stopWords = listOf("now", "call", "dial", "please", "can", "you", "to", "phone", "da", "bhai", "ji", "didi", "sir", "madam", "uncle")
        val words = rawClean.split("\\s+".toRegex()).filter { it !in stopWords }
        val cleanQuery = words.joinToString(" ").ifBlank { rawClean }
        val primaryKeyword = words.firstOrNull() ?: cleanQuery

        val results = mutableListOf<ContactEntry>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val photo = if (photoIdx >= 0) it.getString(photoIdx) else null

                    val nameLower = name.lowercase()
                    val numDigits = number.filter { char -> char.isDigit() }
                    val queryDigits = cleanQuery.filter { char -> char.isDigit() }

                    val matchesFull = nameLower.contains(cleanQuery)
                    val matchesKeyword = primaryKeyword.length >= 2 && nameLower.contains(primaryKeyword)
                    val matchesNumber = queryDigits.isNotEmpty() && numDigits.contains(queryDigits)

                    if (matchesFull || matchesKeyword || matchesNumber) {
                        results.add(ContactEntry(id, name, number, photo))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts: ${e.message}", e)
        }

        // Rank by best match relevance
        return results.distinctBy { it.phoneNumber }.sortedWith(
            compareBy<ContactEntry> { entry ->
                val nameLower = entry.name.lowercase()
                when {
                    nameLower == cleanQuery -> 0
                    nameLower == primaryKeyword -> 1
                    nameLower.startsWith(cleanQuery) || nameLower.startsWith(primaryKeyword) -> 2
                    nameLower.contains(cleanQuery) -> 3
                    else -> 4
                }
            }
        ).take(maxResults)
    }

    /**
     * Looks up contact name from an incoming phone number.
     */
    fun findCallerNameByNumber(rawNumber: String): String? {
        if (!hasReadContactsPermission() || rawNumber.isBlank()) return null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        return it.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve contact by number: ${e.message}")
        }
        return null
    }

    /**
     * Places a phone call dynamically:
     * - If target is numeric, calls directly or dials.
     * - If target is a contact name, searches contacts first to retrieve phone number.
     */
    fun placeCall(target: String): CallPlacementResult {
        val trimmed = target.trim()
        if (trimmed.isBlank()) {
            // Open dialer screen only when explicitly requested with empty target
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            return CallPlacementResult(
                success = true,
                recipientName = "Phone Dialer",
                phoneNumber = "",
                isDirectCall = false,
                message = "Opening phone dialer."
            )
        }

        // Check if target is already a phone number
        val digits = trimmed.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val isNumeric = digits.length >= 3 && (digits.length >= trimmed.length / 2)

        val targetNumber: String
        val displayName: String

        if (isNumeric) {
            targetNumber = digits
            val lookupName = findCallerNameByNumber(digits)
            displayName = lookupName ?: digits
        } else {
            // Search contact by name
            val contacts = searchContacts(trimmed)
            if (contacts.isEmpty()) {
                return CallPlacementResult(
                    success = false,
                    recipientName = trimmed,
                    phoneNumber = "",
                    isDirectCall = false,
                    message = if (!hasReadContactsPermission()) {
                        "Please grant Contacts permission so Alya can look up '$trimmed'."
                    } else {
                        "I couldn't find a matching contact for '$trimmed'. Please check the name or tell me the contact name again."
                    }
                )
            }

            val bestMatch = contacts.first()
            displayName = bestMatch.name
            targetNumber = bestMatch.phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        }

        val uri = Uri.parse("tel:$targetNumber")
        val canDirectCall = hasCallPhonePermission()

        val callIntent = if (canDirectCall) {
            Intent(Intent.ACTION_CALL, uri)
        } else {
            Intent(Intent.ACTION_DIAL, uri)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(callIntent)
            CallPlacementResult(
                success = true,
                recipientName = displayName,
                phoneNumber = targetNumber,
                isDirectCall = canDirectCall,
                message = if (canDirectCall) {
                    "Calling $displayName."
                } else {
                    "Opening dialer for $displayName ($targetNumber)."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call: ${e.message}", e)
            CallPlacementResult(
                success = false,
                recipientName = displayName,
                phoneNumber = targetNumber,
                isDirectCall = false,
                message = "Failed to place call to $displayName: ${e.localizedMessage}"
            )
        }
    }

    companion object {
        private const val TAG = "ContactManager"
    }
}
