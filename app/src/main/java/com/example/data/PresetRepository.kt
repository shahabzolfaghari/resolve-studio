package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.GradePreset
import com.example.model.ColorGradeSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for storing and retrieving custom color grading presets.
 * Combines built-in factory looks with user-created presets saved across app sessions.
 */
class PresetRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("color_grade_presets", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, GradePreset::class.java)
    private val adapter = moshi.adapter<List<GradePreset>>(listType)

    private val _presets = MutableStateFlow<List<GradePreset>>(emptyList())
    val presets: StateFlow<List<GradePreset>> = _presets.asStateFlow()

    init {
        loadPresets()
    }

    private fun loadPresets() {
        val json = prefs.getString("user_saved_presets", null)
        val userPresets = if (!json.isNullOrBlank()) {
            try {
                adapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        _presets.value = GradePreset.DEFAULT_PRESETS + userPresets
    }

    suspend fun savePreset(
        name: String,
        description: String,
        category: String,
        settings: ColorGradeSettings
    ): GradePreset = withContext(Dispatchers.IO) {
        val cleanSettings = settings.copy(customLutData = null)
        val newPreset = GradePreset(
            name = name,
            description = description,
            category = category,
            isBuiltIn = false,
            settings = cleanSettings
        )

        val currentUserPresets = _presets.value.filterNot { it.isBuiltIn }
        val updatedUserList = currentUserPresets + newPreset

        val json = adapter.toJson(updatedUserList)
        prefs.edit().putString("user_saved_presets", json).apply()

        _presets.value = GradePreset.DEFAULT_PRESETS + updatedUserList
        newPreset
    }

    suspend fun deletePreset(id: String) = withContext(Dispatchers.IO) {
        val currentUserPresets = _presets.value.filterNot { it.isBuiltIn }
        val updatedUserList = currentUserPresets.filterNot { it.id == id }

        val json = adapter.toJson(updatedUserList)
        prefs.edit().putString("user_saved_presets", json).apply()

        _presets.value = GradePreset.DEFAULT_PRESETS + updatedUserList
    }
}
