package com.example.meshcall

import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PeerAdapter(
    private val onPeerClicked: (WifiP2pDevice) -> Unit
) : RecyclerView.Adapter<PeerAdapter.PeerViewHolder>() {

    private val peers = mutableListOf<WifiP2pDevice>()

    fun setPeers(newPeers: Collection<WifiP2pDevice>) {
        peers.clear()
        peers.addAll(newPeers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_peer, parent, false)
        return PeerViewHolder(view, onPeerClicked)
    }

    override fun onBindViewHolder(holder: PeerViewHolder, position: Int) {
        holder.bind(peers[position])
    }

    override fun getItemCount(): Int = peers.size

    class PeerViewHolder(itemView: View, private val onClick: (WifiP2pDevice) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val textDeviceName: TextView = itemView.findViewById(R.id.textDeviceName)
        private val buttonConnect: View = itemView.findViewById(R.id.buttonConnect)
        
        fun bind(device: WifiP2pDevice) {
            textDeviceName.text = device.deviceName ?: "Unknown Device"
            buttonConnect.setOnClickListener { onClick(device) }
            itemView.setOnClickListener { onClick(device) }
        }
    }
}
