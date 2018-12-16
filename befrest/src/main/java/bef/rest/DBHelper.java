package bef.rest;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "notes_db";


    public static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS " + "LogCat" + "("
                    + "ID" + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "Log" + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                    + ")";



    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {

        // create notes table
      db.execSQL(CREATE_TABLE);
    }

    public long insertNote(String note) {

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("Log", note);
        long id = db.insert("LogCat", null, values);
        db.close();
        return id;
    }


    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + "LogCat");

        // Create tables again
        onCreate(db);
    }
}