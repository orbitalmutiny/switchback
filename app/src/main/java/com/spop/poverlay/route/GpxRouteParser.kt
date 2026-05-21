package com.spop.poverlay.route

import org.w3c.dom.Element
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val MaxGradeWindowMeters = 100.0

class GpxRouteParser {
    fun parse(
        id: String,
        fallbackName: String,
        inputStream: InputStream
    ): ImportedRoute {
        val document = DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
                setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            .newDocumentBuilder()
            .parse(inputStream)

        val rawPoints = document
            .routeElementsByTagName("trkpt")
            .ifEmpty { document.routeElementsByTagName("rtept") }
            .map { element ->
                RawRoutePoint(
                    latitude = element.getAttribute("lat").toDouble(),
                    longitude = element.getAttribute("lon").toDouble(),
                    elevationMeters = element.optionalChildText("ele")?.toDoubleOrNull()
                )
            }

        require(rawPoints.size >= 2) { "GPX route must contain at least two route points" }

        val routeName = document
            .routeElementsByTagName("name")
            .firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName

        val points = rawPoints.withDistanceFromStart()
        return ImportedRoute(
            id = id,
            name = routeName,
            points = points,
            metadata = points.toMetadata()
        )
    }

    private fun List<RawRoutePoint>.withDistanceFromStart(): List<RoutePoint> {
        var distanceMeters = 0.0
        return mapIndexed { index, point ->
            if (index > 0) {
                val previous = this[index - 1]
                distanceMeters += haversineMeters(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude
                )
            }
            RoutePoint(
                latitude = point.latitude,
                longitude = point.longitude,
                elevationMeters = point.elevationMeters,
                distanceFromStartMeters = distanceMeters
            )
        }
    }

    private fun List<RoutePoint>.toMetadata(): RouteMetadata {
        var totalClimbMeters = 0.0
        zipWithNext().forEach { (start, end) ->
            val elevationGain = ((end.elevationMeters ?: 0.0) - (start.elevationMeters ?: 0.0))
            if (elevationGain > 0.0) {
                totalClimbMeters += elevationGain
            }
        }
        val distanceMeters = last().distanceFromStartMeters
        val averageClimbingGradePercent = if (distanceMeters > 0.0) {
            (totalClimbMeters / distanceMeters) * 100.0
        } else {
            0.0
        }
        return RouteMetadata(
            distanceMeters = distanceMeters,
            totalClimbMeters = totalClimbMeters,
            maxGradePercent = maxClimbingGradePercent(),
            averageClimbingGradePercent = averageClimbingGradePercent
        )
    }

    private fun List<RoutePoint>.maxClimbingGradePercent(): Double {
        if (size < 2) {
            return 0.0
        }

        var maxGradePercent = 0.0
        forEachIndexed { startIndex, start ->
            val startElevation = start.elevationMeters ?: return@forEachIndexed
            val targetDistance = start.distanceFromStartMeters + MaxGradeWindowMeters
            val end = drop(startIndex + 1)
                .firstOrNull { it.distanceFromStartMeters >= targetDistance && it.elevationMeters != null }
                ?: drop(startIndex + 1).lastOrNull { it.elevationMeters != null }
                ?: return@forEachIndexed
            val distance = end.distanceFromStartMeters - start.distanceFromStartMeters
            if (distance < MaxGradeWindowMeters && last().distanceFromStartMeters >= MaxGradeWindowMeters) {
                return@forEachIndexed
            }
            val elevationGain = (end.elevationMeters ?: startElevation) - startElevation
            if (elevationGain > 0.0 && distance > 0.0) {
                maxGradePercent = max(maxGradePercent, (elevationGain / distance) * 100.0)
            }
        }
        return maxGradePercent
    }

    private fun Element.optionalChildText(name: String): String? =
        routeElementsByTagName(name)
            .firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun Element.routeElementsByTagName(name: String): List<Element> =
        getElementsByTagNameNS("*", name).toElementList()
            .ifEmpty { getElementsByTagName(name).toElementList() }

    private fun org.w3c.dom.Document.routeElementsByTagName(name: String): List<Element> =
        getElementsByTagNameNS("*", name).toElementList()
            .ifEmpty { getElementsByTagName(name).toElementList() }

    private fun org.w3c.dom.NodeList.toElementList(): List<Element> =
        (0 until length)
            .mapNotNull { item(it) as? Element }

    private fun DocumentBuilderFactory.setFeatureIfSupported(
        name: String,
        value: Boolean
    ) {
        runCatching {
            setFeature(name, value)
        }
    }

    private fun haversineMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(endLatitude - startLatitude)
        val dLon = Math.toRadians(endLongitude - startLongitude)
        val startLatRad = Math.toRadians(startLatitude)
        val endLatRad = Math.toRadians(endLatitude)

        val a = sin(dLat / 2).pow(2.0) +
                cos(startLatRad) * cos(endLatRad) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    private data class RawRoutePoint(
        val latitude: Double,
        val longitude: Double,
        val elevationMeters: Double?
    )
}

fun String.toRouteId(): String =
    lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "route" }
