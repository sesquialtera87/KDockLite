/*
 * MIT License
 *
 * Copyright (c) 2026 Mattia Marelli
 */

package org.mth.docking

import java.util.Properties

/**
 * Represents the complete snapshot of the workspace layout configuration for persistence.
 *
 * @author Mattia Marelli
 * @since 2026
 */
data class WorkspaceState(
    val leftSide: SideState,
    val rightSide: SideState,
    val southSide: SideState,
    val leftDividerLocation: Int,
    val rightDividerLocation: Int,
    val southDividerLocation: Int,
    val floatingDocks: List<FloatingDockState> = emptyList()
)

/**
 * Serializes this [WorkspaceState] topology into a [Properties] object.
 */
fun WorkspaceState.toProperties(): Properties = Properties().apply {
    add("leftDividerLocation", leftDividerLocation)
    add("rightDividerLocation", rightDividerLocation)
    add("southDividerLocation", southDividerLocation)

    add("floating.dock", floatingDocks.joinToString(",") { it.id })

    putAll(leftSide.toProperties("left"))
    putAll(rightSide.toProperties("right"))
    putAll(southSide.toProperties("south"))

    floatingDocks.forEach { putAll(it.toProperties()) }
}

/**
 * Represents the state of a specific docking side panel.
 */
data class SideState(
    val dockIds: List<String>,
    val activeDockId: String?,
    val isCollapsed: Boolean,
    val lastDividerLocation: Int
)

/**
 * Serializes this [SideState] layout configuration into a [Properties] object.
 */
fun SideState.toProperties(side: String): Properties = Properties().apply {
    add("$side.dockIds", dockIds.joinToString(","))
    add("$side.active", activeDockId)
    add("$side.isCollapsed", isCollapsed)
    add("$side.lastDividerLocation", lastDividerLocation)
}

/**
 * Represents the geometry and structural attributes of a detached/floating dock window.
 */
data class FloatingDockState(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * Serializes this [FloatingDockState] geometry context into a [Properties] object.
 */
fun FloatingDockState.toProperties(): Properties = Properties().apply {
    add("floating.${id}.x", x)
    add("floating.${id}.y", y)
    add("floating.${id}.width", width)
    add("floating.${id}.height", height)
}

/**
 * Ricostruisce un oggetto [WorkspaceState] a partire da un set di [Properties].
 */
fun Properties.toWorkspaceState(): WorkspaceState {
    // Recupera la lista degli ID dei dock floating salvati
    val floatingDockIds = getList("floating.dock")

    // Ricostruisce lo stato geometrico di ciascun dock floating
    val floatingDocks = floatingDockIds.mapNotNull { id ->
        val x = getProperty("floating.$id.x")?.toIntOrNull()
        val y = getProperty("floating.$id.y")?.toIntOrNull()
        val width = getProperty("floating.$id.width")?.toIntOrNull()
        val height = getProperty("floating.$id.height")?.toIntOrNull()

        if (x != null && y != null && width != null && height != null) {
            FloatingDockState(id = id, x = x, y = y, width = width, height = height)
        } else {
            null // Salta se i dati geometrici sono corrotti o incompleti
        }
    }

    return WorkspaceState(
        leftSide = getSideState("left"),
        rightSide = getSideState("right"),
        southSide = getSideState("south"),
        leftDividerLocation = getProperty("leftDividerLocation")?.toIntOrNull() ?: -1,
        rightDividerLocation = getProperty("rightDividerLocation")?.toIntOrNull() ?: -1,
        southDividerLocation = getProperty("southDividerLocation")?.toIntOrNull() ?: -1,
        floatingDocks = floatingDocks
    )
}

/**
 * Funzione di utilità interna per estrarre lo stato di un singolo lato ([SideState]).
 */
private fun Properties.getSideState(side: String): SideState {
    val dockIdsRaw = getProperty("$side.dockIds") ?: ""
    val dockIds = if (dockIdsRaw.isBlank()) emptyList() else dockIdsRaw.split(",")

    // Gestisce il valore nullo o vuoto per il dock attivo in modo sicuro
    val activeDockId = getProperty("$side.active")?.takeIf { it.isNotBlank() }

    val isCollapsed = getProperty("$side.isCollapsed")?.toBooleanStrictOrNull() ?: true
    val lastDividerLocation = getProperty("$side.lastDividerLocation")?.toIntOrNull() ?: -1

    return SideState(
        dockIds = dockIds,
        activeDockId = activeDockId,
        isCollapsed = isCollapsed,
        lastDividerLocation = lastDividerLocation
    )
}

internal fun Properties.add(key: String, value: Any?) {
    val v = value?.toString() ?: ""
    put(key, v)
}

internal fun Properties.getList(key: String, sep: String = ","): List<String> =
    this.getProperty(key)?.split(sep) ?: emptyList()