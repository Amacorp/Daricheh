package com.daricheh.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daricheh.app.MeshApplication
import com.daricheh.app.R
import com.daricheh.app.data.ContactsManager
import com.daricheh.app.data.MessageStore
import com.daricheh.app.service.MeshService

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rvConversations: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnTabChats: Button
    private lateinit var btnTabContacts: Button
    private lateinit var btnDiscover: Button
    private lateinit var tvPeerCount: TextView

    private var messageStore: MessageStore? = null
    private var contactsManager: ContactsManager? = null
    private var conversationAdapter: ConversationAdapter? = null
    private var contactsAdapter: ContactsAdapter? = null
    private var meshService: MeshService? = null
    private var isBound = false
    private var showingChats = true
    private val app by lazy { MeshApplication.instance }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as MeshService.MeshBinder
                meshService = binder.getService()
                isBound = true
                setupCallbacks()
                updatePeerCount()
            } catch (e: Exception) {
                app.log("MainActivity: Service connection error: ${e.message}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check setup
        if (!app.isSetupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            app.log("MainActivity: setContentView error: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "UI Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize views with try-catch for each
        try {
            toolbar = findViewById(R.id.toolbar)
            rvConversations = findViewById(R.id.rvConversations)
            tvEmpty = findViewById(R.id.tvEmpty)
            tvUsername = findViewById(R.id.tvUsername)
            tvStatus = findViewById(R.id.tvStatus)
            btnTabChats = findViewById(R.id.btnTabChats)
            btnTabContacts = findViewById(R.id.btnTabContacts)
            btnDiscover = findViewById(R.id.fabDiscover)
            tvPeerCount = findViewById(R.id.tvPeerCount)
        } catch (e: Exception) {
            app.log("MainActivity: findViewById error: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "View Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Set user info
        try {
            tvUsername.text = app.username
            tvStatus.text = "آنلاین • شبکه مش فعال"
        } catch (e: Exception) {
            app.log("MainActivity: Set user info error: ${e.message}")
        }

        // Initialize data
        try {
            messageStore = MessageStore(this)
            contactsManager = ContactsManager(this)
        } catch (e: Exception) {
            app.log("MainActivity: Data init error: ${e.message}")
        }

        setupUI()
        requestPermissions()
    }

    private fun setupUI() {
        try {
            btnTabChats.setOnClickListener { showChatsTab() }
            btnTabContacts.setOnClickListener { showContactsTab() }

            conversationAdapter = ConversationAdapter { conv ->
                openChat(conv.peerId, conv.peerName)
            }

            contactsAdapter = ContactsAdapter { contact ->
                if (contact.hasApp && contact.peerId != null) {
                    try {
                        messageStore?.getOrCreateConversation(contact.peerId!!, contact.name, contact.phoneNumber)
                        openChat(contact.peerId!!, contact.name)
                    } catch (e: Exception) {
                        app.log("MainActivity: Contact click error: ${e.message}")
                    }
                } else {
                    Toast.makeText(this, "این کاربر هنوز اپلیکیشن را ندارد", Toast.LENGTH_SHORT).show()
                }
            }

            rvConversations.layoutManager = LinearLayoutManager(this)
            rvConversations.adapter = conversationAdapter

            btnDiscover.setOnClickListener {
                if (isBound && meshService != null) {
                    try {
                        meshService?.discoverPeers()
                        Toast.makeText(this, "در حال جستجو...", Toast.LENGTH_SHORT).show()
                        btnDiscover.postDelayed({ showPeerPicker() }, 2000)
                    } catch (e: Exception) {
                        app.log("MainActivity: Discover error: ${e.message}")
                    }
                } else {
                    Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
                }
            }

            showChatsTab()
        } catch (e: Exception) {
            app.log("MainActivity: SetupUI error: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Setup Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePeerCount() {
        try {
            val count = meshService?.getDiscoveredPeers()?.size ?: 0
            tvPeerCount.text = count.toString()
        } catch (e: Exception) {
            app.log("MainActivity: Update peer count error: ${e.message}")
        }
    }

    private fun showChatsTab() {
        try {
            showingChats = true
            btnTabChats.setBackgroundColor(Color.parseColor("#6366F1"))
            btnTabChats.setTextColor(Color.WHITE)
            btnTabContacts.setBackgroundColor(Color.TRANSPARENT)
            btnTabContacts.setTextColor(Color.parseColor("#475569"))
            rvConversations.adapter = conversationAdapter
            refreshConversations()
        } catch (e: Exception) {
            app.log("MainActivity: ShowChatsTab error: ${e.message}")
        }
    }

    private fun showContactsTab() {
        try {
            showingChats = false
            btnTabContacts.setBackgroundColor(Color.parseColor("#6366F1"))
            btnTabContacts.setTextColor(Color.WHITE)
            btnTabChats.setBackgroundColor(Color.TRANSPARENT)
            btnTabChats.setTextColor(Color.parseColor("#475569"))
            rvConversations.adapter = contactsAdapter
            refreshContacts()
        } catch (e: Exception) {
            app.log("MainActivity: ShowContactsTab error: ${e.message}")
        }
    }

    private fun showPeerPicker() {
        try {
            val peers = meshService?.getDiscoveredPeers() ?: emptyList()
            if (peers.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("دستگاه‌های نزدیک")
                    .setMessage("هیچ دستگاهی پیدا نشد.\nمطمئن شوید وای‌فای یا بلوتوث روشن است.")
                    .setPositiveButton("تلاش مجدد") { _, _ ->
                        meshService?.discoverPeers()
                        btnDiscover.postDelayed({ showPeerPicker() }, 3000)
                    }
                    .setNegativeButton("بستن", null)
                    .show()
                return
            }

            val names = peers.map { "${it.name} (${it.connectionType})" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("شروع گفتگو با:")
                .setItems(names) { _, which ->
                    try {
                        val peer = peers[which]
                        meshService?.connectToPeer(peer)
                        messageStore?.getOrCreateConversation(peer.id, peer.name, peer.phoneNumber)
                        refreshConversations()
                        openChat(peer.id, peer.name)
                    } catch (e: Exception) {
                        app.log("MainActivity: Peer selection error: ${e.message}")
                    }
                }
                .setNegativeButton("بستن", null)
                .show()
        } catch (e: Exception) {
            app.log("MainActivity: ShowPeerPicker error: ${e.message}")
        }
    }

    private fun openChat(peerId: String, peerName: String) {
        if (peerId.isBlank()) {
            Toast.makeText(this, "Invalid peer", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("peer_id", peerId)
                putExtra("peer_name", peerName)
            })
        } catch (e: Exception) {
            app.log("MainActivity: Open chat error: ${e.message}")
        }
    }

    private fun refreshConversations() {
        try {
            val convs = messageStore?.getAllConversations() ?: emptyList()
            conversationAdapter?.submitList(convs)
            tvEmpty.visibility = if (convs.isEmpty()) View.VISIBLE else View.GONE
            if (convs.isEmpty()) tvEmpty.text = "هنوز مکالمه‌ای ندارید"
        } catch (e: Exception) {
            app.log("MainActivity: RefreshConversations error: ${e.message}")
        }
    }

    private fun refreshContacts() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                tvEmpty.text = "برای نمایش مخاطبین نیاز به دسترسی دارید"
                tvEmpty.visibility = View.VISIBLE
                return
            }
            val contacts = contactsManager?.getPhoneContacts()?.sortedByDescending { it.hasApp } ?: emptyList()
            contactsAdapter?.submitList(contacts)
            tvEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
            if (contacts.isEmpty()) tvEmpty.text = "هیچ‌کدام از مخاطبین شما هنوز این اپ را ندارند"
        } catch (e: Exception) {
            app.log("MainActivity: RefreshContacts error: ${e.message}")
        }
    }

    private fun setupCallbacks() {
        try {
            meshService?.onMessageReceived = { msg ->
                runOnUiThread {
                    try {
                        val conv = messageStore?.getOrCreateConversation(msg.senderId, msg.senderName, msg.senderPhone)
                        conv?.addMessage(msg)
                        conv?.unreadCount = (conv?.unreadCount ?: 0) + 1
                        if (conv != null) messageStore?.saveConversation(conv)
                        if (showingChats) refreshConversations()
                    } catch (e: Exception) {
                        app.log("MainActivity: Message callback error: ${e.message}")
                    }
                }
            }
            meshService?.onPeerListChanged = { peers ->
                runOnUiThread {
                    try {
                        updatePeerCount()
                        peers.forEach {
                            if (it.phoneNumber.isNotBlank()) contactsManager?.registerPhone(it.phoneNumber, it.id)
                        }
                    } catch (e: Exception) {
                        app.log("MainActivity: Peer list callback error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            app.log("MainActivity: SetupCallbacks error: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (showingChats) refreshConversations() else refreshContacts()
            updatePeerCount()
        } catch (e: Exception) {
            app.log("MainActivity: OnResume error: ${e.message}")
        }
    }

    private fun requestPermissions() {
        try {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CONTACTS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.addAll(listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.addAll(listOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.POST_NOTIFICATIONS
                ))
            }
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
            } else {
                startMeshService()
            }
        } catch (e: Exception) {
            app.log("MainActivity: RequestPermissions error: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        try {
            startMeshService()
        } catch (e: Exception) {
            app.log("MainActivity: Permissions result error: ${e.message}")
        }
    }

    private fun startMeshService() {
        try {
            val intent = Intent(this, MeshService::class.java)
            ContextCompat.startForegroundService(this, intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            app.log("MainActivity: StartService error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isBound) {
                unbindService(serviceConnection)
                isBound = false
            }
        } catch (e: Exception) {
            app.log("MainActivity: OnDestroy error: ${e.message}")
        }
    }
}