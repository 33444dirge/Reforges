package com.willfp.reforges.reforges.util

import com.willfp.eco.core.items.args.LookupArgParser
import com.willfp.reforges.reforges.Reforge
import com.willfp.reforges.reforges.Reforges
import com.willfp.reforges.util.reforges
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.function.Predicate

object ReforgeArgParser : LookupArgParser {
    override fun parseArguments(
        args: Array<String>,
        meta: ItemMeta
    ): Predicate<ItemStack>? {
        val foundReforges = mutableListOf<Reforge>()

        for (arg in args) {
            val split = arg.split(":").toTypedArray()
            if (split.size == 1 || !split[0].equals("reforge", ignoreCase = true)) {
                continue
            }
            val match = Reforges.getByKey(split[1].lowercase()) ?: continue
            foundReforges.add(match)
        }

        if (foundReforges.isEmpty()) return null

        meta.reforges = foundReforges

        return Predicate { test ->
            val testMeta = test.itemMeta ?: return@Predicate false
            foundReforges == testMeta.reforges
        }
    }

    override fun serializeBack(meta: ItemMeta): String? {
        val reforges = meta.reforges
        if (reforges.isEmpty()) return null

        return reforges.joinToString(" ") { "reforge:${it.id}" }
    }
}