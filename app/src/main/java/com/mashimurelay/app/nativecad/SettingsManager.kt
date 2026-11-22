package com.mashimurelay.app.nativecad

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {

    private const val PREFS_NAME = "mashimurelay_native_settings"
    private const val KEY_GRID_SNAP = "is_grid_snap_enabled"
    private const val KEY_GRID_SIZE = "grid_size_px"
    private const val KEY_AUTO_SAVE = "is_auto_save_enabled"
    private const val KEY_FLOW_ANIM = "show_current_flow_anim"
    private const val KEY_PIN_LABELS = "show_pin_labels"
    private const val KEY_SOUND = "is_sound_enabled"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoSaveEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_SAVE, false)
    }

    fun setAutoSaveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
    }

    fun getGridSize(context: Context): Float {
        return prefs(context).getFloat(KEY_GRID_SIZE, 30f)
    }

    fun setGridSize(context: Context, size: Float) {
        prefs(context).edit().putFloat(KEY_GRID_SIZE, size).apply()
    }

    fun isGridSnapEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GRID_SNAP, true)
    }

    fun setGridSnapEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GRID_SNAP, enabled).apply()
    }

    fun isCurrentFlowAnimEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_FLOW_ANIM, true)
    }

    fun setCurrentFlowAnimEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLOW_ANIM, enabled).apply()
    }

    fun isPinLabelsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PIN_LABELS, true)
    }

    fun setPinLabelsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PIN_LABELS, enabled).apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SOUND, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    private const val KEY_COMPONENT_NAMES = "show_component_names"

    fun isComponentNamesEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_COMPONENT_NAMES, true)
    }

    fun setComponentNamesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COMPONENT_NAMES, enabled).apply()
    }

    private const val KEY_LAST_PROJECT = "last_opened_project_name"

    fun getLastProjectName(context: Context): String? {
        return prefs(context).getString(KEY_LAST_PROJECT, null)
    }

    fun setLastProjectName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_LAST_PROJECT, name).apply()
    }
}
