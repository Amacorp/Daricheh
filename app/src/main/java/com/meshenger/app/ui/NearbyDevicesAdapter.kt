package com.daricheh.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.daricheh.app.R
import com.daricheh.app.model.Peer

class NearbyDevicesAdapter(
    private var peers: List<Peer>,
    private val onConnect: (Peer) -> Unit
) : RecyclerView.Adapter<NearbyDevicesAdapter.ViewHolder>() {

    fun updateList(newPeers: List<Peer>) {
        peers = newPeers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(peers[position])
    }

    override fun getItemCount() = peers.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardDevice)
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvInfo: TextView = itemView.findViewById(R.id.tvDeviceInfo)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        private val btnConnect: Button = itemView.findViewById(R.id.btnConnect)

        fun bind(peer: Peer) {
            tvName.text = peer.name
            tvInfo.text = "${peer.connectionType} • ${peer.deviceType}"
            tvAddress.text = peer.address

            btnConnect.setOnClickListener {
                onConnect(peer)
            }
        }
    }
}