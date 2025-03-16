package com.kooritea.fcmfix

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class XposedProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        return false
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        //这里填写查询逻辑
        var config = JSONObject()
        try {
            val fis = context!!.openFileInput("config.json")
            val inputStreamReader = InputStreamReader(fis, StandardCharsets.UTF_8)
            val stringBuilder = StringBuilder()
            val reader = BufferedReader(inputStreamReader)
            var line = reader.readLine()
            while (line != null) {
                stringBuilder.append(line).append('\n')
                line = reader.readLine()
            }
            config = JSONObject(stringBuilder.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        val COLUMN_NAME = arrayOf("key", "value")
        val data = MatrixCursor(COLUMN_NAME)
        try {
            data.addRow(
                arrayOf<Any>(
                    "disableAutoCleanNotification",
                    if (config.isNull("disableAutoCleanNotification")) "0" else (if (config.getBoolean(
                            "disableAutoCleanNotification"
                        )
                    ) "1" else "0")
                )
            )
            val jsonAllowList = config.getJSONArray("allowList")
            for (i in 0..<jsonAllowList.length()) {
                data.addRow(arrayOf<Any>("allowList", jsonAllowList.getString(i)))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return data
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        //这里填写插入逻辑
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        //这里填写更新逻辑
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        //这里填写删除逻辑
        return 0
    }

    companion object {
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            uriMatcher.addURI("com.kooritea.fcmfix.provider", "config", 0)
        }
    }
}