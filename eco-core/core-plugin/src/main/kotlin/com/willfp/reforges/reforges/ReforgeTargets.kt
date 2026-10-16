package com.willfp.reforges.reforges

import com.google.common.collect.ImmutableSet
import com.willfp.libreforge.slot.impl.SlotTypeAny
import com.willfp.reforges.plugin
import org.bukkit.inventory.ItemStack

object ReforgeTargets {
    private val registered = mutableMapOf<String, ReforgeTarget>()

    val ALL = ReforgeTarget("all", SlotTypeAny, HashSet())

    init {
        registered["all"] = ALL
        update()
    }

    fun getByName(name: String): ReforgeTarget? {
        return registered[name]
    }

    fun getForItem(item: ItemStack?): List<ReforgeTarget> {
        if (item == null) {
            return emptyList()
        }

        return registered.values
            .filter { !it.id.equals("all", ignoreCase = true) }
            .filter { it.matches(item) }
    }

    internal fun update() {
        ALL.items.clear()
        val targetIds = plugin.targetYml.targets.toSet()
        for (id in ArrayList(registered.keys)) {
            if (!id.equals("all", ignoreCase = true) && id !in targetIds) {
                registered.remove(id)
            }
        }
        for (id in targetIds) {
            // Keep existing target instances so Reforge objects created before
            // late item/tag registration observe the refreshed items and slot.
            val target = registered[id] ?: ReforgeTarget(
                id,
                plugin.targetYml.getSlot(id),
                mutableSetOf()
            ).also { registered[id] = it }
            target.slot = plugin.targetYml.getSlot(id)
            target.items.clear()
            target.items.addAll(plugin.targetYml.getTargetItems(id))
            registered[id] = target
            ALL.items.addAll(target.items)
        }
    }

    fun values(): Set<ReforgeTarget> {
        return ImmutableSet.copyOf(registered.values)
    }
}
