package com.willfp.reforges.util

import com.google.gson.JsonParser
import com.willfp.eco.core.fast.fast
import com.willfp.reforges.plugin
import com.willfp.reforges.reforges.Reforge
import com.willfp.reforges.reforges.ReforgeTarget
import com.willfp.reforges.reforges.Reforges
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

private val reforgeKey = plugin.namespacedKeyFactory.create("reforge")
private val reforgeStoneKey = plugin.namespacedKeyFactory.create("reforge_stone")

/**
 * 从 PersistentDataContainer 读取重铸石列表（JSON 数组格式）
 * 向后兼容：如果存储的是旧版单值格式，也会正确读取
 */
fun PersistentDataContainer.getReforges(): List<Reforge> {
    if (!this.has(reforgeKey, PersistentDataType.STRING)) return emptyList()
    val raw = this.get(reforgeKey, PersistentDataType.STRING) ?: return emptyList()
    if (raw.isEmpty()) return emptyList()

    // 尝试解析为 JSON 数组（新版格式）
    return try {
        val jsonArray = JsonParser.parseString(raw).asJsonArray
        jsonArray.mapNotNull { element -> Reforges.getByKey(element.asString) }
    } catch (e: Exception) {
        // 向后兼容：旧版单值格式
        val reforge = Reforges.getByKey(raw)
        if (reforge != null) listOf(reforge) else emptyList()
    }
}

/**
 * 将重铸石列表写入 PersistentDataContainer（JSON 数组格式）
 */
fun PersistentDataContainer.setReforges(reforges: List<Reforge>) {
    if (reforges.isEmpty()) {
        this.remove(reforgeKey)
    } else {
        val jsonArray = reforges.joinToString(",") { "\"${it.id.key}\"" }
        this.set(reforgeKey, PersistentDataType.STRING, "[$jsonArray]")
    }
}

var ItemStack?.reforges: List<Reforge>
    get() {
        this ?: return emptyList()
        return this.fast().persistentDataContainer.getReforges()
    }
    set(value) {
        this ?: return
        this.fast().persistentDataContainer.setReforges(value)
    }

var ItemMeta?.reforges: List<Reforge>
    get() {
        this ?: return emptyList()
        return this.persistentDataContainer.getReforges()
    }
    set(value) {
        this ?: return
        this.persistentDataContainer.setReforges(value)
    }

/**
 * 旧版单值属性 - 保持向后兼容
 * 读取时返回第一个重铸石，写入时替换为仅包含该重铸石的列表
 */
var ItemStack?.reforge: Reforge?
    get() {
        return this.reforges.firstOrNull()
    }
    set(value) {
        this.reforges = if (value == null) emptyList() else listOf(value)
    }

var ItemMeta?.reforge: Reforge?
    get() {
        return this.reforges.firstOrNull()
    }
    set(value) {
        this.reforges = if (value == null) emptyList() else listOf(value)
    }

var ItemStack?.reforgeStone: Reforge?
    get() {
        this ?: return null
        return this.fast().persistentDataContainer.reforgeStone
    }
    set(value) {
        this ?: return
        this.fast().persistentDataContainer.reforgeStone = value
    }

var ItemMeta?.reforgeStone: Reforge?
    get() {
        this ?: return null
        return this.persistentDataContainer.reforgeStone
    }
    set(value) {
        this ?: return
        this.persistentDataContainer.reforgeStone = value
    }

var PersistentDataContainer?.reforgeStone: Reforge?
    get() {
        this ?: return null

        if (!this.has(reforgeStoneKey, PersistentDataType.STRING)) {
            return null
        }

        val active = this.get(reforgeStoneKey, PersistentDataType.STRING)
        return Reforges.getByKey(active)
    }
    set(value) {
        this ?: return
        if (value == null) {
            this.remove(reforgeStoneKey)
        } else {
            this.set(reforgeStoneKey, PersistentDataType.STRING, value.id.key)
        }
    }

fun Collection<ReforgeTarget>.getRandomReforge(
    disallowed: Collection<Reforge> = emptyList()
): Reforge? {
    val applicable = mutableListOf<Reforge>()

    for (reforge in Reforges.values()) {
        if (reforge.targets.intersect(this.toSet()).isNotEmpty() && !reforge.requiresStone) {
            applicable.add(reforge)
        }
    }

    applicable.removeAll(disallowed)
    return applicable.randomOrNull()
}