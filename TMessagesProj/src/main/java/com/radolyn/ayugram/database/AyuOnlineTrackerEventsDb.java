package com.radolyn.ayugram.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

public class AyuOnlineTrackerEventsDb extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final AyuOnlineTrackerEventsDb[] instances = new AyuOnlineTrackerEventsDb[UserConfig.MAX_ACCOUNT_COUNT];

    public static class TrackerEvent {
        public long id;
        public long userId;
        public int eventType; // 1 = online, 0 = offline, -1..-4 = approximate
        public long timestamp;
        public long durationMs;
    }

    public static AyuOnlineTrackerEventsDb getInstance(int account) {
        if (account < 0 || account >= instances.length) {
            account = 0;
        }
        synchronized (AyuOnlineTrackerEventsDb.class) {
            if (instances[account] == null) {
                instances[account] = new AyuOnlineTrackerEventsDb(ApplicationLoader.applicationContext, account);
            }
            return instances[account];
        }
    }

    public AyuOnlineTrackerEventsDb(Context context, int account) {
        super(context, "ayutracker_events_" + account + ".db", null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `TrackerEvent` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`userId` INTEGER NOT NULL, " +
                    "`eventType` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_TrackerEvent_time` ON `TrackerEvent` (`timestamp` DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_TrackerEvent_user` ON `TrackerEvent` (`userId`)");
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized void insertEvent(long userId, int eventType, long timestamp, long durationMs) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("userId", userId);
            cv.put("eventType", eventType);
            cv.put("timestamp", timestamp);
            cv.put("durationMs", durationMs);
            db.insert("TrackerEvent", null, cv);

            // Keep max 1000 events to prevent DB bloating
            db.execSQL("DELETE FROM TrackerEvent WHERE id NOT IN (SELECT id FROM TrackerEvent ORDER BY timestamp DESC LIMIT 1000)");
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public synchronized ArrayList<TrackerEvent> getEvents(long filterUserId, int limit, int offset) {
        ArrayList<TrackerEvent> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            String query;
            String[] args;
            if (filterUserId != 0) {
                query = "SELECT id, userId, eventType, timestamp, durationMs FROM TrackerEvent WHERE userId = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
                args = new String[]{String.valueOf(filterUserId), String.valueOf(limit), String.valueOf(offset)};
            } else {
                query = "SELECT id, userId, eventType, timestamp, durationMs FROM TrackerEvent ORDER BY timestamp DESC LIMIT ? OFFSET ?";
                args = new String[]{String.valueOf(limit), String.valueOf(offset)};
            }
            cursor = db.rawQuery(query, args);
            while (cursor.moveToNext()) {
                TrackerEvent ev = new TrackerEvent();
                ev.id = cursor.getLong(0);
                ev.userId = cursor.getLong(1);
                ev.eventType = cursor.getInt(2);
                ev.timestamp = cursor.getLong(3);
                ev.durationMs = cursor.getLong(4);
                list.add(ev);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public synchronized int getEventsCount() {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM TrackerEvent", null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    public synchronized void clearEvents(long filterUserId) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (filterUserId != 0) {
                db.delete("TrackerEvent", "userId = ?", new String[]{String.valueOf(filterUserId)});
            } else {
                db.delete("TrackerEvent", null, null);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
