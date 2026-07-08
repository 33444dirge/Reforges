package com.willfp.reforges.reforges

import com.willfp.reforges.plugin
import org.bukkit.inventory.ItemStack

/**
 * 管理所有重铸石槽位显示配置。
 */
object ReforgeSockets {
    private val registered = mutableListOf<ReforgeSocketConfig>()

    /**
     * 重新从配置文件加载所有槽位配置。
     */
    internal fun update() {
        registered.clear()
        for (id in plugin.socketsYml.socketIds) {
            if (!plugin.socketsYml.isEnabled(id)) continue

            val slots = plugin.socketsYml.getSocketSlots(id)
            if (slots.isEmpty()) continue

            val config = ReforgeSocketConfig(
                id = id,
                items = plugin.socketsYml.getSocketItems(id).toMutableSet(),
                sockets = slots.entries.sortedBy { it.key }.map { (slotNum, pair) ->
                    ReforgeSocketConfig.SocketEntry(
                        slot = slotNum,
                        reforgeId = pair.first,
                        line = pair.second
                    )
                }
            )
            if (config.items.isNotEmpty() && config.sockets.isNotEmpty()) {
                registered.add(config)
            }
        }
    }

    /**
     * 获取匹配物品的第一个槽位配置（按配置顺序）。
     */
    fun getForItem(item: ItemStack?): ReforgeSocketConfig? {
        if (item == null) return null
        return registered.firstOrNull { it.matches(item) }
    }

    /**
     * 获取物品上应显示的可见槽位 Lore 行。
     *
     * 逻辑：
     * 1. 按重铸石 ID 分组槽位
     * 2. 统计物品上各 ID 的重铸石数量
     * 3. 每个组内按槽位编号升序消耗（同 ID 有 N 个则消耗前 N 个槽位）
     * 4. 返回未被消耗的槽位的显示文本
     *
     * 例如：
     *   物品有 1 个 aerobic → 消耗第 1 个 aerobic 槽位（编号最小）
     *   物品有 2 个 aerobic → 消耗第 1 个和第 4 个 aerobic 槽位（如果有）
     *
     * @param item 物品
     * @param reforges 物品上已有的重铸石列表
     * @return 可见的槽位显示文本列表，若无匹配则返回 null
     */
    fun getVisibleSockets(item: ItemStack?, reforges: List<Reforge>): List<String>? {
        val config = getForItem(item) ?: return null
        if (config.sockets.isEmpty()) return null

        // 统计物品上各重铸石 ID 的数量
        val reforgeCounts = mutableMapOf<String, Int>()
        for (reforge in reforges) {
            val key = reforge.id.key
            reforgeCounts[key] = (reforgeCounts[key] ?: 0) + 1
        }

        // 按 reforgeId 分组槽位（保持原排序）
        val groups = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        for (entry in config.sockets) {
            groups.getOrPut(entry.reforgeId) { mutableListOf() }.add(entry.slot to entry.line)
        }

        // 计算被消耗的槽位编号
        val consumed = mutableSetOf<Int>()
        for ((reforgeId, entries) in groups) {
            val count = reforgeCounts[reforgeId] ?: 0
            // 消耗前 N 个（按槽位编号顺序），即同 ID 的石头按顺序填槽位
            for (i in 0 until minOf(count, entries.size)) {
                consumed.add(entries[i].first)
            }
        }

        // 返回未被消耗的槽位的显示文本
        return config.sockets
            .filter { it.slot !in consumed }
            .map { it.line }
    }
}