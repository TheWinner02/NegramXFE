package com.radolyn.ayugram.controllers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import androidx.collection.LongSparseArray;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

public class AyuOnlineTrackerController extends BaseController {

    public static final int TARGET_ALL_CONTACTS = 0;
    public static final int TARGET_SELECTED_ONLY = 1;

    private static final AyuOnlineTrackerController[] Instance = new AyuOnlineTrackerController[UserConfig.MAX_ACCOUNT_COUNT];

    private final SharedPreferences preferences;
    private final Set<Long> trackedUserIds = new HashSet<>();
    private final LongSparseArray<StatusRecord> statusRecords = new LongSparseArray<>();
    private final ArrayList<TrackedUsersChangeListener> listeners = new ArrayList<>();

    public interface TrackedUsersChangeListener {
        void onTrackedUsersChanged();
    }

    private static class StatusRecord {
        int statusCode = -999;
        long onlineSince = 0;
        int lastSeenDate = 0;
    }

    public AyuOnlineTrackerController(int account) {
        super(account);
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("ayutracker_" + account, Context.MODE_PRIVATE);
        loadTrackedUsers();
    }

    public static AyuOnlineTrackerController getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    public static AyuOnlineTrackerController getInstance(int account) {
        AyuOnlineTrackerController controller = Instance[account];
        if (controller == null) {
            synchronized (AyuOnlineTrackerController.class) {
                controller = Instance[account];
                if (controller == null) {
                    controller = new AyuOnlineTrackerController(account);
                    Instance[account] = controller;
                }
            }
        }
        return controller;
    }

