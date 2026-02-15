package com.daricheh.app.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.daricheh.app.databinding.ItemMessageReceivedBinding
import com.daricheh.app.databinding.ItemMessageSentBinding
import com.daricheh.app.model.MeshMessage
import com.daricheh.app.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val myId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TAG = "ChatAdapter"
        private const val VIEW_SENT = 0
        private const val VIEW_RECEIVED = 1
    }

    private val messages = mutableListOf<MeshMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<MeshMessage>) {
        try {
            messages.clear()
            messages.addAll(list)
            notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "submitList error", e)
        }
    }

    fun addMessage(message: MeshMessage) {
        try {
            if (messages.none { it.id == message.id }) {
                messages.add(message)
                notifyItemInserted(messages.size - 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "addMessage error", e)
        }
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        try {
            val index = messages.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                messages[index].status = status
                notifyItemChanged(index)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateStatus error", e)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return try {
            if (messages[position].senderId == myId) VIEW_SENT else VIEW_RECEIVED
        } catch (e: Exception) {
            VIEW_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_SENT) {
            val binding = ItemMessageSentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            SentViewHolder(binding)
        } else {
            val binding = ItemMessageReceivedBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ReceivedViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            val message = messages[position]
            when (holder) {
                is SentViewHolder -> holder.bind(message)
                is ReceivedViewHolder -> holder.bind(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBind error at position $position", e)
        }
    }

    override fun getItemCount() = messages.size

    inner class SentViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MeshMessage) {
            binding.tvMessage.text = message.content
            binding.tvTime.text = timeFormat.format(Date(message.timestamp))
            binding.tvStatus.text = when (message.status) {
                MessageStatus.PENDING -> "⏳"
                MessageStatus.SENT -> "✓"
                MessageStatus.DELIVERED -> "✓✓"
                MessageStatus.FAILED -> "✗"
            }
            if (message.hopCount > 0) {
                binding.tvHops.text = "Hops: ${message.hopCount}"
                binding.tvHops.visibility = View.VISIBLE
            } else {
                binding.tvHops.visibility = View.GONE
            }
        }
    }

    inner class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MeshMessage) {
            binding.tvMessage.text = message.content
            binding.tvSender.text = message.senderName
            binding.tvTime.text = timeFormat.format(Date(message.timestamp))
            if (message.hopCount > 0) {
                binding.tvHops.text = "Hops: ${message.hopCount}"
                binding.tvHops.visibility = View.VISIBLE
            } else {
                binding.tvHops.visibility = View.GONE
            }
        }
    }
}