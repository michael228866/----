package com.example.mocklocationtester

enum class RouteEndBehavior {
    STOP,
    LOOP,
    PING_PONG
}

data class RouteStep(
    val position: LatLng,
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float,
    val finished: Boolean
)

class RouteSimulator(
    private val waypoints: List<LatLng>,
    private val endBehavior: RouteEndBehavior = RouteEndBehavior.PING_PONG,
    startPosition: LatLng? = null
) {
    private var targetIndex = nearestWaypointIndex(startPosition)
    private var direction = 1
    private var finished = waypoints.isEmpty()

    fun step(
        currentPosition: LatLng,
        speedMetersPerSecond: Float,
        updateIntervalSeconds: Double
    ): RouteStep {
        if (finished || waypoints.isEmpty()) {
            return RouteStep(
                position = currentPosition,
                speedMetersPerSecond = 0f,
                bearingDegrees = 0f,
                finished = true
            )
        }

        if (waypoints.size == 1 || speedMetersPerSecond <= 0f || updateIntervalSeconds <= 0.0) {
            return RouteStep(
                position = currentPosition,
                speedMetersPerSecond = 0f,
                bearingDegrees = 0f,
                finished = waypoints.size <= 1
            )
        }

        val target = waypoints[targetIndex]
        val distanceToTarget = distanceMeters(currentPosition, target)
        val stepDistance = speedMetersPerSecond.toDouble() * updateIntervalSeconds
        val bearingToTarget = if (distanceToTarget > 0.0) {
            bearingDegrees(currentPosition, target)
        } else {
            0.0
        }

        if (distanceToTarget <= stepDistance) {
            val nextTargetExists = advanceTarget()
            return RouteStep(
                position = target,
                speedMetersPerSecond = if (nextTargetExists) speedMetersPerSecond else 0f,
                bearingDegrees = if (nextTargetExists) {
                    bearingDegrees(target, waypoints[targetIndex]).toFloat()
                } else {
                    bearingToTarget.toFloat()
                },
                finished = !nextTargetExists
            )
        }

        val moved = moveLatLng(
            latDeg = currentPosition.latitude,
            lonDeg = currentPosition.longitude,
            distanceMeters = stepDistance,
            bearingDeg = bearingToTarget
        )

        return RouteStep(
            position = LatLng(moved.first, moved.second),
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingToTarget.toFloat(),
            finished = false
        )
    }

    private fun advanceTarget(): Boolean {
        if (waypoints.size <= 1) {
            finished = true
            return false
        }

        val next = targetIndex + direction
        if (next in waypoints.indices) {
            targetIndex = next
            return true
        }

        return when (endBehavior) {
            RouteEndBehavior.STOP -> {
                finished = true
                false
            }

            RouteEndBehavior.LOOP -> {
                targetIndex = 0
                direction = 1
                true
            }

            RouteEndBehavior.PING_PONG -> {
                direction *= -1
                val pingPongNext = targetIndex + direction
                if (pingPongNext in waypoints.indices) {
                    targetIndex = pingPongNext
                    true
                } else {
                    finished = true
                    false
                }
            }
        }
    }

    private fun nearestWaypointIndex(startPosition: LatLng?): Int {
        if (waypoints.isEmpty() || startPosition == null) {
            return 0
        }
        return waypoints.indices.minBy { index ->
            distanceMeters(startPosition, waypoints[index])
        }
    }
}
