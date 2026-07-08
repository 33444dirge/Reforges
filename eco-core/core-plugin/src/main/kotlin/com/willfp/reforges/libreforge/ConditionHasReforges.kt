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
 * 条件：检测玩家主手上的物品是否含有重铸石
 *
 * 配置示例：
 *   # 检测是否有任意重铸石
 *   conditions:
 *     - type: has_reforges
 *
 *   # 检测是否有特定重铸石
 *   conditions:
 *     - type: has_reforges
 *       reforge: sharp
 */
object ConditionHasReforges : Condition<NoCompileData>("has_reforges") {
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

        val reforges = item.reforges
        if (reforges.isEmpty()) return false

        // 如果指定了 reforge ID，则检测是否包含该特定重铸石
        if (config.has("reforge")) {
            val targetReforge = config.getString("reforge")
            return reforges.any { it.id.key.equals(targetReforge, ignoreCase = true) }
        }

        // 未指定 reforge ID，只要存在任意重铸石就通过
        return true
    }
}