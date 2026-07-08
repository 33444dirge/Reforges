package com.willfp.reforges.display

import com.willfp.eco.core.display.Display
import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.display.DisplayPriority
import com.willfp.eco.core.display.DisplayProperties
import com.willfp.eco.core.fast.FastItemStack
import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.placeholder.context.placeholderContext
import com.willfp.eco.util.SkullUtils
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.toJSON
import net.kyori.adventure.text.format.TextDecoration
import com.willfp.libreforge.ItemProvidedHolder
import com.willfp.reforges.plugin
import com.willfp.reforges.reforges.ReforgeSockets
import com.willfp.reforges.reforges.ReforgeTargets
import com.willfp.reforges.util.getReforges
import com.willfp.reforges.util.reforges
import com.willfp.reforges.util.reforgeStone
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType

@Suppress("DEPRECATION")
object ReforgesDisplay : DisplayModule(plugin, DisplayPriority.HIGH) {
    private val tempKey = plugin.namespacedKeyFactory.create("temp")

    override fun display(
        itemStack: ItemStack,
        player: Player?,
        props: DisplayProperties,
        vararg args: Any
    ) {
        val targets = ReforgeTargets.getForItem(itemStack)

        val fast = itemStack.fast()

        val stone = fast.persistentDataContainer.reforgeStone

        if (targets.isEmpty() && stone == null) {
            return
        }

        val fastItemStack = FastItemStack.wrap(itemStack)

        val lore = fastItemStack.lore

        val reforgesList = fast.persistentDataContainer.getReforges()

        val context = placeholderContext(
            player = player,
            item = itemStack
        )

        if (reforgesList.isEmpty() && stone == null) {
            if (plugin.configYml.getBool("reforge.show-reforgable")) {
                if (props.inGui) {
                    return
                }

                val addLore: MutableList<String> = ArrayList()
                for (string in plugin.configYml.getFormattedStrings("reforge.reforgable-suffix")) {
                    addLore.add(Display.PREFIX + string)
                }
                lore.addAll(addLore)
            }
        }

        if (stone != null) {
            val meta = itemStack.itemMeta
            meta.setDisplayName(stone.config.getFormattedString("stone.name"))
            val stoneMeta = stone.stone.itemMeta
            if (stoneMeta is SkullMeta) {
                val stoneTexture = SkullUtils.getSkullTexture(stoneMeta)

                if (stoneTexture != null) {
                    SkullUtils.setSkullTexture(meta as SkullMeta, stoneTexture)
                }
            }

            itemStack.itemMeta = meta

            val stoneLore = stone.config.getStrings("stone.lore")
                .map { it.replace("%price%", if (player == null) "" else stone.stonePrice?.getDisplay(player) ?: "") }
                .formatEco(player)
                .map { "${Display.PREFIX}$it" }

            lore.addAll(0, stoneLore)
        }

        if (reforgesList.isNotEmpty()) {
            if (plugin.configYml.getBool("reforge.display-in-lore")) {
                val addLore: MutableList<String> = ArrayList()
                for (reforge in reforgesList) {
                    for (string in plugin.configYml.getFormattedStrings("reforge.reforged-prefix")) {
                        addLore.add(Display.PREFIX + string.replace("%reforge%", reforge.name))
                    }
                    addLore.addAll(reforge.description.formatEco(context))
                }
                addLore.replaceAll { "${Display.PREFIX}$it" }
                lore.addAll(addLore)
            }

            if (plugin.configYml.getBool("reforge.display-in-name")) {
                val displayName = fastItemStack.displayNameComponent

                // 检查是否已经有重铸石前缀，避免重复添加
                val hasAnyReforgeInName = reforgesList.any { reforge ->
                    fastItemStack.displayName.contains(reforge.name)
                }

                if (!hasAnyReforgeInName) {
                    fastItemStack.persistentDataContainer.set(
                        tempKey,
                        PersistentDataType.STRING,
                        displayName.toJSON()
                    )

                    // 将所有重铸石名称添加到物品名前缀
                    var allReforgePrefixes = displayName
                    for (reforge in reforgesList) {
                        allReforgePrefixes = StringUtils.toComponent("${reforge.name} ")
                            .decoration(TextDecoration.ITALIC, false)
                            .append(allReforgePrefixes)
                    }

                    fastItemStack.setDisplayName(allReforgePrefixes)
                }
            }


            if (player != null) {
                for (reforge in reforgesList) {
                    val provided = ItemProvidedHolder(reforge, itemStack)

                    val lines = provided.getNotMetLines(player).map { Display.PREFIX + it }

                    if (lines.isNotEmpty()) {
                        lore.add(Display.PREFIX)
                        lore.addAll(lines)
                    }
                }
            }
        }

        // ---- 重铸石槽位显示 ----
        val socketLines = ReforgeSockets.getVisibleSockets(itemStack, reforgesList)
        if (!socketLines.isNullOrEmpty()) {
            for (line in socketLines) {
                lore.add(Display.PREFIX + line)
            }
        }

        fastItemStack.lore = lore
    }

    override fun revert(itemStack: ItemStack) {
        val fis = FastItemStack.wrap(itemStack)

        // 移除所有由 Display 系统添加的 lore 行（以 Display.PREFIX 开头）
        // 这些行是 display() 中由本模块及其他 DisplayModule 动态添加的，
        // 不应持久化到物品的 NBT 中
        val cleanedLore = fis.lore.filterNot { it.startsWith(Display.PREFIX) }
        if (cleanedLore.size != fis.lore.size) {
            fis.lore = cleanedLore.toMutableList()
        }

        if (itemStack.reforges.isEmpty()) return

        // 还原原始显示名称
        if (!plugin.configYml.getBool("reforge.display-in-name")) {
            return
        }

        if (fis.persistentDataContainer.has(tempKey, PersistentDataType.STRING)) {
            fis.setDisplayName(
                StringUtils.jsonToComponent(
                    fis.persistentDataContainer.get(
                        tempKey,
                        PersistentDataType.STRING
                    )
                )
            )

            fis.persistentDataContainer.remove(tempKey)
        }
    }
}