    private void loadTrackedUsers() {
        synchronized (trackedUserIds) {
            trackedUserIds.clear();
            Set<String> set = preferences.getStringSet("tracked_users", null);
            if (set != null) {
                for (String s : set) {
                    try {
                        trackedUserIds.add(Long.parseLong(s));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void saveTrackedUsers() {
        synchronized (trackedUserIds) {
            Set<String> set = new HashSet<>();
            for (Long id : trackedUserIds) {
                set.add(String.valueOf(id));
            }
            preferences.edit().putStringSet("tracked_users", set).apply();
        }
    }

    public boolean isEnabled() {
        return preferences.getBoolean("enabled", false);
    }

    public void setEnabled(boolean value) {
        preferences.edit().putBoolean("enabled", value).apply();
    }

    public boolean isNotifyOnline() {
        return preferences.getBoolean("notify_online", true);
    }

    public void setNotifyOnline(boolean value) {
        preferences.edit().putBoolean("notify_online", value).apply();
    }

    public boolean isNotifyOffline() {
        return preferences.getBoolean("notify_offline", true);
    }

    public void setNotifyOffline(boolean value) {
        preferences.edit().putBoolean("notify_offline", value).apply();
    }

    public boolean isNotifyDuration() {
        return preferences.getBoolean("notify_duration", true);
    }

    public void setNotifyDuration(boolean value) {
        preferences.edit().putBoolean("notify_duration", value).apply();
    }

    public boolean isNotifyApproximate() {
        return preferences.getBoolean("notify_approximate", true);
    }

    public void setNotifyApproximate(boolean value) {
        preferences.edit().putBoolean("notify_approximate", value).apply();
    }

    public int getTargetMode() {
        return preferences.getInt("target_mode", TARGET_ALL_CONTACTS);
    }

    public void setTargetMode(int mode) {
        preferences.edit().putInt("target_mode", mode).apply();
    }

    public ArrayList<Long> getTrackedUserIds() {
        synchronized (trackedUserIds) {
            return new ArrayList<>(trackedUserIds);
        }
    }

    public boolean isUserTracked(long userId) {
        if (getTargetMode() == TARGET_ALL_CONTACTS) {
            return ContactsController.getInstance(currentAccount).isContact(userId) || isUserExplicitlyTracked(userId);
        } else {
            return isUserExplicitlyTracked(userId);
        }
    }

    public boolean isUserExplicitlyTracked(long userId) {
        synchronized (trackedUserIds) {
            return trackedUserIds.contains(userId);
        }
    }

    public boolean addTrackedUser(long userId) {
        boolean added;
        synchronized (trackedUserIds) {
            added = trackedUserIds.add(userId);
            if (added) {
                saveTrackedUsers();
            }
        }
        if (added) {
            notifyListeners();
        }
        return added;
    }

    public void removeTrackedUser(long userId) {
        boolean removed;
        synchronized (trackedUserIds) {
            removed = trackedUserIds.remove(userId);
            if (removed) {
                saveTrackedUsers();
            }
        }
        if (removed) {
            notifyListeners();
        }
    }

    public void addListener(TrackedUsersChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(TrackedUsersChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        AndroidUtilities.runOnUIThread(() -> {
            for (TrackedUsersChangeListener l : new ArrayList<>(listeners)) {
                l.onTrackedUsersChanged();
            }
        });
    }

    private int getStatusCode(TLRPC.UserStatus status) {
        if (status instanceof TLRPC.TL_userStatusOnline) {
            return 1;
        } else if (status instanceof TLRPC.TL_userStatusOffline) {
            return 0;
        } else if (status instanceof TLRPC.TL_userStatusRecently) {
            return -1;
        } else if (status instanceof TLRPC.TL_userStatusLastWeek) {
            return -2;
        } else if (status instanceof TLRPC.TL_userStatusLastMonth) {
            return -3;
        } else if (status instanceof TLRPC.TL_userStatusEmpty) {
            return -4;
        }
        return -999;
    }

    public void processUpdate(TL_update.TL_updateUserStatus update) {
        if (!isEnabled() || update == null || update.status == null) {
            return;
        }
        long userId = update.user_id;
        if (userId == getUserConfig().getClientUserId()) {
            return;
        }
        if (!isUserTracked(userId)) {
            return;
        }

        TLRPC.User user = getMessagesController().getUser(userId);
        if (user == null || user.bot) {
            return;
        }

        StatusRecord record = statusRecords.get(userId);
        int newCode = getStatusCode(update.status);
        if (record == null) {
            record = new StatusRecord();
            record.statusCode = newCode;
            if (newCode == 1) {
                record.onlineSince = System.currentTimeMillis();
            } else if (newCode == 0) {
                record.lastSeenDate = update.status.expires;
            }
            statusRecords.put(userId, record);
            return;
        }

        int oldCode = record.statusCode;
        if (oldCode == newCode) {
            return;
        }

        record.statusCode = newCode;
        long now = System.currentTimeMillis();
        String userName = ContactsController.formatName(user.first_name, user.last_name);

        if (newCode == 1) { // Became ONLINE
            record.onlineSince = now;
            if (isNotifyOnline()) {
                String text = LocaleController.formatString("AyuOnlineTrackerNotificationOnline", R.string.AyuOnlineTrackerNotificationOnline, userName);
                sendNotification(user, text, true);
            }
        } else if (newCode == 0) { // Became OFFLINE
            long durationMs = (record.onlineSince > 0) ? (now - record.onlineSince) : 0;
            record.onlineSince = 0;
            record.lastSeenDate = update.status.expires;
            if (isNotifyOffline()) {
                String text;
                if (isNotifyDuration() && durationMs >= 3000) {
                    String durationStr = LocaleController.formatDuration((int) (durationMs / 1000));
                    text = LocaleController.formatString("AyuOnlineTrackerNotificationOfflineWithDuration", R.string.AyuOnlineTrackerNotificationOfflineWithDuration, userName, durationStr);
                } else {
                    text = LocaleController.formatString("AyuOnlineTrackerNotificationOffline", R.string.AyuOnlineTrackerNotificationOffline, userName);
                }
                sendNotification(user, text, false);
            }
        } else if (newCode < 0) { // Approximate status
            if (oldCode == 1) {
                long durationMs = (record.onlineSince > 0) ? (now - record.onlineSince) : 0;
                record.onlineSince = 0;
                if (isNotifyOffline()) {
                    String text;
                    if (isNotifyDuration() && durationMs >= 3000) {
                        String durationStr = LocaleController.formatDuration((int) (durationMs / 1000));
                        text = LocaleController.formatString("AyuOnlineTrackerNotificationOfflineWithDuration", R.string.AyuOnlineTrackerNotificationOfflineWithDuration, userName, durationStr);
                    } else {
                        text = LocaleController.formatString("AyuOnlineTrackerNotificationOffline", R.string.AyuOnlineTrackerNotificationOffline, userName);
                    }
                    sendNotification(user, text, false);
                }
            } else if (isNotifyApproximate() && oldCode != -999) {
                String statusName = getApproximateStatusName(newCode);
                if (statusName != null) {
                    String text = LocaleController.formatString("AyuOnlineTrackerNotificationApproximate", R.string.AyuOnlineTrackerNotificationApproximate, userName, statusName);
                    sendNotification(user, text, false);
                }
            }
        }
    }

    private String getApproximateStatusName(int code) {
        switch (code) {
            case -1:
                return LocaleController.getString("Lately", R.string.Lately);
            case -2:
                return LocaleController.getString("WithinAWeek", R.string.WithinAWeek);
            case -3:
                return LocaleController.getString("WithinAMonth", R.string.WithinAMonth);
            case -4:
                return LocaleController.getString("ALongTimeAgo", R.string.ALongTimeAgo);
            default:
                return null;
        }
    }

    private void sendNotification(TLRPC.User user, String text, boolean isOnline) {
        if (user == null || TextUtils.isEmpty(text)) return;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                // 1. In-app bulletin if foreground
                if (!ApplicationLoader.mainInterfacePaused) {
                    BaseFragment fragment = LaunchActivity.getLastFragment();
                    if (fragment != null && fragment.getParentActivity() != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(
                            isOnline ? R.drawable.msg_status_set_solar : R.drawable.msg_notifications_solar,
                            ContactsController.formatName(user.first_name, user.last_name),
                            text
                        ).show();
                    }
                }

                // 2. System notification
                NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    String channelId = NotificationsController.OTHER_NOTIFICATIONS_CHANNEL;
                    if (channelId == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        channelId = "ayutracker_channel";
                        NotificationChannel channel = new NotificationChannel(channelId, "Online Tracker", NotificationManager.IMPORTANCE_DEFAULT);
                        nm.createNotificationChannel(channel);
                    }

                    Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
                    intent.setAction("com.tmessages.openchat" + Math.random());
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra("userId", user.id);
                    PendingIntent contentIntent = PendingIntent.getActivity(
                        ApplicationLoader.applicationContext,
                        (int) (user.id & 0x7FFFFFFF),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, channelId != null ? channelId : "Other")
                        .setContentTitle(ContactsController.formatName(user.first_name, user.last_name))
                        .setContentText(text)
                        .setSmallIcon(R.drawable.notification)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(contentIntent);

                    nm.notify("ayutracker", (int) (user.id & 0x7FFFFFFF), builder.build());
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }
}
