package com.spop.poverlay.ride

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RideSessionStore(context: Context) {
    private val sessionsDir = File(context.filesDir, "ride_sessions")

    fun save(record: RideSessionRecord): File {
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs()
        }

        val file = File(sessionsDir, "${record.id}.json")
        file.writeText(record.toJson().toString())
        return file
    }

    fun listSessionFiles(): List<File> =
        sessionsDir
            .listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun listSessionSummaries(): List<RideSessionSummary> =
        listSessionFiles().mapNotNull { file ->
            runCatching {
                JSONObject(file.readText()).getJSONObject("summary").toRideSessionSummary()
            }.getOrNull()
        }

    fun loadSession(id: String): RideSessionRecord? {
        val file = File(sessionsDir, "$id.json")
        if (!file.exists()) {
            return null
        }
        return runCatching {
            JSONObject(file.readText()).toRideSessionRecord()
        }.getOrNull()
    }

    private fun RideSessionRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startedAtMs", startedAtMs)
        put("completedAtMs", completedAtMs)
        put("summary", summary.toJson())
        put(
            "samples",
            JSONArray().also { samplesJson ->
                samples.forEach { samplesJson.put(it.toJson()) }
            }
        )
    }

    private fun RideSessionSummary.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startedAtMs", startedAtMs)
        put("completedAtMs", completedAtMs)
        put("durationMs", durationMs)
        put("sampleCount", sampleCount)
        put("averagePowerWatts", averagePowerWatts)
        put("maxPowerWatts", maxPowerWatts)
        put("averageCadenceRpm", averageCadenceRpm)
        put("distanceMiles", distanceMiles)
        put("averageHeartRateBpm", averageHeartRateBpm)
        put("maxHeartRateBpm", maxHeartRateBpm)
        put("totalWorkKilojoules", totalWorkKilojoules)
        put("estimatedCalories", estimatedCalories)
    }

    private fun RideTelemetrySample.toJson() = JSONObject().apply {
        put("timestampMs", timestampMs)
        put("powerWatts", powerWatts)
        put("cadenceRpm", cadenceRpm)
        put("resistance", resistance)
        put("speedMph", speedMph)
        put("distanceMiles", distanceMiles)
        put("heartRateBpm", heartRateBpm)
        put("routePositionMeters", routePositionMeters)
    }

    private fun JSONObject.toRideSessionSummary() = RideSessionSummary(
        id = getString("id"),
        name = if (isNull("name")) null else getString("name"),
        startedAtMs = getLong("startedAtMs"),
        completedAtMs = if (isNull("completedAtMs")) null else getLong("completedAtMs"),
        durationMs = getLong("durationMs"),
        sampleCount = getInt("sampleCount"),
        averagePowerWatts = getDouble("averagePowerWatts").toFloat(),
        maxPowerWatts = getDouble("maxPowerWatts").toFloat(),
        averageCadenceRpm = getDouble("averageCadenceRpm").toFloat(),
        distanceMiles = getDouble("distanceMiles").toFloat(),
        averageHeartRateBpm = if (isNull("averageHeartRateBpm")) null else getInt("averageHeartRateBpm"),
        maxHeartRateBpm = if (isNull("maxHeartRateBpm")) null else getInt("maxHeartRateBpm"),
        totalWorkKilojoules = if (isNull("totalWorkKilojoules")) 0f else getDouble("totalWorkKilojoules").toFloat(),
        estimatedCalories = if (isNull("estimatedCalories")) 0 else getInt("estimatedCalories")
    )

    private fun JSONObject.toRideSessionRecord() = RideSessionRecord(
        id = getString("id"),
        name = if (isNull("name")) null else getString("name"),
        startedAtMs = getLong("startedAtMs"),
        completedAtMs = if (isNull("completedAtMs")) null else getLong("completedAtMs"),
        samples = getJSONArray("samples").toRideTelemetrySamples()
    )

    private fun JSONArray.toRideTelemetrySamples(): List<RideTelemetrySample> =
        (0 until length()).map { index ->
            getJSONObject(index).toRideTelemetrySample()
        }

    private fun JSONObject.toRideTelemetrySample() = RideTelemetrySample(
        timestampMs = getLong("timestampMs"),
        powerWatts = getDouble("powerWatts").toFloat(),
        cadenceRpm = getDouble("cadenceRpm").toFloat(),
        resistance = getInt("resistance"),
        speedMph = getDouble("speedMph").toFloat(),
        distanceMiles = getDouble("distanceMiles").toFloat(),
        heartRateBpm = if (isNull("heartRateBpm")) null else getInt("heartRateBpm"),
        routePositionMeters = if (isNull("routePositionMeters")) null else getDouble("routePositionMeters").toFloat()
    )
}
