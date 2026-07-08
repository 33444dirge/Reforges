package com.willfp.reforges.gui

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.config.emptyConfig
import com.willfp.eco.core.display.Display
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.gui.captiveSlot
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.menu.MenuEvent
import com.willfp.eco.core.gui.menu.events.CaptiveItemChangeEvent
import com.willfp.eco.core.gui.onEvent
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.CustomSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.items.builder.modify
import com.willfp.eco.core.items.isEmpty
import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.eco.core.sound.PlayableSound
import com.willfp.ecomponent.CaptiveItem
import com.willfp.ecomponent.menuStateVar
import com.willfp.ecomponent.setSlot
import com.willfp.libreforge.LibreforgeSpigotPlugin
import com.willfp.reforges.api.applyReforge
import com.willfp.reforges.api.dismantleReforges
import com.willfp.reforges.plugin
import com.willfp.reforges.reforges.PriceMultipliers.reforgePriceMultiplier
import com.willfp.reforges.reforges.Reforge
import com.willfp.reforges.reforges.ReforgeTarget
import com.willfp.reforges.reforges.ReforgeTargets
import com.willfp.reforges.util.ReforgeStatus
import com.willfp.reforges.util.getRandomReforge
import com.willfp.reforges.util.reforges
import com.willfp.reforges.util.reforgeStone
import org.bukkit.entity.Player
import kotlin.math.pow


private data class ReforgeGUIStatus(
    val status: ReforgeStatus,
    val price: ConfiguredPrice,
    val isStonePrice: Boolean
)

private class ReforgePriceChangeEvent : MenuEvent

private val Menu.reforgeStatus by menuStateVar(
    ReforgeGUIStatus(
        ReforgeStatus.NO_ITEM,
        ConfiguredPrice.createOrFree(emptyConfig()),
        false
    )
)

private object IndicatorSlot : CustomSlot() {
    private val slot = slot { player, menu ->
        val status = menu.reforgeStatus[player].status

        if (status == ReforgeStatus.ALLOW || status == ReforgeStatus.ALLOW_STONE || status == ReforgeStatus.ALLOW_DISMANTLE) {
            Items.lookup(plugin.configYml.getString("gui.show-allowed.allow-material")).item
        } else {
            Items.lookup(plugin.configYml.getString("gui.show-allowed.deny-material")).item
        }
    }

    init {
        init(slot)
    }
}

