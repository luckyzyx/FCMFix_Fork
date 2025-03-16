package com.kooritea.fcmfix.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri

class ContentProviderHelper(context: Context, uri: String) {
    var contentResolver: ContentResolver = context.contentResolver
    private val cursor = contentResolver.query(
        uri.toUri(), null, "all", null, null
    )
    var useDefaultValue: Boolean = false

    @SuppressLint("Range")
    fun getLong(selection: String, defaultValue: Long): Long {
        if (useDefaultValue || cursor == null || cursor.count == 0) {
            return defaultValue
        }
        cursor.moveToFirst()
        do {
            if (selection == cursor.getString(cursor.getColumnIndex("key"))) {
                return cursor.getLong(cursor.getColumnIndex("value"))
            }
        } while (cursor.moveToNext())
        return defaultValue
    }

    @SuppressLint("Range")
    fun getString(selection: String, defaultValue: String): String {
        if (useDefaultValue || cursor == null || cursor.count == 0) {
            return defaultValue
        }
        cursor.moveToFirst()
        do {
            if (selection == cursor.getString(cursor.getColumnIndex("key"))) {
                return cursor.getString(cursor.getColumnIndex("value"))
            }
        } while (cursor.moveToNext())
        return defaultValue
    }

    @SuppressLint("Range")
    fun getBoolean(selection: String, defaultValue: Boolean): Boolean {
        if (useDefaultValue || cursor == null || cursor.count == 0) {
            return defaultValue
        }
        cursor.moveToFirst()
        do {
            if (selection == cursor.getString(cursor.getColumnIndex("key"))) {
                return "1" == cursor.getString(cursor.getColumnIndex("value"))
            }
        } while (cursor.moveToNext())
        return defaultValue
    }

    @SuppressLint("Range")
    fun getStringSet(selection: String): Set<String> {
        if (useDefaultValue || cursor == null || cursor.count == 0) {
            return HashSet()
        }
        cursor.moveToFirst()
        val result: MutableSet<String> = HashSet()
        do {
            if (selection == cursor.getString(cursor.getColumnIndex("key"))) {
                result.add(cursor.getString(cursor.getColumnIndex("value")))
            }
        } while (cursor.moveToNext())
        return result
    }

    fun close() {
        cursor!!.close()
    }
}
