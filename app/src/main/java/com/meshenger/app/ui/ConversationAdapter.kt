package com.daricheh.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.daricheh.app.R
import com.daricheh.app.model.Conversation
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private val conversations = mutableListOf<Conversation>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val avatarColors = intArrayOf(
        0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(),
        0xFFF59E0B.toInt(), 0xFF8B5CF6.toInt(), 0xFF06B6D4.toInt()
    )

    fun submitList(list: List<Conversation>) {
        conversations.clear()
        conversations.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            holder.bind(conversations[position])
        } catch (_: Exception) {}
    }

    override fun getItemCount() = conversations.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // استفاده از findViewById برای view های جدید
        private val ivAvatar: ShapeableImageView? = itemView.findViewById(R.id.ivAvatar)
        private val tvPeerName: TextView? = itemView.findViewById(R.id.tvPeerName)
        private val tvLastMessage: TextView? = itemView.findViewById(R.id.tvLastMessage)
        private val tvTime: TextView? = itemView.findViewById(R.id.tvTime)
        private val tvUnreadCount: TextView? = itemView.findViewById(R.id.tvUnreadCount)
        private val viewOnline: View? = itemView.findViewById(R.id.viewOnline)

        fun bind(conversation: Conversation) {
            tvPeerName?.text = conversation.peerName
            tvLastMessage?.text = conversation.lastMessage?.content ?: "شروع گفتگو..."
            tvTime?.text = if (conversation.lastMessageTime > 0) {
                timeFormat.format(Date(conversation.lastMessageTime))
            } else ""

            if (conversation.unreadCount > 0) {
                tvUnreadCount?.text = conversation.unreadCount.toString()
                tvUnreadCount?.visibility = View.VISIBLE
            } else {
                tvUnreadCount?.visibility = View.GONE
            }

            // Avatar color
            val colorIndex = Math.abs(conversation.peerId.hashCode()) % avatarColors.size
            ivAvatar?.setBackgroundColor(avatarColors[colorIndex])

            // Online status
            viewOnline?.visibility = if (conversation.isOnline) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onClick(conversation) }
        }
    }
}