private class ActivatorSlot(
    itemToReforge: CaptiveItem,
    reforgeStone: CaptiveItem
) : CustomSlot() {
    private val slot = slot({ player, menu ->
        val (status, price) = menu.reforgeStatus[player]

        val configKey = status.configKey

        val reforgesToDismantle = if (status == ReforgeStatus.ALLOW_DISMANTLE) {
            val stone = reforgeStone[player]?.reforgeStone
            val item = itemToReforge[player]
            if (stone != null && item != null) {
                stone.getReforgesToDismantle(item).joinToString(", ") { it.name }
            } else ""
        } else ""

        val dismantleCount = if (status == ReforgeStatus.ALLOW_DISMANTLE) {
            val stone = reforgeStone[player]?.reforgeStone
            val item = itemToReforge[player]
            if (stone != null && item != null) {
                val toRemove = stone.getReforgesToDismantle(item)
                val total = toRemove.size
                val maxCount = stone.dismantleMaxCount
                if (maxCount > 0) {
                    "&c$total&7/&c$maxCount"
                } else {
                    "&c$total"
                }
            } else "&c0"
        } else "&c0"

        Items.lookup(plugin.configYml.getString("gui.$configKey.material")).modify {
            setDisplayName(plugin.configYml.getString("gui.$configKey.name"))
            addLoreLines(plugin.configYml.getStrings("gui.$configKey.lore").map {
                it.replace("%price%", price.getDisplay(player))
                    .replace("%stone%", reforgeStone[player]?.reforgeStone?.name ?: "")
                    .replace("%reforges%", reforgesToDismantle)
                    .replace("%dismantle-count%", dismantleCount)
                    // Legacy
                    .replace("%cost%", price.getDisplay(player))
                    .replace("%xpcost%", price.getDisplay(player))
            })
        }
    }) {
        onLeftClick { event, _, menu ->
            val player = event.whoClicked as Player

            val item = itemToReforge[player] ?: return@onLeftClick
            val currentReforges = item.reforges

            val targets = ReforgeTargets.getForItem(item)

            var usedStone = false

            val stoneInMenu = reforgeStone[player]?.reforgeStone

            // === 检查是否为拆卸石 ===
            if (stoneInMenu != null && stoneInMenu.dismantleEnabled) {
                if (stoneInMenu.canDismantle(item)) {
                    // ---- 拆卸逻辑 ----
                    val price = menu.reforgeStatus[player].price

                    if (!price.canAfford(player)) {
                        player.sendMessage(
                            EcoPlugin.getPlugin(LibreforgeSpigotPlugin::class.java)
                                .langYml.getMessage("cannot-afford-price").replace("%price%", price.getDisplay(player))
                        )
                        PlayableSound.create(plugin.configYml.getSubsection("gui.cannot-afford-sound"))?.playTo(player)
                        return@onLeftClick
                    }

                    // 先检查是否有可拆卸的重铸石
                    val toRemove = stoneInMenu.getReforgesToDismantle(item)
                    if (toRemove.isEmpty()) {
                        player.sendMessage(plugin.langYml.getMessage("no-reforges-to-dismantle"))
                        return@onLeftClick
                    }

                    price.pay(player)

                    // 执行拆卸（内部包含概率判定）
                    val removed = player.dismantleReforges(item, stoneInMenu)
                    if (removed.isEmpty()) {
                        // 拆卸失败（概率未通过）
                        if (stoneInMenu.consumeOnFail) {
                            // 失败且消耗石头 - 从 GUI 槽位移除
                            val stone = reforgeStone[player] ?: return@onLeftClick
                            stone.amount -= 1
                        }
                        // 否则石头留在槽位中，玩家可以再次尝试
                        player.sendMessage(plugin.langYml.getMessage("dismantle-failed"))
                        menu.callEvent(player, CaptiveItemChangeEvent(0, 0, null, null))
                        return@onLeftClick
                    }

                    // 拆卸成功
                    player.sendMessage(
                        plugin.langYml.getMessage("dismantled-reforges")
                            .replace("%count%", removed.size.toString())
                            .replace("%reforges%", removed.joinToString(", ") { it.name })
                    )

                    // 消耗拆卸石（如果不返还）
                    val stone = reforgeStone[player] ?: return@onLeftClick
                    if (!stoneInMenu.dismantleReturnStone) {
                        stone.amount -= 1
                    }

                    PlayableSound.create(plugin.configYml.getSubsection("gui.dismantle-sound"))?.playTo(player)
                    // 刷新物品显示：清除 Display 系统添加的临时 lore，客户端通过数据包自动显示
                    Display.revert(item)
                    menu.callEvent(player, CaptiveItemChangeEvent(0, 0, null, null))
                    return@onLeftClick
                } else {
                    // 拆卸石不能作用于该物品，提示错误
                    player.sendMessage(plugin.langYml.getMessage("cannot-dismantle-item"))
                    return@onLeftClick
                }
            }

            // ---- 普通重铸逻辑 ----
            val reforge = if (stoneInMenu != null && stoneInMenu.canBeAppliedTo(item) && stoneInMenu.checkReforgeConditions(player, item)) {
                usedStone = true
                stoneInMenu
            } else if (plugin.configYml.getBool("reforge.require-stone")) {
                // require-stone 模式下不允许无石重铸
                return@onLeftClick
            } else {
                targets.getRandomReforge(disallowed = currentReforges)
            }

            if (reforge == null) {
                return@onLeftClick
            }

            val price = menu.reforgeStatus[player].price

            if (!price.canAfford(player)) {
                player.sendMessage(
                    EcoPlugin.getPlugin(LibreforgeSpigotPlugin::class.java)
                        .langYml.getMessage("cannot-afford-price").replace("%price%", price.getDisplay(player))
                )
                PlayableSound.create(plugin.configYml.getSubsection("gui.cannot-afford-sound"))?.playTo(player)
                return@onLeftClick
            }

            if (!player.applyReforge(item, reforge, price, usedStone)) {
                // 重铸失败（事件被取消或概率未通过）
                if (usedStone && reforge.consumeOnFail) {
                    // 失败且消耗石头 - 从 GUI 槽位移除
                    val stone = reforgeStone[player] ?: return@onLeftClick
                    stone.amount -= 1
                }
                // 否则石头留在槽位中，玩家可以再次尝试
                player.sendMessage(plugin.langYml.getMessage("reforge-failed").replace("%reforge%", reforge.name))
                return@onLeftClick
            }

            player.sendMessage(plugin.langYml.getMessage("applied-reforge").replace("%reforge%", reforge.name))

            if (usedStone) {
                val stone = reforgeStone[player] ?: return@onLeftClick
                stone.amount -= 1
                PlayableSound.create(plugin.configYml.getSubsection("gui.stone-sound"))?.playTo(player)
            }

            PlayableSound.create(plugin.configYml.getSubsection("gui.sound"))?.playTo(player)
            // 刷新物品显示：清除 Display 系统添加的临时 lore，客户端通过数据包自动显示
            Display.revert(item)
            menu.callEvent(player, CaptiveItemChangeEvent(0, 0, null, null))
        }
    }

    init {
        init(slot)
    }
}

@Suppress("DEPRECATION")
object ReforgeGUI {
    private lateinit var menu: Menu

    private lateinit var itemToReforge: CaptiveItem
    private lateinit var reforgeStone: CaptiveItem

    private lateinit var defaultPrice: ConfiguredPrice

