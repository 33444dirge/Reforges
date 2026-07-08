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
 * 条件：检测玩家主手上的物品含有多少个重铸石
 *
 * 配置示例：
 *   # 至少 1 个
 *   conditions:
 *     - type: reforge_count
 *       minimum: 1
 *
 *   # 不超过 3 个
 *   conditions:
 *     - type: reforge_count
 *       maximum: 3
 *
 *   # 在 1 ~ 3 个之间
 *   conditions:
 *     - type: reforge_count
 *       minimum: 1
 *       maximum: 3
 *
 *   # 恰好 2 个
 *   conditions:
 *     - type: reforge_count
 *       minimum: 2
 *       maximum: 2
 */
object ConditionReforgeCount : Condition<NoCompileData>("reforge_count") {
    override val arguments = arguments { }

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

        val count = item.reforges.size

        // 检查最小值（含）
        if (config.has("minimum")) {
            val min = config.getInt("minimum")
            if (count < min) return false
        }

        // 检查最大值（含）
        if (config.has("maximum")) {
            val max = config.getInt("maximum")
            if (count > max) return false
        }

        return true
    }
}