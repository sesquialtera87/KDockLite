/*
 * MIT License
 *
 * Copyright (c) 2026 Mattia Marelli
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.mth.docking

import java.awt.Dimension
import java.awt.Point
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

    private val log = Logger.getLogger(PersistenceManager.javaClass.name)

    /**
     * Captures the current runtime layout and split configurations of the targeted [Workspace].
     */
    fun captureState(workspace: Workspace): WorkspaceState {
        log.fine { "Capturing workspace layout state" } // LOG

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
     */
    fun restoreState(workspace: Workspace, state: WorkspaceState) {
        log.fine { "Restoring workspace layout from saved state" } // LOG

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

                // We add it to a temporary side, then detach it immediately
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

            // Ora che la UI sa esattamente quanto è grande ogni sezione, iniettiamo i valori salvati
            if (state.leftDividerLocation >= 0) workspace.leftSplit.dividerLocation = state.leftDividerLocation
            if (state.rightDividerLocation >= 0) workspace.rightSplit.dividerLocation = state.rightDividerLocation
            if (state.southDividerLocation >= 0) workspace.southSplit.dividerLocation = state.southDividerLocation

            // Un ultimo rinfresco per stampare a video il risultato finale corretto
            workspace.revalidate()
            workspace.repaint()
        }
    }

    private fun restoreSideDocks(workspace: Workspace, sideState: SideState, location: Int) {
        sideState.dockIds.forEach { id ->
            if (workspace.isAlreadyDocked(id)) {
                log.fine { "Side dock $id already docked" } // LOG
            } else {
                workspace.singleDockFactory.invoke(id)?.let { (dock, _) ->
                    workspace.addDock(dock, location)
                }
            }
        }
    }
}