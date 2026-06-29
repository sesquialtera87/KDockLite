/*
 * MIT License
 *
 * Copyright (c) 2026 Mattia Marelli
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.mth.docking

import java.io.File
import java.util.Properties

/**
 * Defines the contract for serializing and deserializing the workspace layout topology.
 */
interface LayoutPersister {
    /**
     * Serializes the given [WorkspaceState] and writes it to the target file.
     * @param state The layout topology snapshot to save.
     * @param output The destination file on the disk.
     */
    fun save(state: WorkspaceState, output: File)

    /**
     * Reads a file and reconstructs the corresponding [WorkspaceState].
     * @param output The source file containing the serialized properties.
     * @return The reconstructed [WorkspaceState], or null if deserialization fails.
     */
    fun load(output: File): WorkspaceState?
}

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
 * Serializes this [WorkspaceState] topology into a flat [Properties] object.
 * Maps all layout properties, nested sub-panel side states, and floating window bounds.
 * @return A [Properties] instance populated with the serialized workspace data topology.
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
 * Represents the state of a specific docking side panel sidebar.
 * Tracks nested docks layout registration sequence order, visibility, and collapse states.
 */
data class SideState(
    val dockIds: List<String>,
    val activeDockId: String?,
    val isCollapsed: Boolean,
    val lastDividerLocation: Int
)

/**
 * Serializes this [SideState] layout configuration into a key-value [Properties] object prefixed by the side identifier name.
 * @param side The structural identifier key name of the side panel (e.g., "left", "right", "south").
 * @return A [Properties] object filled with prefixed configuration metrics matching the target side context.
 */
fun SideState.toProperties(side: String): Properties = Properties().apply {
    add("$side.dockIds", dockIds.joinToString(","))
    add("$side.active", activeDockId)
    add("$side.isCollapsed", isCollapsed)
    add("$side.lastDividerLocation", lastDividerLocation)
}

/**
 * Represents the geometry bounds and structural attributes of a detached/floating dock window frame.
 */
data class FloatingDockState(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * Serializes this [FloatingDockState] geometry context into an isolated key-value [Properties] object.
 * Prefixes entries with the format `floating.{id}.*`.
 * @return A [Properties] instance populated with explicit window coordinate parameters.
 */
fun FloatingDockState.toProperties(): Properties = Properties().apply {
    add("floating.${id}.x", x)
    add("floating.${id}.y", y)
    add("floating.${id}.width", width)
    add("floating.${id}.height", height)
}

/**
 * Reconstructs a complete structured [WorkspaceState] topology tree parsing data values inside this [Properties] entity.
 * Validates sub-components, properties keys, and filters corrupted or incomplete floating frame definitions.
 * @return The restored [WorkspaceState] graph mapping historical layouts state records.
 */
fun Properties.toWorkspaceState(): WorkspaceState {
    val floatingDockIds = getList("floating.dock")

    val floatingDocks = floatingDockIds.mapNotNull { id ->
        val x = getProperty("floating.$id.x")?.toIntOrNull()
        val y = getProperty("floating.$id.y")?.toIntOrNull()
        val width = getProperty("floating.$id.width")?.toIntOrNull()
        val height = getProperty("floating.$id.height")?.toIntOrNull()

        if (x != null && y != null && width != null && height != null) {
            FloatingDockState(id = id, x = x, y = y, width = width, height = height)
        } else {
            null
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
 * Internal private helper extension that extracts structural configuration states of an isolated side panel mapping from key keys.
 * @param side The semantic location token string used to filter properties entry structures.
 * @return An encapsulated [SideState] configuration object instance.
 */
private fun Properties.getSideState(side: String): SideState {
    val dockIdsRaw = getProperty("$side.dockIds") ?: ""
    val dockIds = if (dockIdsRaw.isBlank()) emptyList() else dockIdsRaw.split(",")

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

/**
 * Internal utility writing safe mappings translating string objects to keys while handling nullable values gracefully.
 */
internal fun Properties.add(key: String, value: Any?) {
    val v = value?.toString() ?: ""
    put(key, v)
}

/**
 * Parses a flat character separated string token value found inside the properties object matching keys mapping definitions.
 * @param key The database property key lookups target identifier string.
 * @param sep The regex string token divisor symbol default set to comma values.
 * @return A structured list of isolated string items found.
 */
internal fun Properties.getList(key: String, sep: String = ","): List<String> =
    this.getProperty(key)?.split(sep) ?: emptyList()