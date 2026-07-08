package com.willfp.reforges.config

import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.StaticBaseConfig
import com.willfp.libreforge.loader.LibreforgePlugin

/**
 * 拆卸物品映射配置（dismantle-mappings.yml）
 *
 * 当拆卸重铸石时，如果该重铸石的 ID 在此映射中，则给予对应的物品而非原重铸石。
 * 这可以用于兼容 CraftEngine 等自定义物品插件。
 */
class DismantleMappingsConfig(
    plugin: LibreforgePlugin
) : StaticBaseConfig("dismantle-mappings", plugin, ConfigType.YAML) {

    /**
     * 获取指定重铸石 ID 对应的映射物品 ID。
     * 
     * @param reforgeId 重铸石的 namespaced key（即配置文件名）
     * @return 映射的物品 ID，如果没有映射则返回 null
     */
    fun getMapping(reforgeId: String): String? {
        return if (has("mappings.$reforgeId")) {
            getString("mappings.$reforgeId")
        } else null
    }

    /**
     * 检查指定重铸石 ID 是否有映射配置。
     */
    fun hasMapping(reforgeId: String): Boolean {
        return has("mappings.$reforgeId")
    }

    /**
     * 获取所有映射条目的数量。
     */
    val mappingCount: Int
        get() = if (has("mappings")) getSubsection("mappings").getKeys(false).size else 0
}