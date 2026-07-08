package com.willfp.reforges.util

enum class ReforgeStatus(
    val configKey: String
) {
    ALLOW("allow"),
    ALLOW_STONE("allow-stone"),
    ALLOW_DISMANTLE("allow-dismantle"),
    INVALID_ITEM("invalid-item"),
    NO_ITEM("no-item")
}