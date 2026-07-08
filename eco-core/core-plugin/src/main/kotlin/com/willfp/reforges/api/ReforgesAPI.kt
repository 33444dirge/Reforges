@file:JvmName("ReforgesAPI")

package com.willfp.reforges.api

import com.willfp.eco.core.items.Items
import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.eco.core.sound.PlayableSound
import com.willfp.reforges.api.event.ReforgeApplyEvent
import com.willfp.reforges.plugin
import com.willfp.reforges.reforges.Reforge
import com.willfp.reforges.util.reforges
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ThreadLocalRandom

/**
 * Apply a reforge to an item, firing [ReforgeApplyEvent].
 * Adds the reforge to the item's existing reforge list (supports multiple reforges).
 * Returns false if the event was cancelled or the reforge failed (by chance); true if successful.
 */
fun Player.applyReforge(
    item: ItemStack,
    reforge: Reforge,
    price: ConfiguredPrice,
    usedStone: Boolean = false
): Boolean {
    val event = ReforgeApplyEvent(this, item, reforge, price, usedStone)
    Bukkit.getPluginManager().callEvent(event)

    if (event.isCancelled) return false

    event.price.pay(this)

    // ---- 概率判定 ----
    val roll = ThreadLocalRandom.current().nextDouble()
    if (roll > reforge.chance) {
        // 重铸失败 - 播放失败音效
        reforge.failSound?.let { PlayableSound.create(it)?.playTo(this) }
        return false
    }

    // 追加到现有重铸石列表，而不是覆盖
    val currentReforges = item.reforges.toMutableList()
    currentReforges.add(event.reforge)
    item.reforges = currentReforges
    event.reforge.runOnReforgeEffects(this, item)
    return true
}

/**
 * Dismantle reforges from an item using a dismantle stone.
 * Removes the specified reforges from the item without firing [ReforgeApplyEvent].
 *
 * @param item The item to dismantle reforges from.
 * @param dismantleStone The dismantle reforge stone used.
 * @return The list of reforges that were removed (empty if none were removed or dismantle failed).
 */
fun Player.dismantleReforges(
    item: ItemStack,
    dismantleStone: Reforge
): List<Reforge> {
    val toRemove = dismantleStone.getReforgesToDismantle(item)
    if (toRemove.isEmpty()) return emptyList()

    // ---- 概率判定 ----
    val roll = ThreadLocalRandom.current().nextDouble()
    if (roll > dismantleStone.chance) {
        // 拆卸失败 - 播放失败音效
        dismantleStone.failSound?.let { PlayableSound.create(it)?.playTo(this) }
        return emptyList()
    }

    val currentReforges = item.reforges.toMutableList()
    currentReforges.removeAll(toRemove.toSet())
    item.reforges = currentReforges

    // 如果配置了返还拆卸石本身，返还给玩家
    if (dismantleStone.dismantleReturnStone) {
        val leftover = inventory.addItem(dismantleStone.stone.clone()).values
        if (leftover.isNotEmpty()) {
            world.dropItem(location, leftover.first())
        }
    }

    // 如果配置了返还被拆下来的重铸石，逐一返还给玩家
    if (dismantleStone.dismantleReturnDismantledReforges) {
        for (removedReforge in toRemove) {
            // 检查是否有物品映射配置
            val mapping = plugin.dismantleMappingsConfig.getMapping(removedReforge.id.key)
            val stoneStack = if (mapping != null) {
                // 使用映射的物品而非原重铸石
                Items.lookup(mapping).item.clone().apply { amount = 1 }
            } else {
                removedReforge.stone.clone().apply { amount = 1 }
            }
            val leftover = inventory.addItem(stoneStack).values
            if (leftover.isNotEmpty()) {
                world.dropItem(location, leftover.first())
            }
        }
    }

    return toRemove
}