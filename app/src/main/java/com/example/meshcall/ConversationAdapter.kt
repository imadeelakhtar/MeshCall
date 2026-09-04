package com.example.meshcall

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private val conversations = mutableListOf<Conversation>()

    fun setConversations(newConversations: List<Conversation>) {
        conversations.clear()
        conversations.addAll(newConversations)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(conversations[position])
    }

    override fun getItemCount() = conversations.size

    class ViewHolder(itemView: View, val onClick: (Conversation) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val textDisplayName: TextView = itemView.findViewById(R.id.textDisplayName)
        private val textLastMessage: TextView = itemView.findViewById(R.id.textLastMessage)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val badgeUnread: TextView = itemView.findViewById(R.id.badgeUnread)
        private val viewConnectionStatus: View = itemView.findViewById(R.id.viewConnectionStatus)
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)

        fun bind(conversation: Conversation) {
            textDisplayName.text = conversation.peerDisplayName.ifEmpty { conversation.peerUsername }
            textLastMessage.text = conversation.lastMessage
            
            if (conversation.unreadCount > 0) {
                badgeUnread.visibility = View.VISIBLE
                badgeUnread.text = conversation.unreadCount.toString()
            } else {
                badgeUnread.visibility = View.GONE
            }

            if (conversation.isConnected) {
                viewConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")) // Green
            } else {
                viewConnectionStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B0B0B0")) // Gray
            }

            imgAvatar.loadAvatar(conversation.peerAvatarUri)

            textTime.text = formatTime(conversation.lastMessageTimestamp)

            itemView.setOnClickListener {
                onClick(conversation)
            }
        }

        private fun formatTime(timestamp: Long): String {
            if (timestamp == 0L) return ""
            return if (DateUtils.isToday(timestamp)) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            } else {
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}
