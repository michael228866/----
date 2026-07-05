package com.example.mocklocationtester

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val EARTH_RADIUS_METERS = 6371000.0

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

fun distanceMeters(a: LatLng, b: LatLng): Double {
    require(a.latitude.isFinite() && a.longitude.isFinite()) { "第一個座標必須是有效數字。" }
    require(b.latitude.isFinite() && b.longitude.isFinite()) { "第二個座標必須是有效數字。" }

    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = Math.toRadians(b.latitude - a.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)

    val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    val angularDistance = 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
    return EARTH_RADIUS_METERS * angularDistance
}

fun bearingDegrees(a: LatLng, b: LatLng): Double {
    require(a.latitude.isFinite() && a.longitude.isFinite()) { "第一個座標必須是有效數字。" }
    require(b.latitude.isFinite() && b.longitude.isFinite()) { "第二個座標必須是有效數字。" }

    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
    return normalizeBearing(Math.toDegrees(atan2(y, x)))
}

fun moveLatLng(
    latDeg: Double,
    lonDeg: Double,
    distanceMeters: Double,
    bearingDeg: Double
): Pair<Double, Double> {
    require(latDeg.isFinite()) { "緯度必須是有效數字。" }
    require(lonDeg.isFinite()) { "經度必須是有效數字。" }
    require(distanceMeters.isFinite()) { "距離必須是有效數字。" }
    require(bearingDeg.isFinite()) { "方向角必須是有效數字。" }
    require(latDeg in -90.0..90.0) { "緯度必須介於 -90 到 90 之間。" }
    require(distanceMeters >= 0.0) { "距離必須大於或等於 0。" }

    if (distanceMeters == 0.0) {
        return latDeg to normalizeLongitude(lonDeg)
    }

    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearingRad = Math.toRadians(normalizeBearing(bearingDeg))
    val latRad = Math.toRadians(latDeg)
    val lonRad = Math.toRadians(lonDeg)

    val nextLatRad = asin(
        sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
    )
    val nextLonRad = lonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(latRad),
        cos(angularDistance) - sin(latRad) * sin(nextLatRad)
    )

    return Math.toDegrees(nextLatRad).coerceIn(-90.0, 90.0) to
        normalizeLongitude(Math.toDegrees(nextLonRad))
}

fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
    if (polygon.size < 3) {
        return false
    }

    var inside = false
    var previous = polygon.last()
    for (current in polygon) {
        val intersects = (current.longitude > point.longitude) !=
            (previous.longitude > point.longitude) &&
            point.latitude < (previous.latitude - current.latitude) *
            (point.longitude - current.longitude) /
            ((previous.longitude - current.longitude).takeIf { it != 0.0 } ?: Double.MIN_VALUE) +
            current.latitude
        if (intersects) {
            inside = !inside
        }
        previous = current
    }
    return inside
}

fun generateRandomWaypointsInPolygon(
    polygon: List<LatLng>,
    count: Int
): List<LatLng> {
    require(polygon.size >= 3) { "多邊形至少需要 3 個座標點。" }

    val targetCount = count.coerceIn(10, 50)
    val minLat = polygon.minOf { it.latitude }
    val maxLat = polygon.maxOf { it.latitude }
    val minLon = polygon.minOf { it.longitude }
    val maxLon = polygon.maxOf { it.longitude }

    require(minLat < maxLat) { "多邊形緯度範圍不可為空。" }
    require(minLon < maxLon) { "多邊形經度範圍不可為空。" }

    val random = Random(System.nanoTime())
    val generated = mutableListOf<LatLng>()
    val maxAttempts = targetCount * 500

    repeat(maxAttempts) {
        if (generated.size >= targetCount) {
            return@repeat
        }

        val point = LatLng(
            latitude = random.nextDouble(minLat, maxLat),
            longitude = random.nextDouble(minLon, maxLon)
        )
        if (isPointInPolygon(point, polygon)) {
            generated.add(point)
        }
    }

    return nearestNeighborSort(generated)
}

fun normalizeLongitude(lonDeg: Double): Double {
    val normalized = (lonDeg + 540.0) % 360.0 - 180.0
    return if (normalized == -180.0 && lonDeg > 0.0) {
        180.0
    } else {
        normalized
    }
}

fun normalizeBearing(bearingDeg: Double): Double {
    val normalized = bearingDeg % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

fun normalizeBearing(bearingDeg: Float): Float {
    val normalized = bearingDeg % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}

private fun nearestNeighborSort(points: List<LatLng>): List<LatLng> {
    if (points.size <= 2) {
        return points
    }

    val remaining = points.toMutableList()
    val ordered = mutableListOf<LatLng>()
    val start = remaining.minWith(compareBy<LatLng> { it.latitude }.thenBy { it.longitude })
    ordered.add(start)
    remaining.remove(start)

    while (remaining.isNotEmpty()) {
        val current = ordered.last()
        val nearest = remaining.minBy { distanceMeters(current, it) }
        ordered.add(nearest)
        remaining.remove(nearest)
    }

    return ordered
}

fun boundingBox(points: List<LatLng>): Pair<LatLng, LatLng> {
    require(points.isNotEmpty()) { "座標點不可為空。" }
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    return LatLng(minLat, minLon) to LatLng(max(maxLat, minLat), max(maxLon, minLon))
}
