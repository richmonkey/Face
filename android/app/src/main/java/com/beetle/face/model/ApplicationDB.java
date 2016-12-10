package com.beetle.face.model;

import com.google.code.p.leveldb.LevelDB;

/**
 * Created by houxh on 16/2/20.
 */
public class ApplicationDB {
    private static ApplicationDB instance;
    public static ApplicationDB getInstance() {
        if (instance == null) {
            instance = new ApplicationDB();
            instance.load();
        }
        return instance;
    }


    public boolean firstRun = true;

    private void load() {
        LevelDB db = LevelDB.getDefaultDB();
        try {
            firstRun = (db.getLong("first_run") != 0 ? true : false);
        } catch(Exception e) {
            firstRun = true;
        }
    }

    public void save() {
        LevelDB db = LevelDB.getDefaultDB();
        try {
            db.setLong("first_run", firstRun ? 1 : 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
