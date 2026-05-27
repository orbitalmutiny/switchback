package com.spop.poverlay.route

enum class ManualResistanceTolerance(
    val id: String,
    val label: String,
    val halfBand: Int
) {
    Tight("tight", "Tight (±2)", 2),
    Normal("normal", "Normal (±4)", 4),
    Loose("loose", "Loose (±6)", 6);

    companion object {
        val Default = Normal

        fun fromId(id: String?): ManualResistanceTolerance =
            values().firstOrNull { it.id == id } ?: Default
    }
}
