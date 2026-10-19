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
import com.radolyn.ayugram.database.AyuOnlineTrackerEventsDb;
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
import org.telegram.messenger.UserObject;
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
    private final ArrayList<EventsChangeListener> eventListeners = new ArrayList<>();
    private volatile boolean initialSyncDone = false;

    public interface TrackedUsersChangeListener {
        void onTrackedUsersChanged();
    }

    public interface EventsChangeListener {
        void onEventsChanged();
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
        if (isEnabled()) {
            startPollingIfNeeded();
        }
        AndroidUtilities.runOnUIThread(() -> initialSyncDone = true, 5000);
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

    private boolean pollingScheduled = false;
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isEnabled()) {
                pollingScheduled = false;
                return;
            }
            if (!ApplicationLoader.mainInterfacePaused) {
                checkTrackedUsersStatus();
            }
            AndroidUtilities.runOnUIThread(this, 45000);
        }
    };

    public void startPollingIfNeeded() {
        if (!isEnabled()) return;
        if (!pollingScheduled) {
            pollingScheduled = true;
            AndroidUtilities.runOnUIThread(pollRunnable, 20000);
        }
    }

    public void checkTrackedUsersStatus() {
        if (!isEnabled()) return;
        if (getTargetMode() == TARGET_ALL_CONTACTS) {
            getContactsController().reloadContactsStatusesMaybe(true);
        } else {
            ArrayList<Long> ids = getTrackedUserIds();
            boolean needContactsReload = false;
            for (Long id : ids) {
                if (id != null && id != 0) {
                    if (ContactsController.getInstance(currentAccount).isContact(id)) {
                        needContactsReload = true;
                    } else {
                        getMessagesController().reloadUser(id);
                    }
                }
            }
            if (needContactsReload) {
                getContactsController().reloadContactsStatusesMaybe(true);
            }
        }
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
        if (value) {
            startPollingIfNeeded();
            checkTrackedUsersStatus();
        }
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
            TLRPC.User u = getMessagesController().getUser(userId);
            if (u != null && u.status != null) {
                synchronized (statusRecords) {
                    StatusRecord r = new StatusRecord();
                    r.statusCode = getStatusCode(u.status);
                    if (r.statusCode == 1) {
                        r.onlineSince = System.currentTimeMillis();
                    } else if (r.statusCode == 0) {
                        r.lastSeenDate = u.status.expires;
                    }
                    statusRecords.put(userId, r);
                }
            }
            getMessagesController().reloadUser(userId);
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
            synchronized (statusRecords) {
                statusRecords.remove(userId);
            }
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

    public void addEventsListener(EventsChangeListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    public void removeEventsListener(EventsChangeListener listener) {
        eventListeners.remove(listener);
    }

    public void notifyEventsUpdated() {
        AndroidUtilities.runOnUIThread(() -> {
            for (EventsChangeListener l : new ArrayList<>(eventListeners)) {
                l.onEventsChanged();
            }
        });
    }

    public AyuOnlineTrackerEventsDb getEventsDb() {
        return AyuOnlineTrackerEventsDb.getInstance(currentAccount);
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
        if (update != null && update.status != null) {
            processStatusUpdate(update.user_id, update.status);
        }
    }

    public void onUserActivityDetected(long userId) {
        if (!isEnabled() || userId == 0 || userId == getUserConfig().getClientUserId()) {
            return;
        }
        if (!isUserTracked(userId)) {
            return;
        }
        TLRPC.TL_userStatusOnline onlineStatus = new TLRPC.TL_userStatusOnline();
        onlineStatus.expires = getConnectionsManager().getCurrentTime() + 60;
        processStatusUpdate(userId, onlineStatus);
    }

    public void onUserPrivacyOffline(long userId) {
        if (!isEnabled() || userId == 0 || userId == getUserConfig().getClientUserId()) {
            return;
        }
        if (!isUserTracked(userId)) {
            return;
        }
        TLRPC.TL_userStatusOffline offlineStatus = new TLRPC.TL_userStatusOffline();
        offlineStatus.expires = getConnectionsManager().getCurrentTime();
        processStatusUpdate(userId, offlineStatus);
    }

    public void onInitialContactsLoaded() {
        initialSyncDone = true;
    }

    public void processStatusUpdate(long userId, TLRPC.UserStatus newStatus) {
        if (!isEnabled() || newStatus == null || userId == 0 || userId == getUserConfig().getClientUserId()) {
            return;
        }
        if (!isUserTracked(userId)) {
            return;
        }

        TLRPC.User user = getMessagesController().getUser(userId);
        if (user == null || user.bot) {
            return;
        }

        int newCode = getStatusCode(newStatus);
        long now = System.currentTimeMillis();
        boolean shouldNotifyOnline = false;
        boolean shouldNotifyOffline = false;
        long offlineDurationMs = 0;
        int approximateCode = 0;

        synchronized (statusRecords) {
            StatusRecord record = statusRecords.get(userId);
            if (record == null) {
                record = new StatusRecord();
                int oldCode = (user.status != null && user.status != newStatus) ? getStatusCode(user.status) : -999;
                record.statusCode = newCode;
                if (newCode == 1) {
                    record.onlineSince = now;
                } else if (newCode == 0) {
                    record.lastSeenDate = newStatus.expires;
                }
                statusRecords.put(userId, record);

                if (initialSyncDone) {
                    if (newCode == 1 && oldCode != 1) {
                        shouldNotifyOnline = true;
                    } else if (newCode == 0 && oldCode == 1) {
                        shouldNotifyOffline = true;
                    }
                }
            } else {
                int oldCode = record.statusCode;
                if (oldCode == newCode) {
                    return;
                }
                record.statusCode = newCode;

                if (initialSyncDone) {
                    if (newCode == 1) {
                        record.onlineSince = now;
                        shouldNotifyOnline = true;
                    } else if (newCode == 0) {
                        offlineDurationMs = (record.onlineSince > 0) ? (now - record.onlineSince) : 0;
                        record.onlineSince = 0;
                        record.lastSeenDate = newStatus.expires;
                        shouldNotifyOffline = true;
                    } else if (newCode < 0) {
                        if (oldCode == 1) {
                            offlineDurationMs = (record.onlineSince > 0) ? (now - record.onlineSince) : 0;
                            record.onlineSince = 0;
                            shouldNotifyOffline = true;
                        } else if (oldCode != -999) {
                            approximateCode = newCode;
                        }
                    }
                } else {
                    if (newCode == 1) {
                        record.onlineSince = now;
                    } else if (newCode == 0) {
                        record.lastSeenDate = newStatus.expires;
                    }
                }
            }
        }

        if (shouldNotifyOnline) {
            getEventsDb().insertEvent(userId, 1, now, 0);
            notifyEventsUpdated();
            if (isNotifyOnline()) {
                String userName = UserObject.getUserName(user);
                String text = LocaleController.formatString("AyuOnlineTrackerNotificationOnline", R.string.AyuOnlineTrackerNotificationOnline, userName);
                sendNotification(user, text, true);
            }
        } else if (shouldNotifyOffline) {
            getEventsDb().insertEvent(userId, 0, now, offlineDurationMs);
            notifyEventsUpdated();
            if (isNotifyOffline()) {
                String userName = UserObject.getUserName(user);
                String text;
                if (isNotifyDuration() && offlineDurationMs >= 3000) {
                    String durationStr = LocaleController.formatDuration((int) (offlineDurationMs / 1000));
                    text = LocaleController.formatString("AyuOnlineTrackerNotificationOfflineWithDuration", R.string.AyuOnlineTrackerNotificationOfflineWithDuration, userName, durationStr);
                } else {
                    text = LocaleController.formatString("AyuOnlineTrackerNotificationOffline", R.string.AyuOnlineTrackerNotificationOffline, userName);
                }
                sendNotification(user, text, false);
            }
        } else if (approximateCode < 0) {
            getEventsDb().insertEvent(userId, approximateCode, now, 0);
            notifyEventsUpdated();
            if (isNotifyApproximate()) {
                String statusName = getApproximateStatusName(approximateCode);
                if (statusName != null) {
                    String userName = UserObject.getUserName(user);
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
                String userName = UserObject.getUserName(user);
                // 1. In-app bulletin if foreground
                if (!ApplicationLoader.mainInterfacePaused) {
                    BaseFragment fragment = LaunchActivity.getLastFragment();
                    if (fragment != null && fragment.getParentActivity() != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(
                            isOnline ? R.drawable.msg_status_set_solar : R.drawable.msg_notifications_solar,
                            userName,
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
                        .setContentTitle(userName)
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
