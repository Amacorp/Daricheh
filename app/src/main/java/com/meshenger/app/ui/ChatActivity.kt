package com.daricheh.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daricheh.app.MeshApplication
import com.daricheh.app.R
import com.daricheh.app.data.MessageStore
import com.daricheh.app.model.MeshMessage
import com.daricheh.app.service.MeshService

class ChatActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var tvChatName: TextView

    private var messageStore: MessageStore? = null
    private var chatAdapter: ChatAdapter? = null
    private var peerId: String = ""
    private var peerName: String = ""
    private var meshService: MeshService? = null
    private var isBound = false
    private val app by lazy { MeshApplication.instance }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                meshService = (service as MeshService.MeshBinder).getService()
                isBound = true

                meshService?.onMessageReceived = { msg ->
                    if (msg.senderId == peerId) {
                        runOnUiThread {
                            try {
                                addMsg(msg)
                            } catch (e: Exception) {
                                app.log("ChatActivity: Message receive error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                app.log("ChatActivity: Service connection error: ${e.message}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_chat)
        } catch (e: Exception) {
            app.log("ChatActivity: setContentView error: ${e.message}")
            Toast.makeText(this, "UI Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Find views
        try {
            toolbar = findViewById(R.id.toolbar)
            rvMessages = findViewById(R.id.rvMessages)
            etMessage = findViewById(R.id.etMessage)
            btnSend = findViewById(R.id.btnSend)
            tvChatName = findViewById(R.id.tvChatName)
        } catch (e: Exception) {
            app.log("ChatActivity: findViewById error: ${e.message}")
            Toast.makeText(this, "View Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        peerId = intent.getStringExtra("peer_id") ?: ""
        peerName = intent.getStringExtra("peer_name") ?: "Unknown"

        if (peerId.isEmpty()) {
            Toast.makeText(this, "Invalid peer", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvChatName.text = peerName

        try {
            messageStore = MessageStore(this)
        } catch (e: Exception) {
            app.log("ChatActivity: MessageStore init error: ${e.message}")
        }

        setupUI()
        loadMessages()

        try {
            val intent = Intent(this, MeshService::class.java)
            bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            app.log("ChatActivity: Bind service error: ${e.message}")
        }
    }

    private fun setupUI() {
        try {
            chatAdapter = ChatAdapter(app.deviceId)
            rvMessages.apply {
                layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                    stackFromEnd = true
                }
                adapter = chatAdapter
            }

            btnSend.setOnClickListener {
                try {
                    val text = etMessage.text?.toString()?.trim() ?: ""
                    if (text.isNotEmpty() && isBound && meshService != null) {
                        val msg = meshService?.sendMessage(peerId, text)
                        if (msg != null) {
                            addMsg(msg)
                            etMessage.text?.clear()
                        } else {
                            Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    app.log("ChatActivity: Send error: ${e.message}")
                    Toast.makeText(this, "Send error", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            app.log("ChatActivity: SetupUI error: ${e.message}")
        }
    }

    private fun loadMessages() {
        try {
            val conv = messageStore?.getConversation(peerId)
            if (conv != null) {
                chatAdapter?.submitList(conv.messages.toMutableList())
                scroll()
                conv.unreadCount = 0
                messageStore?.saveConversation(conv)
            }
        } catch (e: Exception) {
            app.log("ChatActivity: LoadMessages error: ${e.message}")
        }
    }

    private fun addMsg(msg: MeshMessage) {
        try {
            chatAdapter?.addMessage(msg)
            scroll()
            val conv = messageStore?.getOrCreateConversation(peerId, peerName)
            conv?.addMessage(msg)
            if (conv != null) {
                messageStore?.saveConversation(conv)
            }
        } catch (e: Exception) {
            app.log("ChatActivity: AddMsg error: ${e.message}")
        }
    }

    private fun scroll() {
        try {
            rvMessages.post {
                try {
                    val c = chatAdapter?.itemCount ?: 0
                    if (c > 0) rvMessages.smoothScrollToPosition(c - 1)
                } catch (e: Exception) {
                    app.log("ChatActivity: Scroll error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            app.log("ChatActivity: Scroll post error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isBound) {
                unbindService(conn)
                isBound = false
            }
        } catch (e: Exception) {
            app.log("ChatActivity: OnDestroy error: ${e.message}")
        }
    }
}