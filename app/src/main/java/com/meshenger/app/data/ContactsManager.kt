package com.daricheh.app.data

import android.content.Context
import android.provider.ContactsContract
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.daricheh.app.MeshApplication
import com.daricheh.app.model.Contact

class ContactsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("contacts_store", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val app = MeshApplication.instance

    fun getPhoneContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val registered = getRegisteredPhones()

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            val seen = mutableSetOf<String>()

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val rawNum = it.getString(numIdx) ?: continue
                    val normalized = normalizePhone(rawNum)

                    if (normalized.length < 10 || seen.contains(normalized)) continue
                    seen.add(normalized)

                    contacts.add(Contact(
                        name = name,
                        phoneNumber = normalized,
                        hasApp = registered.containsKey(normalized),
                        peerId = registered[normalized]
                    ))
                }
            }
        } catch (e: Exception) {
            app.log("Error reading contacts: ${e.message}")
        }

        app.log("Loaded ${contacts.size} contacts, ${contacts.count { it.hasApp }} have app")
        return contacts
    }

    fun registerPhone(phoneNumber: String, peerId: String) {
        val phones = getRegisteredPhones().toMutableMap()
        val normalized = normalizePhone(phoneNumber)
        phones[normalized] = peerId
        prefs.edit().putString("registered_phones", gson.toJson(phones)).apply()
        app.log("Registered phone $normalized -> $peerId")
    }

    fun getRegisteredPhones(): Map<String, String> {
        val json = prefs.getString("registered_phones", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun normalizePhone(phone: String): String {
        var n = phone.replace(Regex("[\\s\\-().]"), "")
        if (n.startsWith("+98")) n = "0" + n.substring(3)
        else if (n.startsWith("98") && n.length > 10) n = "0" + n.substring(2)
        else if (n.startsWith("00989")) n = "0" + n.substring(4)
        return n
    }
}