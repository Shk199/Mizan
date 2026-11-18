package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.writingDraftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "writing_draft"
)

data class WritingVerseDraft(
    val sadr: String,
    val ajuz: String
)

data class WritingDraft(
    val meterId: String = "tawil",
    val formType: String = "تام",
    val noFixedQafiya: Boolean = true,
    val roiChar: String = "",
    val majraOption: String = "ضمة",
    val tasriFirstVerse: Boolean = false,
    val isSettingsExpanded: Boolean = true,
    val verses: List<WritingVerseDraft> = listOf(WritingVerseDraft("", ""))
)

object WritingDraftStore {
    private val DRAFT_JSON_KEY = stringPreferencesKey("writing_draft_json")

    suspend fun loadDraft(context: Context): WritingDraft? {
        return try {
            val prefs = context.applicationContext.writingDraftDataStore.data.first()
            val raw = prefs[DRAFT_JSON_KEY] ?: return null
            val obj = JSONObject(raw)
            val versesArr = obj.optJSONArray("verses") ?: JSONArray()
            val verses = mutableListOf<WritingVerseDraft>()
            for (i in 0 until versesArr.length()) {
                val item = versesArr.optJSONObject(i) ?: continue
                verses.add(
                    WritingVerseDraft(
                        sadr = item.optString("sadr", ""),
                        ajuz = item.optString("ajuz", "")
                    )
                )
            }
            if (verses.isEmpty()) {
                verses.add(WritingVerseDraft("", ""))
            }
            WritingDraft(
                meterId = obj.optString("meterId", "tawil"),
                formType = obj.optString("formType", "تام"),
                noFixedQafiya = obj.optBoolean("noFixedQafiya", true),
                roiChar = obj.optString("roiChar", ""),
                majraOption = obj.optString("majraOption", "ضمة"),
                tasriFirstVerse = obj.optBoolean("tasriFirstVerse", false),
                isSettingsExpanded = obj.optBoolean("isSettingsExpanded", true),
                verses = verses
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveDraft(context: Context, draft: WritingDraft) {
        val obj = JSONObject().apply {
            put("meterId", draft.meterId)
            put("formType", draft.formType)
            put("noFixedQafiya", draft.noFixedQafiya)
            put("roiChar", draft.roiChar)
            put("majraOption", draft.majraOption)
            put("tasriFirstVerse", draft.tasriFirstVerse)
            put("isSettingsExpanded", draft.isSettingsExpanded)
            val arr = JSONArray()
            draft.verses.forEach { v ->
                arr.put(
                    JSONObject().apply {
                        put("sadr", v.sadr)
                        put("ajuz", v.ajuz)
                    }
                )
            }
            put("verses", arr)
        }
        context.applicationContext.writingDraftDataStore.edit { prefs ->
            prefs[DRAFT_JSON_KEY] = obj.toString()
        }
    }

    suspend fun clearDraft(context: Context) {
        context.applicationContext.writingDraftDataStore.edit { prefs ->
            prefs.remove(DRAFT_JSON_KEY)
        }
    }
}
