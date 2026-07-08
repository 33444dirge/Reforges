package com.willfp.reforges.reforges

import com.willfp.libreforge.slot.ItemHolderFinder
import com.willfp.libreforge.slot.SlotType
import com.willfp.reforges.util.reforges
import org.bukkit.inventory.ItemStack

object ReforgeFinder : ItemHolderFinder<Reforge>() {
    override fun find(item: ItemStack): List<Reforge> {
        return item.reforges
    }

    override fun isValidInSlot(holder: Reforge, slot: SlotType): Boolean {
        return holder.targets.map { it.slot }.any { it.isOrContains(slot) }
    }
}