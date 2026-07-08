package com.willfp.reforges.config

import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.StaticBaseConfig
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.libreforge.loader.LibreforgePlugin

class SocketsYml(
    plugin: LibreforgePlugin
) : StaticBaseConfig("sockets", plugin, ConfigType.YAML) {
    /**
     * 获取所有 socket 配置的名称列表。
     */
    val socketIds: List<String>
        get() = getKeys(false)

    /**
     * 检查指定 socket 配置是否启用。
     */
    fun isEnabled(socketId: String): Boolean {
        return if (has("$socketId.enabled")) getBool("$socketId.enabled") else true
    }

    /**
     * 获取指定 socket 配置的物品匹配列表。
     */
    fun getSocketItems(socketId: String): Set<TestableItem> {
        val items: MutableSet<TestableItem> = HashSet()
        this.getStrings("$socketId.items").forEach { s ->
            items.add(Items.lookup(s.uppercase()))
        }
        return items
    }

    /**
     * 获取指定 socket 配置的槽位列表。
     *
     * 返回：Map<槽位编号, Pair<重铸石ID, 显示文本>>
     *
     * YAML 格式：
     *   sockets:
     *     1:
     *       aerobic: " &7◇ &8[&7 aerobic 重铸石槽&8]"
     *     2:
     *       thin: " &7◇ &8[&7 thin 重铸石槽&8]"
     */
    fun getSocketSlots(socketId: String): Map<Int, Pair<String, String>> {
        val result = mutableMapOf<Int, Pair<String, String>>()
        if (!has("$socketId.sockets")) return result

        val socketsSection = getSubsection("$socketId.sockets")
        val slotKeys = socketsSection.getKeys(false)
        for (slotKey in slotKeys) {
            val slotNum = slotKey.toIntOrNull() ?: continue
            val slotSection = socketsSection.getSubsection(slotKey)
            // 每个槽位只有一个 key，即重铸石 ID
            val reforgeIds = slotSection.getKeys(false)
            if (reforgeIds.isNotEmpty()) {
                val reforgeId = reforgeIds.first()
                val line = slotSection.getFormattedString(reforgeId)
                result[slotNum] = reforgeId to line
            }
        }
        return result
    }
}