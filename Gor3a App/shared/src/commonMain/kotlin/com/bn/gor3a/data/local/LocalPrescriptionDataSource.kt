package com.bn.gor3a.data.local

import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.domain.model.SavedPrescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalPrescriptionDataSource {
    companion object {
        private const val KEY_PRESCRIPTIONS = "gor3a_saved_prescriptions"
        private val LEGACY_DEMO_IDS = setOf(
            "rx-summer-kids-01",
            "rx-derma-02",
            "rx-general-cold-03"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _prescriptions = MutableStateFlow<List<SavedPrescription>>(loadSavedPrescriptions())

    private fun loadSavedPrescriptions(): List<SavedPrescription> {
        val serialized = PlatformPreferences.getString(KEY_PRESCRIPTIONS, "")
        if (serialized.isBlank()) {
            return emptyList()
        }
        return try {
            val decoded = json.decodeFromString<List<SavedPrescription>>(serialized)
            val cleaned = decoded.filterNot { it.id in LEGACY_DEMO_IDS }
            if (cleaned.size != decoded.size) {
                persistPrescriptions(cleaned)
            }
            cleaned
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistPrescriptions(list: List<SavedPrescription>) {
        try {
            val serialized = json.encodeToString(list)
            PlatformPreferences.setString(KEY_PRESCRIPTIONS, serialized)
        } catch (_: Exception) {}
    }

    fun getPrescriptions(): Flow<List<SavedPrescription>> = _prescriptions.asStateFlow()

    fun savePrescription(prescription: SavedPrescription) {
        val current = _prescriptions.value.toMutableList()
        current.removeAll { it.id == prescription.id }
        current.add(0, prescription)
        _prescriptions.value = current
        persistPrescriptions(current)
    }

    fun deletePrescription(id: String) {
        val current = _prescriptions.value.toMutableList()
        current.removeAll { it.id == id }
        _prescriptions.value = current
        persistPrescriptions(current)
    }

    fun clearAllPrescriptions() {
        _prescriptions.value = emptyList()
        persistPrescriptions(emptyList())
    }
}
