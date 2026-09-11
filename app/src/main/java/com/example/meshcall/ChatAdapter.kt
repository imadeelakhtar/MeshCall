package com.example.meshcall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val voicePlayer: VoicePlayer) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    private val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun getDateHeaderText(time: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = time }
        val now = java.util.Calendar.getInstance()
        
        if (cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) {
            if (cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "Today"
            }
            now.add(java.util.Calendar.DAY_OF_YEAR, -1)
            if (cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)) {
                return "Yesterday"
            }
        }
        return dateFormat.format(java.util.Date(time))
    }

    private fun updateTimeAndDateHeader(
        msg: ChatMessage,
        prevMsg: ChatMessage?,
        textTimestamp: TextView?,
        textDateHeader: TextView?
    ) {
        textTimestamp?.text = timeFormat.format(java.util.Date(msg.timestamp))
        
        if (textDateHeader != null) {
            val showHeader = prevMsg == null || !isSameDay(msg.timestamp, prevMsg.timestamp)
            if (showHeader) {
                textDateHeader.visibility = View.VISIBLE
                textDateHeader.text = getDateHeaderText(msg.timestamp)
            } else {
                textDateHeader.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 1
        private const val VIEW_TYPE_TEXT_RECEIVED = 2
        private const val VIEW_TYPE_VOICE_SENT = 3
        private const val VIEW_TYPE_VOICE_RECEIVED = 4
        private const val VIEW_TYPE_LOCATION_SENT = 5
        private const val VIEW_TYPE_LOCATION_RECEIVED = 6
    }

    fun getMessages(): List<ChatMessage> = messages
    
    fun updateMessage(index: Int, message: ChatMessage) {
        messages[index] = message
        notifyItemChanged(index)
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return if (msg.type == MessageType.VOICE) {
            if (msg.isSent) VIEW_TYPE_VOICE_SENT else VIEW_TYPE_VOICE_RECEIVED
        } else if (msg.type == MessageType.TEXT && LocationMessageUtils.isLocationMessage(msg.text)) {
            if (msg.isSent) VIEW_TYPE_LOCATION_SENT else VIEW_TYPE_LOCATION_RECEIVED
        } else {
            if (msg.isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TEXT_SENT -> TextViewHolder(inflater.inflate(R.layout.item_chat_sent, parent, false))
            VIEW_TYPE_TEXT_RECEIVED -> TextViewHolder(inflater.inflate(R.layout.item_chat_received, parent, false))
            VIEW_TYPE_VOICE_SENT -> VoiceViewHolder(inflater.inflate(R.layout.item_chat_voice_sent, parent, false))
            VIEW_TYPE_VOICE_RECEIVED -> VoiceViewHolder(inflater.inflate(R.layout.item_chat_voice_received, parent, false))
            VIEW_TYPE_LOCATION_SENT -> LocationViewHolder(inflater.inflate(R.layout.item_chat_location_sent, parent, false))
            VIEW_TYPE_LOCATION_RECEIVED -> LocationViewHolder(inflater.inflate(R.layout.item_chat_location_received, parent, false))
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val prevMsg = if (position > 0) messages[position - 1] else null
        android.util.Log.i("MeshCall_CrashTrace", "CHAT_BIND_START position=$position")
        android.util.Log.i("MeshCall_CrashTrace", "CHAT_BIND_TYPE=${msg.type}")
        if (holder is TextViewHolder) {
            holder.bind(msg, prevMsg)
        } else if (holder is VoiceViewHolder) {
            holder.bind(msg, prevMsg, position)
        } else if (holder is LocationViewHolder) {
            holder.bind(msg, prevMsg)
        }
        
        holder.itemView.setOnLongClickListener { view ->
            showActionSheet(view, msg)
            true
        }
        android.util.Log.i("MeshCall_CrashTrace", "CHAT_BIND_END position=$position")
    }
    
    var onReactionSelected: ((messageId: String, reaction: String) -> Unit)? = null
    var onMessageAction: ((action: String, message: ChatMessage, extra: String?) -> Unit)? = null
    var isPeerConnected: Boolean = false

    private fun showActionSheet(anchor: View, msg: ChatMessage) {
        val context = anchor.context
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val view = android.view.LayoutInflater.from(context).inflate(com.example.meshcall.R.layout.bottom_sheet_message_actions, null)
        bottomSheet.setContentView(view)

        val reactionsContainer = view.findViewById<android.widget.LinearLayout>(com.example.meshcall.R.id.reactionsContainer)
        val reactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")
        reactions.forEach { emoji ->
            val tv = android.widget.TextView(context)
            tv.text = emoji
            tv.textSize = 24f
            tv.setPadding(32, 16, 32, 16)
            tv.setOnClickListener {
                onReactionSelected?.invoke(msg.id, emoji)
                bottomSheet.dismiss()
            }
            reactionsContainer.addView(tv)
        }

        view.findViewById<android.view.View>(com.example.meshcall.R.id.actionInfo).setOnClickListener {
            onMessageAction?.invoke("INFO", msg, null)
            bottomSheet.dismiss()
        }

        view.findViewById<android.view.View>(com.example.meshcall.R.id.actionDeleteForMe).setOnClickListener {
            onMessageAction?.invoke("DELETE_FOR_ME", msg, null)
            bottomSheet.dismiss()
        }

        val deleteForEveryone = view.findViewById<android.view.View>(com.example.meshcall.R.id.actionDeleteForEveryone)
        if (msg.isSent && isPeerConnected) {
            deleteForEveryone.visibility = android.view.View.VISIBLE
            deleteForEveryone.setOnClickListener {
                onMessageAction?.invoke("DELETE_FOR_EVERYONE", msg, null)
                bottomSheet.dismiss()
            }
        }

        bottomSheet.show()
    }

    fun removeMessage(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val textState: TextView? = itemView.findViewById(R.id.textState)
        private val textReactions: TextView? = itemView.findViewById(R.id.textReactions)
        private val imageAttachment: android.widget.ImageView? = itemView.findViewById(R.id.imageAttachment)
        private val layoutDocument: View? = itemView.findViewById(R.id.layoutDocument)
        private val textFileName: TextView? = itemView.findViewById(R.id.textFileName)
        private val textFileSize: TextView? = itemView.findViewById(R.id.textFileSize)
        private val progressTransfer: android.widget.ProgressBar? = itemView.findViewById(R.id.progressTransfer)
        private val textTimestamp: TextView? = itemView.findViewById(R.id.textTimestamp)
        private val textDateHeader: TextView? = itemView.findViewById(R.id.textDateHeader)

        fun bind(message: ChatMessage, prevMessage: ChatMessage?) {
            updateTimeAndDateHeader(message, prevMessage, textTimestamp, textDateHeader)
            textMessage.visibility = View.GONE
            imageAttachment?.visibility = View.GONE
            layoutDocument?.visibility = View.GONE
            progressTransfer?.visibility = View.GONE

            if (message.type == MessageType.TEXT) {
                textMessage.visibility = View.VISIBLE
                textMessage.text = message.text
            } else if (message.type == MessageType.CALL) {
                textMessage.visibility = View.VISIBLE
                val prefix = if (message.text == "MISSED") "📞 Missed call"
                             else if (message.text == "DECLINED") "📞 Declined call"
                             else if (message.isSent) "📞 Outgoing call" else "📞 Incoming call"
                
                if (message.text == "COMPLETED") {
                    val sec = (message.durationMs / 1000) % 60
                    val min = (message.durationMs / 1000) / 60
                    textMessage.text = "$prefix\n$min min $sec sec"
                } else {
                    textMessage.text = prefix
                }
            } else if (message.type == MessageType.IMAGE) {
                imageAttachment?.visibility = View.VISIBLE
                if (message.localFilePath != null) {
                    android.util.Log.i("MeshCall_CrashTrace", "FILE_UI_BIND\nmessageId=${message.id}\nlocalFilePath=${message.localFilePath}")
                    val uri = if (message.localFilePath.startsWith("content://")) android.net.Uri.parse(message.localFilePath) else android.net.Uri.fromFile(java.io.File(message.localFilePath))
                    try {
                        val path = if (message.localFilePath.startsWith("content://")) null else message.localFilePath
                        if (path != null) {
                            val options = android.graphics.BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            android.graphics.BitmapFactory.decodeFile(path, options)
                            var inSampleSize = 1
                            val reqWidth = 400
                            val reqHeight = 400
                            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                                val halfHeight: Int = options.outHeight / 2
                                val halfWidth: Int = options.outWidth / 2
                                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                                    inSampleSize *= 2
                                }
                            }
                            options.inSampleSize = inSampleSize
                            options.inJustDecodeBounds = false
                            val bitmap = android.graphics.BitmapFactory.decodeFile(path, options)
                            imageAttachment?.setImageBitmap(bitmap)
                        } else {
                            itemView.context.contentResolver.openInputStream(uri)?.close()
                            imageAttachment?.setImageURI(uri)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MeshCall_ChatAdapter", "Failed to load image: ${e.message}")
                    }
                    imageAttachment?.setOnClickListener {
                        val intent = android.content.Intent(itemView.context, PhotoViewerActivity::class.java)
                        intent.putExtra("localFilePath", message.localFilePath)
                        itemView.context.startActivity(intent)
                    }
                }
                if (message.progress in 1..99) {
                    progressTransfer?.visibility = View.VISIBLE
                    progressTransfer?.progress = message.progress
                }
            } else if (message.type == MessageType.FILE) {
                layoutDocument?.visibility = View.VISIBLE
                textFileName?.text = message.fileName ?: "Document"
                val sizeKb = (message.fileSize ?: 0) / 1024
                textFileSize?.text = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
                
                layoutDocument?.setOnClickListener {
                    if (message.localFilePath != null) {
                        val uri = if (message.localFilePath.startsWith("content://")) android.net.Uri.parse(message.localFilePath) else android.net.Uri.fromFile(java.io.File(message.localFilePath))
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        intent.setDataAndType(uri, message.mimeType ?: "*/*")
                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { itemView.context.startActivity(intent) } catch (e: Exception) {}
                    }
                }
                if (message.progress in 1..99) {
                    progressTransfer?.visibility = View.VISIBLE
                    progressTransfer?.progress = message.progress
                }
            }
            
            textState?.let {
                if (message.isSent) {
                    it.visibility = View.VISIBLE
                    it.text = when (message.state) {
                        MessageState.SENDING -> "◌"
                        MessageState.SENT -> "✓"
                        MessageState.DELIVERED -> "✓✓"
                        MessageState.SEEN -> "✓✓" // We will color this blue in XML or here
                        MessageState.FAILED -> "!"
                    }
                    if (message.state == MessageState.SEEN) {
                        it.setTextColor(android.graphics.Color.parseColor("#34B7F1")) // WhatsApp blue
                    } else {
                        it.setTextColor(android.graphics.Color.GRAY)
                    }
                } else {
                    it.visibility = View.GONE
                }
            }
            
            textReactions?.let {
                if (message.reactions.isNotEmpty()) {
                    it.visibility = View.VISIBLE
                    it.text = message.reactions.joinToString(" ")
                } else {
                    it.visibility = View.GONE
                }
            }
        }
    }

    inner class VoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val btnPlayPause: ImageButton = itemView.findViewById(R.id.buttonPlayPause)
        private val textDuration: TextView = itemView.findViewById(R.id.textDuration)
        private val textState: TextView? = itemView.findViewById(R.id.textState)
        private val textReactions: TextView? = itemView.findViewById(R.id.textReactions)
        private val textTimestamp: TextView? = itemView.findViewById(R.id.textTimestamp)
        private val textDateHeader: TextView? = itemView.findViewById(R.id.textDateHeader)
        private val waveformView: WaveformView = itemView.findViewById(R.id.waveformView)

        private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var updateRunnable: java.lang.Runnable? = null

        fun bind(message: ChatMessage, prevMessage: ChatMessage?, position: Int) {
            updateTimeAndDateHeader(message, prevMessage, textTimestamp, textDateHeader)
            val audioPath = message.audioPath ?: return
            
            waveformView.setAudioPath(audioPath)
            if (message.isSent) {
                waveformView.setColors(android.graphics.Color.parseColor("#4DFFFFFF"), android.graphics.Color.WHITE)
            } else {
                waveformView.setColors(android.graphics.Color.parseColor("#4D34B7F1"), android.graphics.Color.parseColor("#34B7F1"))
            }

            if (message.durationMs <= 0L) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioPath)
                    val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    message.durationMs = timeStr?.toLong() ?: 0L
                } catch (e: Exception) {
                } finally {
                    try { retriever.release() } catch(e: Exception) {}
                }
            }

            val totalDurationMs = message.durationMs

            fun formatTime(ms: Long): String {
                val totalSec = ms / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                return String.format("%02d:%02d", min, sec)
            }

            val isCurrentlyPlaying = voicePlayer.currentPlayingPath == audioPath
            val isPaused = isCurrentlyPlaying && voicePlayer.isPaused
            val isPlaying = isCurrentlyPlaying && !voicePlayer.isPaused
            
            if (isCurrentlyPlaying) {
                val currentPos = voicePlayer.getCurrentPosition().toLong()
                textDuration.text = formatTime(currentPos)
                waveformView.setProgress(if (totalDurationMs > 0) currentPos.toFloat() / totalDurationMs else 0f)
            } else {
                textDuration.text = formatTime(totalDurationMs)
                waveformView.setProgress(0f)
            }

            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            btnPlayPause.setImageResource(iconRes)
            
            updateRunnable?.let { updateHandler.removeCallbacks(it) }
            
            if (isPlaying) {
                updateRunnable = object : Runnable {
                    override fun run() {
                        if (voicePlayer.currentPlayingPath == audioPath && !voicePlayer.isPaused) {
                            val currentPos = voicePlayer.getCurrentPosition().toLong()
                            textDuration.text = formatTime(currentPos)
                            waveformView.setProgress(if (totalDurationMs > 0) currentPos.toFloat() / totalDurationMs else 0f)
                            updateHandler.postDelayed(this, 50)
                        } else {
                            val pausedPos = if (voicePlayer.isPaused && voicePlayer.currentPlayingPath == audioPath) voicePlayer.getCurrentPosition().toLong() else 0L
                            textDuration.text = formatTime(if (pausedPos > 0) pausedPos else totalDurationMs)
                            waveformView.setProgress(if (totalDurationMs > 0 && pausedPos > 0) pausedPos.toFloat() / totalDurationMs else 0f)
                            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        }
                    }
                }
                updateHandler.post(updateRunnable!!)
            }

            textState?.let {
                if (message.isSent) {
                    it.visibility = View.VISIBLE
                    it.text = when (message.state) {
                        MessageState.SENDING -> "◌"
                        MessageState.SENT -> "✓"
                        MessageState.DELIVERED -> "✓✓"
                        MessageState.SEEN -> "✓✓"
                        MessageState.FAILED -> "!"
                    }
                    if (message.state == MessageState.SEEN) {
                        it.setTextColor(android.graphics.Color.parseColor("#34B7F1")) // WhatsApp blue
                    } else {
                        it.setTextColor(android.graphics.Color.GRAY)
                    }
                } else {
                    it.visibility = View.GONE
                }
            }
            
            textReactions?.let {
                if (message.reactions.isNotEmpty()) {
                    it.visibility = View.VISIBLE
                    it.text = message.reactions.joinToString(" ")
                } else {
                    it.visibility = View.GONE
                }
            }
            
            btnPlayPause.setOnClickListener {
                val currentlyPlayingHere = voicePlayer.currentPlayingPath == audioPath
                if (currentlyPlayingHere) {
                    if (voicePlayer.isPaused) {
                        voicePlayer.play(audioPath) {}
                    } else {
                        voicePlayer.pause()
                    }
                } else {
                    voicePlayer.play(audioPath) {
                        notifyDataSetChanged()
                    }
                }
                notifyDataSetChanged()
            }
        }
    }

    inner class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textCoordinates: TextView? = itemView.findViewById(R.id.textCoordinates)
        private val buttonOpenMap: android.view.View? = itemView.findViewById(R.id.buttonOpenMap)
        private val buttonCopyCoords: android.view.View? = itemView.findViewById(R.id.buttonCopyCoords)
        private val textTimestamp: TextView? = itemView.findViewById(R.id.textTimestamp)
        private val textState: TextView? = itemView.findViewById(R.id.textState)
        private val textDateHeader: TextView? = itemView.findViewById(R.id.textDateHeader)
        private val textReactions: TextView? = itemView.findViewById(R.id.textReactions)

        fun bind(message: ChatMessage, prevMessage: ChatMessage?) {
            updateTimeAndDateHeader(message, prevMessage, textTimestamp, textDateHeader)

            val parsed = LocationMessageUtils.parseLocation(message.text)
            if (parsed != null) {
                textCoordinates?.text = "${parsed.first}, ${parsed.second}"
            } else {
                textCoordinates?.text = "Unknown Coordinates"
            }

            buttonOpenMap?.setOnClickListener {
                if (parsed != null) {
                    val uri = android.net.Uri.parse("geo:${parsed.first},${parsed.second}")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    intent.setPackage("app.organicmaps")
                    try {
                        itemView.context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(itemView.context, "Organic Maps is not installed", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(itemView.context, "Failed to open map", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            buttonCopyCoords?.setOnClickListener {
                if (parsed != null) {
                    val clipboard = itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Coordinates", "${parsed.first}, ${parsed.second}")
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(itemView.context, "Coordinates copied", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            textState?.let {
                if (message.isSent) {
                    it.visibility = View.VISIBLE
                    it.text = when (message.state) {
                        MessageState.SENDING -> "..."
                        MessageState.SENT -> "✓"
                        MessageState.DELIVERED -> "✓✓"
                        MessageState.SEEN -> "✓✓"
                        MessageState.FAILED -> "!"
                    }
                    if (message.state == MessageState.SEEN) {
                        it.setTextColor(android.graphics.Color.parseColor("#34B7F1")) // WhatsApp blue
                    } else {
                        it.setTextColor(android.graphics.Color.GRAY)
                    }
                } else {
                    it.visibility = View.GONE
                }
            }
            
            textReactions?.let {
                if (message.reactions.isNotEmpty()) {
                    it.visibility = View.VISIBLE
                    it.text = message.reactions.joinToString(" ")
                } else {
                    it.visibility = View.GONE
                }
            }
        }
    }
}