    fun open(player: Player) {
        menu.open(player)
    }

    internal fun update() {
        itemToReforge = CaptiveItem()
        reforgeStone = CaptiveItem()

        defaultPrice = ConfiguredPrice.createOrFree(plugin.configYml.getSubsection("reforge.price"))

        val maskPattern = plugin.configYml.getStrings("gui.mask.pattern").toTypedArray()

        val maskItems = plugin.configYml.getStrings("gui.mask.materials")
            .mapNotNull { Items.lookup(it) }
            .toTypedArray()

        menu = menu(plugin.configYml.getInt("gui.rows")) {
            title = plugin.langYml.getFormattedString("menu.title")
            setMask(FillerMask(MaskItems(*maskItems), *maskPattern))

            allowChangingHeldItem()

            val allowedPattern = plugin.configYml.getStrings("gui.show-allowed.pattern")
            for (i in 1..allowedPattern.size) {
                val row = allowedPattern[i - 1]
                for (j in 1..9) {
                    if (row[j - 1] != '0') {
                        setSlot(i, j, IndicatorSlot)
                    }
                }
            }

            setSlot(
                plugin.configYml.getInt("gui.item-slot.row"),
                plugin.configYml.getInt("gui.item-slot.column"),
                captiveSlot(),
                bindCaptive = itemToReforge
            )

            setSlot(
                plugin.configYml.getInt("gui.stone-slot.row"),
                plugin.configYml.getInt("gui.stone-slot.column"),
                captiveSlot(),
                bindCaptive = reforgeStone
            )

            setSlot(
                plugin.configYml.getInt("gui.activator-slot.row"),
                plugin.configYml.getInt("gui.activator-slot.column"),
                ActivatorSlot(itemToReforge, reforgeStone)
            )

            setSlot(
                plugin.configYml.getInt("gui.close.location.row"),
                plugin.configYml.getInt("gui.close.location.column"),
                slot(
                    ItemStackBuilder(Items.lookup(plugin.configYml.getString("gui.close.material")))
                        .setDisplayName(plugin.langYml.getFormattedString("menu.close"))
                        .build()
                ) {
                    onLeftClick { event, _, _ -> event.whoClicked.closeInventory() }
                }
            )

            onEvent<ReforgePriceChangeEvent> { player, menu, _ ->
                val status = menu.reforgeStatus[player]

                val item = itemToReforge[player]

                val reforges = item?.reforges?.size ?: 0

                var multiplier = if (status.isStonePrice) 1.0 else {
                    plugin.configYml.getDouble("reforge.cost-exponent")
                        .pow(reforges.toDouble())
                }

                multiplier *= player.reforgePriceMultiplier

                status.price.setMultiplier(player, multiplier)
            }

            onEvent<CaptiveItemChangeEvent> { player, menu, _ ->
                val item = itemToReforge[player]
                val stone = reforgeStone[player]

                val targets = mutableListOf<ReforgeTarget>()

                var price = defaultPrice

                var isStonePrice = false

                val status = if (item.isEmpty) {
                    ReforgeStatus.NO_ITEM
                } else {
                    targets.addAll(ReforgeTargets.getForItem(item))
                    if (targets.isEmpty()) {
                        ReforgeStatus.INVALID_ITEM
                    } else {
                        val reforgeStone = stone.reforgeStone
                        if (reforgeStone == null) {
                            // require-stone 模式下，没有重铸石则禁止重铸
                            if (plugin.configYml.getBool("reforge.require-stone")) {
                                ReforgeStatus.INVALID_ITEM
                            } else {
                                ReforgeStatus.ALLOW
                            }
                        } else if (reforgeStone.dismantleEnabled) {
                            // ---- 拆卸石模式 ----
                            // 拆卸石绝对不能作为普通重铸石使用
                            if (reforgeStone.canDismantle(item)) {
                                price = reforgeStone.dismantlePrice ?: reforgeStone.stonePrice ?: defaultPrice
                                isStonePrice = true
                                ReforgeStatus.ALLOW_DISMANTLE
                            } else {
                                // 物品无法被此拆卸石拆卸，显示为无效
                                ReforgeStatus.INVALID_ITEM
                            }
                        } else if (reforgeStone.canBeAppliedTo(item) && reforgeStone.checkReforgeConditions(player, item!!)) {
                            price = reforgeStone.stonePrice ?: defaultPrice
                            isStonePrice = true
                            ReforgeStatus.ALLOW_STONE
                        } else {
                            ReforgeStatus.INVALID_ITEM
                        }
                    }
                }

                menu.reforgeStatus[player] = ReforgeGUIStatus(status, price, isStonePrice)
                menu.callEvent(player, ReforgePriceChangeEvent())
            }

            onClose { event, menu ->
                DropQueue(event.player as Player)
                    .addItems(menu.getCaptiveItems(event.player as Player))
                    .setLocation(event.player.eyeLocation)
                    .forceTelekinesis()
                    .push()
            }
        }
    }
}