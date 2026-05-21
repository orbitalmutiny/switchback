package com.spop.poverlay.route

import java.io.File

class RouteStore(
    private val routesDir: File,
    private val parser: GpxRouteParser = GpxRouteParser()
) {
    fun saveGpx(
        name: String,
        gpxText: String
    ): ImportedRoute {
        if (!routesDir.exists()) {
            routesDir.mkdirs()
        }
        val routeId = uniqueRouteId(name.toRouteId())
        val file = File(routesDir, "$routeId.gpx")
        file.writeText(gpxText)
        return file.inputStream().use {
            parser.parse(
                id = routeId,
                fallbackName = file.nameWithoutExtension,
                inputStream = it
            )
        }
    }

    fun listRoutes(): List<ImportedRoute> =
        listRouteFiles().mapNotNull { file ->
            loadRoute(file.nameWithoutExtension)
        }

    fun loadRoute(id: String): ImportedRoute? {
        val file = File(routesDir, "$id.gpx")
        if (!file.exists()) {
            return null
        }
        return runCatching {
            file.inputStream().use {
                parser.parse(
                    id = id,
                    fallbackName = file.nameWithoutExtension,
                    inputStream = it
                )
            }
        }.getOrNull()
    }

    fun deleteRoute(id: String): Boolean {
        val file = File(routesDir, "$id.gpx")
        return file.exists() && file.delete()
    }

    private fun listRouteFiles(): List<File> =
        routesDir
            .listFiles { file -> file.extension.equals("gpx", ignoreCase = true) }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()

    private fun uniqueRouteId(baseId: String): String {
        var candidate = baseId
        var suffix = 2
        while (File(routesDir, "$candidate.gpx").exists()) {
            candidate = "$baseId-$suffix"
            suffix += 1
        }
        return candidate
    }
}
