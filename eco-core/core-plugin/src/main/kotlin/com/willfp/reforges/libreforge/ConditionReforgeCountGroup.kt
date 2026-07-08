package com.willfp.reforges.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.get
import com.willfp.libreforge.getProvider
import com.willfp.reforges.util.reforges
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 条件：检测玩家主手上的物品中，属于指定重铸石组的重铸石总数量
 *
 * 统计的是"匹配上有几个"，而不是"必须全部有"。
 *
 * 配置示例：
 *   # 至少命中 1 个
 *   conditions:
 *     - type: reforge_count_group
 *       reforges:
 *         - sharp
 *         - acute
 *         - dynamic
 *       minimum: 1
 *
 *   # 命中 1~2 个
 *   conditions:
 *     - type: reforge_count_group
 *       reforges:
 *         - sharp
 *         - acute
 *         - dynamic
 *       minimum: 1
 *       maximum: 2
 *
 *   # 恰好命中 3 个（全部命中）
 *   conditions:
 *     - type: reforge_count_group
 *       reforges:
 *         - sharp
 *         - acute
 *         - dynamic
 *       minimum: 3
 *       maximum: 3
 */
object ConditionReforgeCountGroup : Condition<NoCompileData>("reforge_count_group") {
    override val arguments = arguments {
        require("reforges", "The list of reforge IDs to count.")
    }

    override fun isMet(
        dispatcher: Dispatcher<*>,
        config: Config,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val player = dispatcher.get<Player>() ?: return false
        // 优先使用 holder 提供的物品（如 GUI 中俘虏槽位的物品），回退到主手物品
        val item = holder.getProvider<ItemStack>() ?: player.inventory.itemInMainHand
        if (item.type.isAir) return false

        val itemReforgeKeys = item.reforges.map { it.id.key.lowercase() }.toSet()

        // 获取组内所有重铸石 ID
        val groupReforgeKeys = config.getStrings("reforges").map { it.lowercase() }.toSet()
        if (groupReforgeKeys.isEmpty()) return false

        // 统计命中了几个（物品没宝石时 matchCount = 0，正常参与 min/max 比较）
        val matchCount = groupReforgeKeys.count { it in itemReforgeKeys }

        // 检查最小值（含）
        if (config.has("minimum")) {
            val min = config.getInt("minimum")
            if (matchCount < min) return false
        }

        // 检查最大值（含）
        if (config.has("maximum")) {
            val max = config.getInt("maximum")
            if (matchCount > max) return false
        }

        return true
    }
}