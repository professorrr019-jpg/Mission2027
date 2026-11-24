package com.mission.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Mutes distracting apps (kept in Settings > Focus & distractions) and keeps a short list of important
 * study notifications for the home card. Muted items are saved, not deleted, so nothing is lost.
 */
class Notifs : NotificationListenerService() {
    companion object {
        val DEFAULT_BLOCK =
            listOf("com.instagram.android", "com.facebook.katana", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage", "com.amazon.mShop.android.shopping", "com.flipkart.android", "com.zhiliaoapp.musically", "in.mohalla.sharechat")
        val DEFAULT_KEEP =
            listOf(
                "com.google.android.gm",
                "com.google.android.apps.messaging",
                "com.whatsapp",
                "org.telegram.messenger",
                "com.google.android.calendar",
                "com.google.android.apps.classroom",
            )

        fun block(): Set<String> = if (Store.has("block_apps")) Store.stringList("block_apps").toSet() else DEFAULT_BLOCK.toSet()

        fun keep(): Set<String> = if (Store.has("keep_apps")) Store.stringList("keep_apps").toSet() else DEFAULT_KEEP.toSet()

        fun postDigest(c: android.content.Context) {
            if (!FeatureRegistry.enabled("x20_notification_digest")) return
            if (!c.nm().areNotificationsEnabled() || c.nm().getNotificationChannel(CH_DIGEST)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return
            val pending = Store.rows("important").filterNot { it.optBoolean("digest_sent", false) }
            if (pending.isEmpty()) return
            val latest = pending.takeLast(8)
            val summary = latest.joinToString("\n") { "- " + it.optString("title").ifBlank { "Notification" }.take(72) }
            val open = android.content.Intent(c, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = android.app.PendingIntent.getActivity(c, 7400, open, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            c.nm().notify(
                7400,
                android.app.Notification.Builder(c, CH_DIGEST)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("${pending.size} important notifications")
                    .setContentText("Your notification digest is ready")
                    .setStyle(android.app.Notification.BigTextStyle().bigText(summary))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
                    .build(),
            )
            pending.forEach { row ->
                val id = row.optString("id")
                if (id.isNotBlank()) Store.update("important", id) { it.put("digest_sent", true) }
            }
        }
    }

    override fun onNotificationPosted(s: StatusBarNotification) {
        try {
            Boot.ensure(this)
            val e = s.notification.extras
            val t = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val x = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val muteAlways = Store.b("mute_distractions", true)
            if (s.packageName in block() && s.isClearable && (muteAlways || Focus.active() || TimerState.running())) {
                val rows = Store.rows("muted")
                if (rows.size > 80) Store.replace("muted", rows.takeLast(60))
                Store.add("muted", obj("pkg" to s.packageName, "title" to t, "t" to System.currentTimeMillis()))
                cancelNotification(s.key)
                return
            }
            if (s.packageName !in keep() || (t.isEmpty() && x.isEmpty())) return
            val rows = Store.rows("important")
            val existing = rows.lastOrNull { it.optString("key") == s.key }
            if (existing != null) {
                Store.update("important", existing.optString("id")) {
                    it.put("pkg", s.packageName)
                    it.put("title", t)
                    it.put("text", x.take(140))
                    it.put("t", System.currentTimeMillis())
                    it.put("digest_sent", false)
                }
            } else {
                if (rows.size >= 40) Store.replace("important", rows.takeLast(20))
                Store.add("important", obj("key" to s.key, "pkg" to s.packageName, "title" to t, "text" to x.take(140), "t" to System.currentTimeMillis(), "digest_sent" to false))
            }
        } catch (ex: Exception) {
            Log.e("notifs", ex)
        }
    }

    fun postDigest(c: android.content.Context) {
        if (!FeatureRegistry.enabled("x20_notification_digest")) return
        if (!c.nm().areNotificationsEnabled() || c.nm().getNotificationChannel(CH_DIGEST)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return
        val pending = Store.rows("important").filterNot { it.optBoolean("digest_sent", false) }
        if (pending.isEmpty()) return
        val latest = pending.takeLast(8)
        val summary = latest.joinToString("\n") { "- " + it.optString("title").ifBlank { "Notification" }.take(72) }
        val open = android.content.Intent(c, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = android.app.PendingIntent.getActivity(c, 7400, open, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        c.nm().notify(
            7400,
            android.app.Notification.Builder(c, CH_DIGEST)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${pending.size} important notifications")
                .setContentText("Your notification digest is ready")
                .setStyle(android.app.Notification.BigTextStyle().bigText(summary))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
                .build(),
        )
        pending.forEach { row ->
            val id = row.optString("id")
            if (id.isNotBlank()) Store.update("important", id) { it.put("digest_sent", true) }
        }
    }
}
