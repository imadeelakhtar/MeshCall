package com.example.meshcall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallHistoryItem(
    val message: ChatMessage,
    val displayName: String,
    val avatarUri: String
)

class CallHistoryAdapter(
    private val onCallClicked: (CallHistoryItem) -> Unit
) : RecyclerView.Adapter<CallHistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<CallHistoryItem>()
    private val timeFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())

    fun setItems(newItems: List<CallHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_call_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageAvatar: ImageView = itemView.findViewById(R.id.imageAvatar)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val iconCallType: ImageView = itemView.findViewById(R.id.iconCallType)
        private val textCallDetails: TextView = itemView.findViewById(R.id.textCallDetails)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)

        fun bind(item: CallHistoryItem) {
            imageAvatar.loadAvatar(item.avatarUri)
            textName.text = item.displayName
            
            textTime.text = timeFormat.format(Date(item.message.timestamp))

            val typeText: String
            val iconTint: Int
            
            if (item.message.text == "MISSED") {
                typeText = "Missed call"
                iconTint = android.graphics.Color.parseColor("#E53935") // Red
            } else if (item.message.text == "DECLINED") {
                typeText = "Declined call"
                iconTint = android.graphics.Color.parseColor("#E53935") // Red
            } else if (item.message.isSent) {
                typeText = "Outgoing call"
                iconTint = android.graphics.Color.parseColor("#43A047") // Green
            } else {
                typeText = "Incoming call"
                iconTint = android.graphics.Color.parseColor("#43A047") // Green
            }

            if (item.message.text == "COMPLETED") {
                val sec = (item.message.durationMs / 1000) % 60
                val min = (item.message.durationMs / 1000) / 60
                textCallDetails.text = "$typeText, $min min $sec sec"
            } else {
                textCallDetails.text = typeText
            }

            iconCallType.setColorFilter(iconTint, android.graphics.PorterDuff.Mode.SRC_IN)

            itemView.setOnClickListener {
                onCallClicked(item)
            }
        }
    }
}
