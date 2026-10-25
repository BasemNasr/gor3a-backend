package com.bn.gor3a.data.local

import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.domain.model.decodeImageBase64
import com.bn.gor3a.domain.model.encodeImageBase64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalPrescriptionDataSource {
    companion object {
        private const val KEY_PRESCRIPTIONS = "gor3a_saved_prescriptions"
        private const val KEY_IMAGE_PREFIX = "gor3a_rx_img_"
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

    private fun imageKey(id: String): String = "$KEY_IMAGE_PREFIX$id"

    private fun loadSavedPrescriptions(): List<SavedPrescription> {
        val serialized = PlatformPreferences.getString(KEY_PRESCRIPTIONS, "")
        if (serialized.isBlank()) {
            return emptyList()
        }
        return try {
            val decoded = json.decodeFromString<List<SavedPrescription>>(serialized)
            val cleaned = decoded
                .filterNot { it.id in LEGACY_DEMO_IDS }
                .map { rx ->
                    val storedB64 = PlatformPreferences.getString(imageKey(rx.id), "").ifBlank {
                        rx.imageBase64 ?: ""
                    }
                    if (storedB64.isNotBlank()) {
                        // Ensure per-key image storage is populated
                        PlatformPreferences.setString(imageKey(rx.id), storedB64)
                        val bytes = decodeImageBase64(storedB64)
                        rx.copy(
                            imageBase64 = storedB64,
                            prescriptionImageBytes = bytes
                        )
                    } else {
                        rx
                    }
                }
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
            // Save each prescription's image in its own dedicated key so the metadata JSON stays fast and compact
            list.forEach { rx ->
                val b64 = rx.imageBase64 ?: encodeImageBase64(rx.prescriptionImageBytes)
                if (!b64.isNullOrBlank()) {
                    PlatformPreferences.setString(imageKey(rx.id), b64)
                }
            }
            val lightweightList = list.map { it.copy(imageBase64 = null) }
            val serialized = json.encodeToString(lightweightList)
            PlatformPreferences.setString(KEY_PRESCRIPTIONS, serialized)
        } catch (_: Exception) {}
    }

    fun getPrescriptions(): Flow<List<SavedPrescription>> = _prescriptions.asStateFlow()

    fun getCurrentPrescriptionsList(): List<SavedPrescription> = _prescriptions.value

    fun getPrescriptionImageBytes(id: String): ByteArray? {
        val inMemory = _prescriptions.value.firstOrNull { it.id == id }?.resolveImageBytes()
        if (inMemory != null && inMemory.isNotEmpty()) return inMemory
        val storedB64 = PlatformPreferences.getString(imageKey(id), "")
        return decodeImageBase64(storedB64)
    }

    fun savePrescription(prescription: SavedPrescription) {
        val current = _prescriptions.value.toMutableList()
        val existing = current.firstOrNull { it.id == prescription.id }

        val resolvedBytes = prescription.resolveImageBytes() ?: existing?.resolveImageBytes()
        val resolvedB64 = prescription.imageBase64
            ?: encodeImageBase64(resolvedBytes)
            ?: existing?.imageBase64

        if (!resolvedB64.isNullOrBlank()) {
            PlatformPreferences.setString(imageKey(prescription.id), resolvedB64)
        }

        val enriched = prescription.copy(
            imageBase64 = resolvedB64,
            prescriptionImageBytes = resolvedBytes
        )

        current.removeAll { it.id == enriched.id }
        current.add(0, enriched)
        _prescriptions.value = current
        persistPrescriptions(current)
    }

    fun mergeCloudPrescriptions(cloudItems: List<SavedPrescription>) {
        if (cloudItems.isEmpty()) return
        val currentMap = _prescriptions.value.associateBy { it.id }.toMutableMap()

        for (cloudRx in cloudItems) {
            if (cloudRx.id in LEGACY_DEMO_IDS) continue
            val localRx = currentMap[cloudRx.id]
            val resolvedBytes = localRx?.resolveImageBytes() ?: cloudRx.resolveImageBytes()
            val resolvedB64 = localRx?.imageBase64
                ?: cloudRx.imageBase64
                ?: encodeImageBase64(resolvedBytes)

            if (!resolvedB64.isNullOrBlank()) {
                PlatformPreferences.setString(imageKey(cloudRx.id), resolvedB64)
            }

            currentMap[cloudRx.id] = cloudRx.copy(
                medications = if (cloudRx.medications.isNotEmpty()) cloudRx.medications else (localRx?.medications ?: emptyList()),
                medicationsCount = maxOf(cloudRx.medicationsCount, cloudRx.medications.size, localRx?.medicationsCount ?: 0),
                imageBase64 = resolvedB64,
                prescriptionImageBytes = resolvedBytes
            )
        }

        val mergedList = currentMap.values.toList()
        _prescriptions.value = mergedList
        persistPrescriptions(mergedList)
    }

    fun deletePrescription(id: String) {
        val current = _prescriptions.value.toMutableList()
        current.removeAll { it.id == id }
        PlatformPreferences.setString(imageKey(id), "")
        _prescriptions.value = current
        persistPrescriptions(current)
    }

    fun clearAllPrescriptions() {
        _prescriptions.value.forEach { rx ->
            PlatformPreferences.setString(imageKey(rx.id), "")
        }
        _prescriptions.value = emptyList()
        persistPrescriptions(emptyList())
    }
}
