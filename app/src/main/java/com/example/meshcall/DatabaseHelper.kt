package com.example.meshcall

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "MeshCall.db"
        private const val DATABASE_VERSION = 4

        const val TABLE_CONVERSATIONS = "conversations"
        const val COL_CONV_ID = "peerUserId"
        const val COL_CONV_USERNAME = "peerUsername"
        const val COL_CONV_DISPLAY_NAME = "peerDisplayName"
        const val COL_CONV_AVATAR_URI = "peerAvatarUri"
        const val COL_CONV_LAST_MESSAGE = "lastMessage"
        const val COL_CONV_LAST_MSG_TYPE = "lastMessageType"
        const val COL_CONV_LAST_MSG_TIME = "lastMessageTimestamp"
        const val COL_CONV_UNREAD = "unreadCount"

        const val TABLE_MESSAGES = "messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_CONV_ID = "conversationId"
        const val COL_MSG_TEXT = "text"
        const val COL_MSG_IS_SENT = "isSent"
        const val COL_MSG_TYPE = "type"
        const val COL_MSG_AUDIO_PATH = "audioPath"
        const val COL_MSG_DURATION = "durationMs"
        const val COL_MSG_STATE = "state"
        const val COL_MSG_TIMESTAMP = "timestamp"
        const val COL_MSG_REACTIONS = "reactions"
        const val COL_MSG_FILE_NAME = "fileName"
        const val COL_MSG_MIME_TYPE = "mimeType"
        const val COL_MSG_FILE_SIZE = "fileSize"
        const val COL_MSG_LOCAL_PATH = "localFilePath"
        const val COL_MSG_DELIVERED_AT = "deliveredAt"
        const val COL_MSG_SEEN_AT = "seenAt"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createConvTable = """
            CREATE TABLE $TABLE_CONVERSATIONS (
                $COL_CONV_ID TEXT PRIMARY KEY,
                $COL_CONV_USERNAME TEXT,
                $COL_CONV_DISPLAY_NAME TEXT,
                $COL_CONV_AVATAR_URI TEXT,
                $COL_CONV_LAST_MESSAGE TEXT,
                $COL_CONV_LAST_MSG_TYPE INTEGER,
                $COL_CONV_LAST_MSG_TIME INTEGER,
                $COL_CONV_UNREAD INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createMsgTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID TEXT PRIMARY KEY,
                $COL_MSG_CONV_ID TEXT,
                $COL_MSG_TEXT TEXT,
                $COL_MSG_IS_SENT INTEGER,
                $COL_MSG_TYPE INTEGER,
                $COL_MSG_AUDIO_PATH TEXT,
                $COL_MSG_DURATION INTEGER,
                $COL_MSG_STATE INTEGER,
                $COL_MSG_TIMESTAMP INTEGER,
                $COL_MSG_REACTIONS TEXT,
                $COL_MSG_FILE_NAME TEXT,
                $COL_MSG_MIME_TYPE TEXT,
                $COL_MSG_FILE_SIZE INTEGER,
                $COL_MSG_LOCAL_PATH TEXT,
                $COL_MSG_DELIVERED_AT INTEGER DEFAULT 0,
                $COL_MSG_SEEN_AT INTEGER DEFAULT 0
            )
        """.trimIndent()

        db.execSQL(createConvTable)
        db.execSQL(createMsgTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Robust schema upgrade: explicitly add all columns introduced since V1.
        // We use try-catch to safely ignore errors if the column was already added by a previous partial upgrade.
        val addColumnIfNotExists = { table: String, column: String, type: String ->
            try {
                db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
            } catch (e: Exception) {
                // Column likely already exists, ignore
            }
        }
        
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_AUDIO_PATH, "TEXT")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_DURATION, "INTEGER DEFAULT 0")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_FILE_NAME, "TEXT")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_MIME_TYPE, "TEXT")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_FILE_SIZE, "INTEGER")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_LOCAL_PATH, "TEXT")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_DELIVERED_AT, "INTEGER DEFAULT 0")
        addColumnIfNotExists(TABLE_MESSAGES, COL_MSG_SEEN_AT, "INTEGER DEFAULT 0")
        
        addColumnIfNotExists(TABLE_CONVERSATIONS, COL_CONV_UNREAD, "INTEGER DEFAULT 0")
    }

    // --- Conversations ---

    fun insertOrUpdateConversation(
        peerUserId: String,
        peerUsername: String,
        peerDisplayName: String,
        peerAvatarUri: String,
        lastMessage: String,
        lastMessageType: Int,
        lastMessageTimestamp: Long,
        unreadIncrement: Int = 0,
        clearUnread: Boolean = false
    ) {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT $COL_CONV_UNREAD, $COL_CONV_USERNAME, $COL_CONV_DISPLAY_NAME, $COL_CONV_AVATAR_URI FROM $TABLE_CONVERSATIONS WHERE $COL_CONV_ID = ?", arrayOf(peerUserId))
        
        var currentUnread = 0
        var finalUsername = peerUsername
        var finalDisplayName = peerDisplayName
        var finalAvatarUri = peerAvatarUri

        if (cursor.moveToFirst()) {
            currentUnread = cursor.getInt(0)
            
            val dbUsername = cursor.getString(1)
            if (finalUsername.isEmpty() && !dbUsername.isNullOrEmpty()) finalUsername = dbUsername
            
            val dbDisplayName = cursor.getString(2)
            if (finalDisplayName.isEmpty() && !dbDisplayName.isNullOrEmpty()) finalDisplayName = dbDisplayName
            
            val dbAvatarUri = cursor.getString(3)
            if (finalAvatarUri.isEmpty() && !dbAvatarUri.isNullOrEmpty()) finalAvatarUri = dbAvatarUri
        }
        cursor.close()

        val newUnread = if (clearUnread) 0 else currentUnread + unreadIncrement

        val values = ContentValues().apply {
            put(COL_CONV_ID, peerUserId)
            put(COL_CONV_USERNAME, finalUsername)
            put(COL_CONV_DISPLAY_NAME, finalDisplayName)
            put(COL_CONV_AVATAR_URI, finalAvatarUri)
            put(COL_CONV_LAST_MESSAGE, lastMessage)
            put(COL_CONV_LAST_MSG_TYPE, lastMessageType)
            put(COL_CONV_LAST_MSG_TIME, lastMessageTimestamp)
            put(COL_CONV_UNREAD, newUnread)
        }

        db.insertWithOnConflict(TABLE_CONVERSATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateConversationProfile(
        peerUserId: String,
        peerUsername: String,
        peerDisplayName: String,
        peerAvatarUri: String
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CONV_USERNAME, peerUsername)
            put(COL_CONV_DISPLAY_NAME, peerDisplayName)
            put(COL_CONV_AVATAR_URI, peerAvatarUri)
        }
        val rowsAffected = db.update(TABLE_CONVERSATIONS, values, "$COL_CONV_ID = ?", arrayOf(peerUserId))
        if (rowsAffected == 0) {
            // If the conversation doesn't exist yet, we insert a placeholder with the profile
            val insertValues = ContentValues().apply {
                put(COL_CONV_ID, peerUserId)
                put(COL_CONV_USERNAME, peerUsername)
                put(COL_CONV_DISPLAY_NAME, peerDisplayName)
                put(COL_CONV_AVATAR_URI, peerAvatarUri)
                put(COL_CONV_UNREAD, 0)
            }
            db.insertWithOnConflict(TABLE_CONVERSATIONS, null, insertValues, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
    
    fun clearUnread(peerUserId: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_CONV_UNREAD, 0)
        }
        db.update(TABLE_CONVERSATIONS, values, "$COL_CONV_ID = ?", arrayOf(peerUserId))
    }

    fun getAllConversations(): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CONVERSATIONS ORDER BY $COL_CONV_LAST_MSG_TIME DESC", null)
        
        while (cursor.moveToNext()) {
            list.add(Conversation(
                peerUserId = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_ID)) ?: "",
                peerUsername = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_USERNAME)) ?: "",
                peerDisplayName = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_DISPLAY_NAME)) ?: "",
                peerAvatarUri = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_AVATAR_URI)) ?: "",
                lastMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_LAST_MESSAGE)) ?: "",
                lastMessageType = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CONV_LAST_MSG_TYPE)),
                lastMessageTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_LAST_MSG_TIME)),
                unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CONV_UNREAD))
            ))
        }
        cursor.close()
        return list
    }

    // --- Messages ---

    fun insertMessage(msg: ChatMessage) {
        val db = writableDatabase
        val reactionsJson = JSONArray(msg.reactions).toString()

        val values = ContentValues().apply {
            put(COL_MSG_ID, msg.id)
            put(COL_MSG_CONV_ID, msg.conversationId)
            put(COL_MSG_TEXT, msg.text)
            put(COL_MSG_IS_SENT, if (msg.isSent) 1 else 0)
            put(COL_MSG_TYPE, msg.type.ordinal)
            put(COL_MSG_AUDIO_PATH, msg.audioPath)
            put(COL_MSG_DURATION, msg.durationMs)
            put(COL_MSG_STATE, msg.state.ordinal)
            put(COL_MSG_TIMESTAMP, msg.timestamp)
            put(COL_MSG_REACTIONS, reactionsJson)
            put(COL_MSG_FILE_NAME, msg.fileName)
            put(COL_MSG_MIME_TYPE, msg.mimeType)
            put(COL_MSG_FILE_SIZE, msg.fileSize)
            put(COL_MSG_LOCAL_PATH, msg.localFilePath)
            put(COL_MSG_DELIVERED_AT, msg.deliveredAt)
            put(COL_MSG_SEEN_AT, msg.seenAt)
        }

        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateMessageState(msgId: String, state: MessageState) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_STATE, state.ordinal)
            if (state == MessageState.DELIVERED) {
                put(COL_MSG_DELIVERED_AT, System.currentTimeMillis())
            } else if (state == MessageState.SEEN) {
                put(COL_MSG_SEEN_AT, System.currentTimeMillis())
            }
        }
        db.update(TABLE_MESSAGES, values, "$COL_MSG_ID = ?", arrayOf(msgId))
    }

    fun addReaction(msgId: String, reaction: String) {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT $COL_MSG_REACTIONS FROM $TABLE_MESSAGES WHERE $COL_MSG_ID = ?", arrayOf(msgId))
        if (cursor.moveToFirst()) {
            val jsonStr = cursor.getString(0)
            val jsonArray = if (jsonStr.isNullOrEmpty()) JSONArray() else JSONArray(jsonStr)
            
            var exists = false
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getString(i) == reaction) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                jsonArray.put(reaction)
                val values = ContentValues().apply {
                    put(COL_MSG_REACTIONS, jsonArray.toString())
                }
                db.update(TABLE_MESSAGES, values, "$COL_MSG_ID = ?", arrayOf(msgId))
            }
        }
        cursor.close()
    }
    
    fun removeReaction(msgId: String, reaction: String) {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT $COL_MSG_REACTIONS FROM $TABLE_MESSAGES WHERE $COL_MSG_ID = ?", arrayOf(msgId))
        if (cursor.moveToFirst()) {
            val jsonStr = cursor.getString(0)
            if (!jsonStr.isNullOrEmpty()) {
                val jsonArray = JSONArray(jsonStr)
                val newArray = JSONArray()
                for (i in 0 until jsonArray.length()) {
                    if (jsonArray.getString(i) != reaction) {
                        newArray.put(jsonArray.getString(i))
                    }
                }
                val values = ContentValues().apply {
                    put(COL_MSG_REACTIONS, newArray.toString())
                }
                db.update(TABLE_MESSAGES, values, "$COL_MSG_ID = ?", arrayOf(msgId))
            }
        }
        cursor.close()
    }

    fun getMessagesForConversation(conversationId: String): List<ChatMessage> {
        android.util.Log.i("MeshCall_CrashTrace", "CHAT_QUERY_START")
        val list = mutableListOf<ChatMessage>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MESSAGES WHERE $COL_MSG_CONV_ID = ? ORDER BY $COL_MSG_TIMESTAMP ASC", arrayOf(conversationId))
        android.util.Log.i("MeshCall_CrashTrace", "CHAT_QUERY_EXECUTED cursor_count=${cursor.count}")
        
        while (cursor.moveToNext()) {
            val reactionsStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_REACTIONS))
            val reactionsList = mutableListOf<String>()
            if (!reactionsStr.isNullOrEmpty()) {
                val jsonArray = JSONArray(reactionsStr)
                for (i in 0 until jsonArray.length()) {
                    reactionsList.add(jsonArray.getString(i))
                }
            }

            list.add(ChatMessage(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
                conversationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONV_ID)),
                text = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_TEXT)),
                isSent = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_IS_SENT)) == 1,
                type = MessageType.values()[cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_TYPE))],
                audioPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_AUDIO_PATH)),
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_DURATION)),
                fileName = cursor.getColumnIndex(COL_MSG_FILE_NAME).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                mimeType = cursor.getColumnIndex(COL_MSG_MIME_TYPE).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                fileSize = cursor.getColumnIndex(COL_MSG_FILE_SIZE).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else null },
                localFilePath = cursor.getColumnIndex(COL_MSG_LOCAL_PATH).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                state = MessageState.values()[cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_STATE))],
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                reactions = reactionsList,
                deliveredAt = cursor.getColumnIndex(COL_MSG_DELIVERED_AT).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L },
                seenAt = cursor.getColumnIndex(COL_MSG_SEEN_AT).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L }
            ))
        }
        cursor.close()
        android.util.Log.i("MeshCall_CrashTrace", "CHAT_QUERY_END")
        return list
    }

    fun getAllCalls(): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MESSAGES WHERE $COL_MSG_TYPE = ? ORDER BY $COL_MSG_TIMESTAMP DESC", arrayOf(MessageType.CALL.ordinal.toString()))
        
        while (cursor.moveToNext()) {
            val reactionsStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_REACTIONS))
            val reactionsList = mutableListOf<String>()
            if (!reactionsStr.isNullOrEmpty()) {
                val jsonArray = JSONArray(reactionsStr)
                for (i in 0 until jsonArray.length()) {
                    reactionsList.add(jsonArray.getString(i))
                }
            }

            list.add(ChatMessage(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
                conversationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONV_ID)),
                text = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_TEXT)),
                isSent = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_IS_SENT)) == 1,
                type = MessageType.values()[cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_TYPE))],
                audioPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_AUDIO_PATH)),
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_DURATION)),
                fileName = cursor.getColumnIndex(COL_MSG_FILE_NAME).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                mimeType = cursor.getColumnIndex(COL_MSG_MIME_TYPE).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                fileSize = cursor.getColumnIndex(COL_MSG_FILE_SIZE).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else null },
                localFilePath = cursor.getColumnIndex(COL_MSG_LOCAL_PATH).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                state = MessageState.values()[cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_STATE))],
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                reactions = reactionsList,
                deliveredAt = cursor.getColumnIndex(COL_MSG_DELIVERED_AT).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L },
                seenAt = cursor.getColumnIndex(COL_MSG_SEEN_AT).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L }
            ))
        }
        cursor.close()
        return list
    }

    fun deleteMessage(msgId: String) {
        val db = writableDatabase
        db.delete(TABLE_MESSAGES, "$COL_MSG_ID = ?", arrayOf(msgId))
    }

    fun deleteConversation(conversationId: String) {
        val messages = getMessagesForConversation(conversationId)
        for (msg in messages) {
            msg.audioPath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {}
            }
            msg.localFilePath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {}
            }
        }
        
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_MESSAGES, "$COL_MSG_CONV_ID = ?", arrayOf(conversationId))
            db.delete(TABLE_CONVERSATIONS, "$COL_CONV_ID = ?", arrayOf(conversationId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getMessage(msgId: String): ChatMessage? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MESSAGES WHERE $COL_MSG_ID = ?", arrayOf(msgId))
        var msg: ChatMessage? = null
        if (cursor.moveToFirst()) {
            val reactionsStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_REACTIONS))
            val reactionsList = mutableListOf<String>()
            if (!reactionsStr.isNullOrEmpty()) {
                val jsonArray = JSONArray(reactionsStr)
                for (i in 0 until jsonArray.length()) {
                    reactionsList.add(jsonArray.getString(i))
                }
            }
            msg = ChatMessage(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
                conversationId = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONV_ID)),
                text = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_TEXT)),
                isSent = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_IS_SENT)) == 1,
                type = MessageType.values()[cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_TYPE))],
                audioPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_AUDIO_PATH)),
                durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_DURATION)),
                fileName = cursor.getColumnIndex(COL_MSG_FILE_NAME).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                mimeType = cursor.getColumnIndex(COL_MSG_MIME_TYPE).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                fileSize = cursor.getColumnIndex(COL_MSG_FILE_SIZE).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else null },
                localFilePath = cursor.getColumnIndex(COL_MSG_LOCAL_PATH).let { if (it >= 0 && !cursor.isNull(it)) cursor.getString(it) else null },
                state = MessageState.values()[cursor.getInt(cursor.getColumnIndexOrThrow(COL_MSG_STATE))],
                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                reactions = reactionsList,
                deliveredAt = cursor.getColumnIndex(COL_MSG_DELIVERED_AT).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L },
                seenAt = cursor.getColumnIndex(COL_MSG_SEEN_AT).let { if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else 0L }
            )
        }
        cursor.close()
        return msg
    }
    
    fun getMessageCountWithFile(localPath: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_MESSAGES WHERE $COL_MSG_LOCAL_PATH = ? OR $COL_MSG_AUDIO_PATH = ?", arrayOf(localPath, localPath))
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }
}
