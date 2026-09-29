package com.example.aidetest.core

/** Common contract used by the modular tool system. Existing tools can adopt this gradually. */
interface ToolContract {
    val id: String
    val name: String
    val category: String
    val description: String

    /** Called by a future ToolHost/Router. Existing MainActivity routing remains compatible. */
    fun open()
}

data class ToolCapability(
    val history: Boolean = false,
    val favorites: Boolean = true,
    val copy: Boolean = true,
    val share: Boolean = true,
    val save: Boolean = false,
    val export: Boolean = false,
    val validation: Boolean = true
)

data class ToolMetadata(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val icon: String = "tools",
    val capability: ToolCapability = ToolCapability()
)
