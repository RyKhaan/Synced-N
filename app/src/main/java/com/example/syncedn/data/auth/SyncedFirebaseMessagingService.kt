package com.example.syncedn.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service that handles background push notifications
 * when the app is killed or running, token refreshes, and team topic subscriptions.
 */
class SyncedFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token generated: $token")
        val prefs = getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        // Sync token to Firestore /users/{uid}
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: prefs.getString(SessionManager.KEY_USER_ID, null)
        if (!uid.isNullOrBlank()) {
            saveTokenToFirestore(uid, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val prefs = getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Check for notification payload
        val notifTitle = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Synced N Ministry Alert"
        val notifBody = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: ""

        val type = remoteMessage.data["type"].orEmpty()
        val isChat = type.equals("chat", ignoreCase = true)

        if (notifBody.isNotBlank()) {
            SyncedMessagingService.dispatchSystemNotification(
                context = applicationContext,
                preferences = prefs,
                title = notifTitle,
                body = notifBody,
                isChatMessage = isChat
            )
        }
    }

    companion object {
        private const val TAG = "SyncedFCM"
        const val KEY_FCM_TOKEN = "fcmToken"

        /**
         * Persists the FCM token to Cloud Firestore under /users/{userId}.
         */
        fun saveTokenToFirestore(uid: String, token: String) {
            if (uid.isBlank() || token.isBlank()) return
            try {
                FirebaseFirestore.getInstance().collection("users")
                    .document(uid)
                    .set(mapOf("fcmToken" to token), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully synced FCM token to Firestore for $uid")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to sync FCM token: ${e.message}")
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception syncing FCM token", e)
            }
        }

        /**
         * Fetches the active FCM device token and saves it in SharedPreferences and Firestore.
         */
        fun syncToken(context: Context, preferences: SharedPreferences) {
            try {
                SyncedMessagingService.ensureChannel(context)
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        preferences.edit().putString(KEY_FCM_TOKEN, token).apply()
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                            ?: preferences.getString(SessionManager.KEY_USER_ID, null)
                        if (!uid.isNullOrBlank()) {
                            saveTokenToFirestore(uid, token)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not fetch FCM token", e)
            }
        }

        /**
         * Subscribes the current device to the team topic for team-wide alerts
         * (services, urgent announcements, schedule changes).
         */
        fun subscribeToTeam(teamCode: String) {
            val code = teamCode.trim().uppercase()
            if (code.isBlank()) return
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("team_$code")
                    .addOnSuccessListener {
                        Log.d(TAG, "Subscribed to FCM topic: team_$code")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to subscribe to topic team_$code: ${e.message}")
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception subscribing to team topic", e)
            }
        }

        /**
         * Unsubscribes the device from a team topic (e.g. when leaving team).
         */
        fun unsubscribeFromTeam(teamCode: String) {
            val code = teamCode.trim().uppercase()
            if (code.isBlank()) return
            try {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("team_$code")
                    .addOnSuccessListener {
                        Log.d(TAG, "Unsubscribed from FCM topic: team_$code")
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception unsubscribing from team topic", e)
            }
        }
    }
}
