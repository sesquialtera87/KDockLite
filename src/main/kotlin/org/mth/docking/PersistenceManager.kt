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

import java.awt.Dimension
import java.awt.Point
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Utility manager responsible for capturing, serializing, and restoring the structural
 * layout configuration states of a [Workspace].
 *
 * @author Mattia Marelli
 * @since 2026
 */
object PersistenceManager {

    /**
     * Default standard storage implementation of [LayoutPersister] writing layout schemas
     * out into raw Java configuration files stream repositories.
     */
    class PropertiesLayoutPersister : LayoutPersister {
        override fun save(state: WorkspaceState, output: File) {
            try {
                state.toProperties().store(FileOutputStream(output), "")
                log.info { "Workspace layout configuration stored safely inside [${output.name}]" }
            } catch (ex: Exception) {
                log.log(Level.SEVERE, "Failed to write workspace layout persistence state", ex)
            }
        }

        override fun load(output: File): WorkspaceState? {
            try {
                val props = Properties().apply {
                    output.inputStream().use { stream -> load(stream) }
                }
                val state = props.toWorkspaceState()
                log.info { "Workspace layout successfully restored from [${output.name}]" }
                return state
            } catch (e: Exception) {
                log.log(Level.SEVERE, "Unable to restore saved layout configuration framework state", e)
                return null
            }
        }
    }

    private val log = Logger.getLogger(PersistenceManager.javaClass.name)

    /**
     * The active system persistence bridge instance engine.
     * Can be hot-swapped dynamically to support alternative serializations schemas (JSON, XML).
     */
    var layoutPersister: LayoutPersister = PropertiesLayoutPersister()

    /**
     * Captures the current runtime layout and split configurations of the targeted [Workspace].
     * Computes node topologies, window sizing bounds, sidebars states, and registers active floating nodes contexts.
     * @param workspace The active GUI container architecture context to evaluate.
     * @return A captured snapshots map tree model matching current viewport settings.
     */
    fun captureState(workspace: Workspace): WorkspaceState {
        log.fine { "Capturing workspace layout state" }

        val floatingDocks = workspace.docks
            .filter { it.floating }
            .mapNotNull { dock ->
                val dialog = SwingUtilities.windowForComponent(dock) ?: return@mapNotNull null
                FloatingDockState(
                    id = dock.id,
                    x = dialog.x,
                    y = dialog.y,
                    width = dialog.width,
                    height = dialog.height
                )
            }

        return WorkspaceState(
            leftSide = captureSideState(workspace.leftSide),
            rightSide = captureSideState(workspace.rightSide),
            southSide = captureSideState(workspace.southSide),
            leftDividerLocation = workspace.leftSplit.dividerLocation,
            rightDividerLocation = workspace.rightSplit.dividerLocation,
            southDividerLocation = workspace.southSplit.dividerLocation,
            floatingDocks = floatingDocks
        )
    }

    /**
     * Analyzes internal properties states of a specific [DockSide] pane container tracking child items allocations details.
     */
    private fun captureSideState(side: DockSide): SideState {
        val activeDock = side.components.filterIsInstance<AbstractDock>().firstOrNull { it.visibleOnScreen }
        return SideState(
            dockIds = side.docks.map { it.id },
            activeDockId = activeDock?.id,
            isCollapsed = side.isCollapsed,
            lastDividerLocation = side.lastDividerLocation
        )
    }

    /**
     * Restores a previously captured [WorkspaceState] hierarchy into an active [Workspace] layout instance.
     * Uses the workspace singleDockFactory to reconstruct instances by ID.
     * Schedules accurate divider positions nodes mappings values updates using safely isolated [SwingUtilities.invokeLater] threads wrappers.
     * @param workspace The target GUI canvas instance to modify.
     * @param state The serialized layout topology settings map schema record to deploy.
     */
    fun restoreState(workspace: Workspace, state: WorkspaceState) {
        log.fine { "Restoring workspace layout from saved state" }

        // 1. Clear current docks setup safely
        val currentDocks = ArrayList(workspace.docks)
        currentDocks.forEach { it.undock() }

        // 2. Re-dock components to their historical sides using the factory
        restoreSideDocks(workspace, state.leftSide, SwingConstants.WEST)
        restoreSideDocks(workspace, state.rightSide, SwingConstants.EAST)
        restoreSideDocks(workspace, state.southSide, SwingConstants.SOUTH)

        // 3. Re-instantiate floating docks configurations
        state.floatingDocks.forEach { floatState ->
            workspace.singleDockFactory.invoke(floatState.id)?.let { (dock, _) ->
                dock.floatDimension = Dimension(floatState.width, floatState.height)
                dock.lastFloatPosition = Point(floatState.x, floatState.y)

                workspace.leftSide.addDock(dock, fire = false)
                workspace.leftSide.detach(dock)
            }
        }

        // 4. Force synchronous UI layout updates before setting divider nodes
        workspace.revalidate()
        workspace.repaint()

        workspace.leftSide.lastDividerLocation = state.leftSide.lastDividerLocation
        workspace.rightSide.lastDividerLocation = state.rightSide.lastDividerLocation
        workspace.southSide.lastDividerLocation = state.southSide.lastDividerLocation

        // 5. Restore active visual components per side
        state.leftSide.activeDockId?.let { id -> workspace.showDock(id) }
        state.rightSide.activeDockId?.let { id -> workspace.showDock(id) }
        state.southSide.activeDockId?.let { id -> workspace.showDock(id) }

        currentDocks.forEach { dock -> workspace.dock(dock.id) }

        // 6. Recover precise divider locations mapping configurations
        SwingUtilities.invokeLater {
            workspace.doLayout()
            workspace.leftSplit.doLayout()
            workspace.rightSplit.doLayout()
            workspace.southSplit.doLayout()

            if (state.leftDividerLocation >= 0) workspace.leftSplit.dividerLocation = state.leftDividerLocation
            if (state.rightDividerLocation >= 0) workspace.rightSplit.dividerLocation = state.rightDividerLocation
            if (state.southDividerLocation >= 0) workspace.southSplit.dividerLocation = state.southDividerLocation

            workspace.revalidate()
            workspace.repaint()
        }
    }

    /**
     * Loops through an ordered collection list of serialized docking string IDs rebuilding missing component structures.
     */
    private fun restoreSideDocks(workspace: Workspace, sideState: SideState, location: Int) {
        sideState.dockIds.forEach { id ->
            if (workspace.isAlreadyDocked(id)) {
                log.fine { "Side dock $id already docked" }
            } else {
                workspace.singleDockFactory.invoke(id)?.let { (dock, _) ->
                    workspace.addDock(dock, location)
                }
            }
        }
    }
}