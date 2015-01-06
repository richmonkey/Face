package com.beetle.face.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by houxh on 14-12-31.
 */
public class HistoryDB {
    private static final String TAG = "face";
    private static final String DATABASE_NAME = "voip.db3";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE = "CREATE TABLE IF NOT EXISTS history(hid INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "peer_uid INT, flag INT, create_timestamp INT, begin_timestamp INT, end_timestamp INT)";
    private static final String TABLE_NAME = "history";


    private static HistoryDB instance;
    public static HistoryDB getInstance() {
        return instance;
    }

    public static void initDatabase(Context context) {
        DatabaseHelper OpenHelper = new DatabaseHelper(context);
        SQLiteDatabase db = OpenHelper.getWritableDatabase();
        instance = new HistoryDB(db);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqlitedatabase, int oldVersion, int newVersion) {
            Log.d(TAG, "update database");

        }
    }

    private SQLiteDatabase db;
    public HistoryDB(SQLiteDatabase db) {
        this.db = db;
    }

    public boolean addHistory(History h) {
        ContentValues values = new ContentValues();
        values.put("peer_uid", h.peerUID);
        values.put("flag", h.flag);
        values.put("create_timestamp", h.createTimestamp);
        values.put("begin_timestamp", h.beginTimestamp);
        values.put("end_timestamp", h.endTimestamp);
        long hid = db.insert(TABLE_NAME, null, values);
        if (hid == -1) {
            return  false;
        }
        h.hid = hid;
        return true;
    }

    public boolean removeHistory(long hid) {
        String[] whereArgs = new String[] {
            String.valueOf(hid)
        };
        db.delete(TABLE_NAME, "hid=?", whereArgs);
        return true;
    }

    public boolean clear() {
        db.delete(TABLE_NAME, null, null);
        return true;
    }

    public ArrayList<History> loadHistoryDB() {
        ArrayList<History> histories = new ArrayList<>();
        Cursor cursor = null;
        try {
            String sql = "select hid, peer_uid, flag, create_timestamp, begin_timestamp, end_timestamp from history";
            cursor = db.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                History h = new History();
                h.hid = cursor.getLong(cursor.getColumnIndex("hid"));
                h.peerUID = cursor.getLong(cursor.getColumnIndex("peer_uid"));
                h.flag = cursor.getInt(cursor.getColumnIndex("flag"));
                h.createTimestamp = cursor.getLong(cursor.getColumnIndex("create_timestamp"));
                h.beginTimestamp = cursor.getLong(cursor.getColumnIndex("begin_timestamp"));
                h.endTimestamp = cursor.getLong(cursor.getColumnIndex("end_timestamp"));
                histories.add(h);
            }
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        } finally {
            if (null != cursor) {
                cursor.close();
            }
        }
        return histories;
    }
}
