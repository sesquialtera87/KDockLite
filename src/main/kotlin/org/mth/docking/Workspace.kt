/*
 * MIT License
 *
 * Copyright (c) 2026 Mattia Marelli
 */

package org.mth.docking

import com.formdev.flatlaf.extras.components.FlatSplitPane
import java.awt.BorderLayout
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.*
import javax.swing.SwingConstants.*

typealias DockInfo = Pair<AbstractDock, Int>

/**
 * Main workspace container component managing layout topology, docking sidebars, split panes,
 * and persistence configurations.
 *
 * @author Mattia Marelli
 * @since 2026
 */
class Workspace private constructor(builder: WorkspaceBuilder) : JPanel() {

    /** Defines how the southern docking panel extends horizontally across the layout. */
    enum class SouthLayoutMode {
        /** South panel is constrained in the middle between West and East sidebars. */
        COMPRESSED,
        /** South panel takes the full window width underneath West and East sidebars. */
        EXTENDED
    }

    /** Fluent builder class to assemble customized [Workspace] layout topographies. */
    class WorkspaceBuilder {
        internal var singleDockFactory: (String) -> DockInfo? = { null }
        internal var southDockVisible = true
        internal var westDockVisible = true
        internal var eastDockVisible = true
        internal var southDockLocation = SOUTH
        internal var toolIconSize = 24
        internal var centralComponent: JComponent? = null
        internal var southLayoutMode = SouthLayoutMode.COMPRESSED
        internal var dividerSize = 8
        internal val docks = mapOf(
            WEST to mutableSetOf(),
            EAST to mutableSetOf(),
            SOUTH to mutableSetOf<String>(),
        )

        fun setDividerSize(size: Int): WorkspaceBuilder {
            dividerSize = size
            return this
        }

        fun southDockLocation(location: Int): WorkspaceBuilder {
            require(location == SOUTH_EAST || location == SOUTH_WEST || location == SOUTH)
            this.southDockLocation = location
            return this
        }

        fun southLayoutMode(mode: SouthLayoutMode): WorkspaceBuilder {
            this.southLayoutMode = mode
            return this
        }

        fun southDockVisible(isVisible: Boolean): WorkspaceBuilder {
            this.southDockVisible = isVisible
            return this
        }

        fun westDockVisible(isVisible: Boolean): WorkspaceBuilder {
            this.westDockVisible = isVisible
            return this
        }

        fun eastDockVisible(isVisible: Boolean): WorkspaceBuilder {
            this.eastDockVisible = isVisible
            return this
        }

        fun setSingleDockFactory(factory: (String) -> DockInfo): WorkspaceBuilder {
            this.singleDockFactory = factory
            return this
        }

        fun setToolIconSize(iconSize: Int): WorkspaceBuilder {
            this.toolIconSize = iconSize
            return this
        }

        fun setCentralComponent(component: JComponent): WorkspaceBuilder {
            this.centralComponent = component
            return this
        }

        fun dock(dockId: String, location: Int): WorkspaceBuilder {
            require(location == WEST || location == SOUTH || location == EAST)
            docks[location]?.add(dockId)
            return this
        }

        fun build(): Workspace {
            TOOL_ICON_SIZE = toolIconSize
            return Workspace(this)
        }
    }

    internal var dividerSize = builder.dividerSize
    internal var singleDockFactory: (String) -> DockInfo? = builder.singleDockFactory

    internal val southLayoutMode = builder.southLayoutMode

    val leftDockToolArea = JPanel(BorderLayout())
    val rightDockToolArea = JPanel(BorderLayout())

    val leftSplit: FlatSplitPane = FlatSplitPane()
    val rightSplit: FlatSplitPane = FlatSplitPane()
    val southSplit: FlatSplitPane = FlatSplitPane()

    val leftSide = DockSide(this, leftSplit, WEST)
    val southSide = DockSide(this, southSplit, SOUTH)
    val rightSide = DockSide(this, rightSplit, EAST)

    val docks get() = leftSide.docks + rightSide.docks + southSide.docks

