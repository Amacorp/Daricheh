package com.daricheh.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.daricheh.app.MeshApplication
import com.daricheh.app.model.Conversation

class MessageStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("message_store", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val app = MeshApplication.instance

    fun getOrCreateConversation(peerId: String, peerName: String, peerPhone: String = ""): Conversation {
        val existing = getConversation(peerId)
        if (existing != null) return existing

        val conversation = Conversation(
            peerId = peerId,
            peerName = peerName,
            peerPhone = peerPhone
        )
        saveConversation(conversation)
        app.log("Created conversation with $peerName ($peerId)")
        return conversation
    }

    fun getConversation(peerId: String): Conversation? {
        val json = prefs.getString("conv_$peerId", null) ?: return null
        return try {
            gson.fromJson(json, Conversation::class.java)
        } catch (e: Exception) {
            app.log("Error loading conversation $peerId: ${e.message}")
            null
        }
    }

    fun saveConversation(conversation: Conversation) {
        try {
            prefs.edit().putString("conv_${conversation.peerId}", gson.toJson(conversation)).apply()
            addToList(conversation.peerId)
        } catch (e: Exception) {
            app.log("Error saving conversation: ${e.message}")
        }
    }

    fun getAllConversations(): List<Conversation> {
        return try {
            getIdList().mapNotNull { getConversation(it) }
                .sortedByDescending { it.lastMessageTime }
        } catch (e: Exception) {
            app.log("Error getting conversations: ${e.message}")
            emptyList()
        }
    }

    private fun getIdList(): Set<String> {
        val json = prefs.getString("conv_id_list", null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun addToList(peerId: String) {
        val ids = getIdList().toMutableSet()
        ids.add(peerId)
        prefs.edit().putString("conv_id_list", gson.toJson(ids)).apply()
    }
}