package com.willfp.reforges.reforges

import com.willfp.eco.core.config.config
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.price.ConfiguredPrice
import com.willfp.eco.core.recipe.Recipes
import com.willfp.eco.core.recipe.recipes.CraftingRecipe
import com.willfp.eco.core.registry.Registrable
import com.willfp.eco.util.StringUtils
import com.willfp.libreforge.Holder
import com.willfp.libreforge.ItemProvidedHolder
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.reforges.plugin
import com.willfp.reforges.util.reforgeStone
import com.willfp.reforges.util.reforges
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.Objects

@Suppress("DEPRECATION")
class Reforge(
    id: String,
    internal val config: Config
) : Holder, Registrable {
    val name = config.getFormattedString("name")

    val namePrefixComponent = StringUtils.toComponent("$name ").decoration(TextDecoration.ITALIC, false)

    val description: List<String> = config.getStrings("description")

    val targets = config.getStrings("targets").mapNotNull { ReforgeTargets.getByName(it) }.toSet()

    override val effects = Effects.compile(
        config.getSubsections("effects"),
        ViolationContext(plugin, "Reforge $id")
    )

    override val conditions = Conditions.compile(
        config.getSubsections("conditions"),
        ViolationContext(plugin, "Reforge $id")
    )

    /**
     * 重铸条件：在重铸时检测（区别于 conditions，后者在运行时检测效果是否触发）
     * 配置在 reforge_conditions 下
     */
    val reforgeConditions: ConditionList = Conditions.compile(
        config.getSubsections("reforge_conditions"),
        ViolationContext(plugin, "Reforge $id").with("reforge_conditions")
    )

    override val id = plugin.createNamespacedKey(id)

    val requiresStone = config.getBool("stone.enabled")

    val stone: ItemStack = ItemStackBuilder(Items.lookup(config.getString("stone.item")).item).apply {
        if (config.getBool("stone.enabled")) {
            setDisplayName(config.getFormattedString("stone.name").replace("%reforge%", name))
            // lore 由 ReforgesDisplay 在显示时动态添加，不需要写入 NBT
        }
    }.build()

    val stonePrice = if (config.has("stone.price")) {
        when {
            // Legacy support
            config.getDouble("stone.price") > 0 -> {
                ConfiguredPrice.createOrFree(
                    config {
                        "value" to config.getDouble("stone.price")
                        "type" to "coins"
                        "display" to "%value%"
                    }
                )
            }

            else -> ConfiguredPrice.createOrFree(config.getSubsection("stone.price"))
        }
    } else null

    // ---- 统一概率配置 ----
    // 如果 dismantle.enabled = true，则此概率作用于拆卸；否则作用于重铸
    val chance: Double = if (config.has("chance")) config.getDouble("chance") else 1.0
    val consumeOnFail: Boolean = if (config.has("consume-on-fail")) config.getBool("consume-on-fail") else true
    val failSound: Config? = if (config.has("fail-sound")) config.getSubsection("fail-sound") else null

    // ---- 拆卸配置 ----
    val dismantleEnabled: Boolean = config.getBool("dismantle.enabled")

    val dismantleReforges: List<String> = if (dismantleEnabled) {
        config.getStrings("dismantle.reforges")
    } else emptyList()

    val dismantleReturnStone: Boolean = if (dismantleEnabled) {
        config.getBool("dismantle.return-stone")
    } else false

    val dismantleReturnDismantledReforges: Boolean = if (dismantleEnabled) {
        config.getBool("dismantle.return-dismantled-reforges")
    } else false

    val dismantleMaxCount: Int = if (dismantleEnabled) {
        config.getInt("dismantle.max-count")
    } else -1

    val dismantlePrice: ConfiguredPrice? = if (dismantleEnabled && config.has("dismantle.price")) {
        ConfiguredPrice.createOrFree(config.getSubsection("dismantle.price"))
    } else null

    /**
     * 判断这个重铸石是否是拆卸石，并且能否对指定物品进行拆卸
     */
    fun canDismantle(item: ItemStack?): Boolean {
        if (!dismantleEnabled) return false
        if (item == null) return false
        val itemReforges = item.reforges
        if (itemReforges.isEmpty()) return false

        return if (dismantleReforges.contains("*")) {
            true // 可以拆卸所有重铸石
        } else {
            itemReforges.any { it.id.key in dismantleReforges }
        }
    }

    /**
     * 获取此拆卸石可以从物品上移除的重铸石列表
     * 受 max-count 配置限制
     */
    fun getReforgesToDismantle(item: ItemStack?): List<Reforge> {
        if (!dismantleEnabled || item == null) return emptyList()
        val itemReforges = item.reforges
        if (itemReforges.isEmpty()) return emptyList()

        val matched = if (dismantleReforges.contains("*")) {
            itemReforges.toList()
        } else {
            itemReforges.filter { it.id.key in dismantleReforges }
        }

        // 受 max-count 限制（-1 表示不限制）
        return if (dismantleMaxCount > 0 && matched.size > dismantleMaxCount) {
            matched.take(dismantleMaxCount)
        } else {
            matched
        }
    }

    private val onReforgeEffects = Effects.compileChain(
        config.getSubsections("on-reforge-effects"),
        ViolationContext(plugin, "Reforge $id").with("on-reforge-effects")
    )

    init {
        stone.reforgeStone = this

        if (config.getBool("stone.enabled")) {
            CustomItem(
                plugin.namespacedKeyFactory.create("stone_" + this.id.key),
                { test -> test.reforgeStone == this },
                stone
            ).register()

            val stoneRecipe: CraftingRecipe? = config.getBool("stone.craftable")
                .takeIf { it }
                ?.let {
                    Recipes.createAndRegisterRecipe(
                        plugin,
                        "stone_" + this.id.key,
                        stone,
                        config.getStrings("stone.recipe"),
                        config.getStringOrNull("stone.recipe-permission"),
                        config.getBool("stone.shapeless")
                    )
                }
        }
    }

    fun canBeAppliedTo(item: ItemStack?): Boolean {
        return targets.any { target -> target.items.any { it.matches(item) } }
    }

    /**
     * 检查重铸条件（reforge_conditions）是否满足
     * @return true 表示条件满足（或没有配置 reforge_conditions），可以重铸
     */
    fun checkReforgeConditions(player: Player, item: ItemStack): Boolean {
        if (reforgeConditions.isEmpty()) return true
        val dispatcher = player.toDispatcher()
        val holder = ItemProvidedHolder(this, item)
        return reforgeConditions.areMet(dispatcher, holder)
    }

    fun runOnReforgeEffects(player: Player, item: ItemStack) {
        onReforgeEffects?.trigger(
            player.toDispatcher(),
            TriggerData(
                player = player,
                item = item
            )
        )
    }

    override fun getID(): String {
        return this.id.key
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is Reforge) {
            return false
        }

        return other.id == this.id
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return "Reforge{$id}"
    }
}