package com.beetle.face.model;

import java.util.ArrayList;

/**
 * Created by houxh on 14-12-31.
 */
public class HistoryDB {

    private static HistoryDB instance = new HistoryDB();
    public static HistoryDB getInstance() {
        return instance;
    }

    public boolean addHistory(History h) {
        return true;
    }

    public boolean removeHistory(long hid) {
        return true;
    }

    public boolean clear() {
        return true;
    }

    ArrayList<History> loadHistoryDB() {
        return null;
    }
}
