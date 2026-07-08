package com.willfp.reforges.reforges

import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import org.bukkit.inventory.ItemStack

/**
 * 一个重铸石槽位显示配置。
 *
 * @param id 配置 ID
 * @param items 匹配的物品列表
 * @param sockets 槽位列表（按编号排序），每个槽位关联一个重铸石 ID
 */
class ReforgeSocketConfig(
    val id: String,
    val items: MutableSet<TestableItem>,
    val sockets: List<SocketEntry>
) {
    /**
     * 单个槽位条目。
     *
     * @param slot 槽位编号（1~N）
     * @param reforgeId 此槽位对应的重铸石 ID
     * @param line 空槽位时显示的 lore 文本
     */
    data class SocketEntry(
        val slot: Int,
        val reforgeId: String,
        val line: String
    )

    init {
        items.removeIf { it is EmptyTestableItem }
    }

    /**
     * 判断给定的物品是否匹配此槽位配置。
     */
    fun matches(itemStack: ItemStack): Boolean {
        return items.any { it.matches(itemStack) }
    }
}