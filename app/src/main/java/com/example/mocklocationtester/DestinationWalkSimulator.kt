package com.example.mocklocationtester

data class DestinationWalkStep(
    val position: LatLng,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val finished: Boolean,
    val targetIndex: Int?,
    val remainingDistanceMeters: Double,
    val estimatedArrivalSeconds: Double?
)

class DestinationWalkSimulator(
    private val destinations: List<LatLng>
) {
    private var targetIndex = 0
    private var finished = destinations.isEmpty()

    fun step(
        currentPosition: LatLng,
        speedMetersPerSecond: Float,
        updateIntervalSeconds: Double
    ): DestinationWalkStep {
        if (finished || destinations.isEmpty()) {
            return DestinationWalkStep(
                position = currentPosition,
                speedMetersPerSecond = 0f,
                bearingDegrees = 0f,
                finished = true,
                targetIndex = null,
                remainingDistanceMeters = 0.0,
                estimatedArrivalSeconds = 0.0
            )
        }

        val safeSpeed = speedMetersPerSecond.coerceAtLeast(0f)
        val remainingBeforeMove = remainingDistanceFrom(currentPosition, targetIndex)
        if (safeSpeed <= 0f || updateIntervalSeconds <= 0.0) {
            return DestinationWalkStep(
                position = currentPosition,
                speedMetersPerSecond = 0f,
                bearingDegrees = bearingToCurrentTarget(currentPosition),
                finished = false,
                targetIndex = targetIndex,
                remainingDistanceMeters = remainingBeforeMove,
                estimatedArrivalSeconds = null
            )
        }

        val target = destinations[targetIndex]
        val distanceToTarget = distanceMeters(currentPosition, target)
        val stepDistance = safeSpeed.toDouble() * updateIntervalSeconds
        val bearingToTarget = if (distanceToTarget > 0.0) {
            bearingDegrees(currentPosition, target).toFloat()
        } else {
            bearingToCurrentTarget(currentPosition)
        }

        if (distanceToTarget <= stepDistance) {
            val reachedPosition = target
            targetIndex += 1
            if (targetIndex >= destinations.size) {
                finished = true
                return DestinationWalkStep(
                    position = reachedPosition,
                    speedMetersPerSecond = 0f,
                    bearingDegrees = bearingToTarget,
                    finished = true,
                    targetIndex = null,
                    remainingDistanceMeters = 0.0,
                    estimatedArrivalSeconds = 0.0
                )
            }

            val remaining = remainingDistanceFrom(reachedPosition, targetIndex)
            return DestinationWalkStep(
                position = reachedPosition,
                speedMetersPerSecond = safeSpeed,
                bearingDegrees = bearingDegrees(reachedPosition, destinations[targetIndex]).toFloat(),
                finished = false,
                targetIndex = targetIndex,
                remainingDistanceMeters = remaining,
                estimatedArrivalSeconds = remaining / safeSpeed.toDouble()
            )
        }

        val moved = moveLatLng(
            latDeg = currentPosition.latitude,
            lonDeg = currentPosition.longitude,
            distanceMeters = stepDistance,
            bearingDeg = bearingToTarget.toDouble()
        )
        val movedPosition = LatLng(moved.first, moved.second)
        val remaining = remainingDistanceFrom(movedPosition, targetIndex)
        return DestinationWalkStep(
            position = movedPosition,
            speedMetersPerSecond = safeSpeed,
            bearingDegrees = bearingToTarget,
            finished = false,
            targetIndex = targetIndex,
            remainingDistanceMeters = remaining,
            estimatedArrivalSeconds = remaining / safeSpeed.toDouble()
        )
    }

    private fun bearingToCurrentTarget(currentPosition: LatLng): Float {
        return if (targetIndex in destinations.indices) {
            bearingDegrees(currentPosition, destinations[targetIndex]).toFloat()
        } else {
            0f
        }
    }

    private fun remainingDistanceFrom(position: LatLng, startingIndex: Int): Double {
        if (startingIndex !in destinations.indices) {
            return 0.0
        }

        var remaining = distanceMeters(position, destinations[startingIndex])
        for (index in startingIndex until destinations.lastIndex) {
            remaining += distanceMeters(destinations[index], destinations[index + 1])
        }
        return remaining
    }
}