    init {
        layout = BorderLayout()

        if (builder.southDockVisible && builder.southDockLocation == SOUTH) {
            southSide.setOrientation(HORIZONTAL)
            add(southSide.toolBar, BorderLayout.SOUTH)
        }

        leftDockToolArea.run {
            border = BorderFactory.createEmptyBorder(0, 3, 5, 2)
            add(leftSide.toolBar, BorderLayout.NORTH)

            if (builder.southDockVisible && builder.southDockLocation == SOUTH_WEST) {
                add(southSide.toolBar, BorderLayout.SOUTH)
            }
        }

        rightDockToolArea.run {
            border = BorderFactory.createEmptyBorder(0, 2, 5, 3)
            add(rightSide.toolBar, BorderLayout.NORTH)

            if (builder.southDockVisible && builder.southDockLocation == SOUTH_EAST) {
                add(southSide.toolBar, BorderLayout.SOUTH)
            }
        }

        leftSplit.run {
            leftComponent = leftSide
            resizeWeight = 0.0
        }

        rightSplit.run {
            rightComponent = rightSide
            resizeWeight = 1.0
        }

        southSplit.run {
            orientation = JSplitPane.VERTICAL_SPLIT
            bottomComponent = southSide
            resizeWeight = 1.0
        }

        when (southLayoutMode) {
            SouthLayoutMode.EXTENDED -> {
                rightSplit.leftComponent = builder.centralComponent
                leftSplit.rightComponent = rightSplit
                southSplit.topComponent = leftSplit
                add(southSplit, BorderLayout.CENTER)
            }

            SouthLayoutMode.COMPRESSED -> {
                southSplit.topComponent = builder.centralComponent
                rightSplit.leftComponent = southSplit
                leftSplit.rightComponent = rightSplit
                add(leftSplit, BorderLayout.CENTER)
            }
        }

        add(leftDockToolArea, BorderLayout.WEST)
        add(rightDockToolArea, BorderLayout.EAST)

        builder.docks.forEach { (location, ids) ->
            ids.forEach { id ->
                singleDockFactory.invoke(id)?.let { (dock, _) ->
                    if (isAlreadyDocked(id)) {
                        log.warning("Single dock with id $id already docked")
                    } else {
                        addDock(dock, location)
                    }
                }
            }
        }
    }

    fun isAlreadyDocked(id: String): Boolean = docks.any { it.id == id }

    fun showDock(id: String) {
        docks.firstOrNull { it.id == id }?.toFront()
    }

    fun dock(id: String) {
        singleDockFactory.invoke(id)?.let { (dock, loc) ->
            if (isAlreadyDocked(id)) {
                log.warning("Single dock with id $id already docked")
                return
            }
            addDock(dock, loc)
        }
    }

    fun addDock(tool: AbstractDock, location: Int) {
        require(location == WEST || location == EAST || location == SOUTH)
        when (location) {
            WEST -> leftSide.addDock(tool)
            EAST -> rightSide.addDock(tool)
            SOUTH -> southSide.addDock(tool)
        }
    }

    /**
     * Programmatically swaps or assigns the main center canvas viewport component,
     * routing it dynamically based on the active structural layout mode hierarchy rules.
     */
    fun setMainComponent(component: JComponent) {
        when (southLayoutMode) {
            SouthLayoutMode.EXTENDED -> rightSplit.leftComponent = component
            SouthLayoutMode.COMPRESSED -> southSplit.topComponent = component
        }
    }

    val frame: JFrame?
        get() = SwingUtilities.getWindowAncestor(this) as? JFrame

    /**
     * Saves the current workspace layout topology into a file destination.
     */
    fun saveLayoutConfiguration(targetFile: File) {
        try {
            val state = PersistenceManager.captureState(this)
            state.toProperties().store(FileOutputStream(targetFile), "")
            log.info { "Workspace layout configuration stored safely inside [${targetFile.name}]" }
        } catch (ex: Exception) {
            log.log(Level.SEVERE, "Failed to write workspace layout persistence state", ex)
        }
    }

    /**
     * Attempts to load and restore the workspace layout configuration state hierarchy from a properties file.
     */
    fun loadLayoutConfiguration(sourceFile: File) {
        if (!sourceFile.exists()) {
            log.fine { "Configuration file not found. Skipping layout restoration." }
            return
        }
        try {
            val props = Properties().apply {
                sourceFile.inputStream().use { stream -> load(stream) }
            }
            val state = props.toWorkspaceState()
            PersistenceManager.restoreState(this, state)
            log.info { "Workspace layout successfully restored from [${sourceFile.name}]" }
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Unable to restore saved layout configuration framework state", e)
        }
    }

    companion object {
        internal var TOOL_ICON_SIZE: Int = 24
        val log: Logger = Logger.getLogger(Workspace::class.java.name)
    }
}