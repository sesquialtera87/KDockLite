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

import com.formdev.flatlaf.extras.components.FlatSplitPane
import java.awt.BorderLayout
import java.io.File
import java.util.logging.Logger
import javax.swing.*
import javax.swing.SwingConstants.*

/**
 * Declares a clean tuple type mapping shortcut binding explicit [AbstractDock] components instances
 * with integer location bounds metrics.
 */
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
        internal var centralComponent: JComponent? = null
        internal var southLayoutMode = SouthLayoutMode.COMPRESSED
        internal val docks = mapOf(
            WEST to mutableSetOf(),
            EAST to mutableSetOf(),
            SOUTH to mutableSetOf<String>(),
        )

        /**
         * Sets the physical dock alignment location constraint for southern layouts nodes.
         * @param location Bound constants (e.g., SOUTH_EAST, SOUTH_WEST, SOUTH).
         * @return This builder instance for method chaining.
         */
        fun southDockLocation(location: Int): WorkspaceBuilder {
            require(location == SOUTH_EAST || location == SOUTH_WEST || location == SOUTH)
            this.southDockLocation = location
            return this
        }

        /**
         * Sets the layout stretching behavior for the southern sidebar panel.
         * @param mode Target layout rendering logic variant strategy option.
         * @return This builder instance for method chaining.
         */
        fun southLayoutMode(mode: SouthLayoutMode): WorkspaceBuilder {
            this.southLayoutMode = mode
            return this
        }

        /** Sets whether the southern dock pane panel control element is drawn on screen. */
        fun southDockVisible(isVisible: Boolean): WorkspaceBuilder {
            this.southDockVisible = isVisible
            return this
        }

        /** Sets whether the western dock pane panel control element is drawn on screen. */
        fun westDockVisible(isVisible: Boolean): WorkspaceBuilder {
            this.westDockVisible = isVisible
            return this
        }

        /** Sets whether the eastern dock pane panel control element is drawn on screen. */
        fun eastDockVisible(isVisible: Boolean): WorkspaceBuilder {
            this.eastDockVisible = isVisible
            return this
        }

        /**
         * Injects the dynamic component initialization builder provider callback logic mapping IDs to concrete views instances.
         * @param factory The operational lambda expression factory method to bind.
         * @return This builder instance for method chaining.
         */
        fun setSingleDockFactory(factory: (String) -> DockInfo): WorkspaceBuilder {
            this.singleDockFactory = factory
            return this
        }

        /** Attaches the foundational fixed mid canvas node element rendering inside the center window. */
        fun setCentralComponent(component: JComponent): WorkspaceBuilder {
            this.centralComponent = component
            return this
        }

        /**
         * Pre-registers a specific docking layout item entry block attached to explicit sides regions bounds.
         * @param dockId The registration lookup string identifier key.
         * @param location Target structural sidebar node location code.
         * @return This builder instance for method chaining.
         */
        fun dock(dockId: String, location: Int): WorkspaceBuilder {
            require(location == WEST || location == SOUTH || location == EAST)
            docks[location]?.add(dockId)
            return this
        }

        /**
         * Assembles, normalizes layout settings properties, initializes global parameters defaults
         * and outputs a fully built [Workspace] instance.
         * @return The ready-to-render [Workspace] panel instance.
         */
        fun build(): Workspace {
            if (UIManager.getInt("kdock.divider.size") == 0) {
                UIManager.put("kdock.divider.size", 8)
            }
            return Workspace(this)
        }
    }

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

    /** Aggregates and returns a linear read-only list containing all active components handles. */
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

    /**
     * Checks if a unique target identifier has already been registered inside any localized sidebar list node.
     * @param id The component string reference lookup key.
     * @return True if the target component is already attached.
     */
    fun isAlreadyDocked(id: String): Boolean = docks.any { it.id == id }

    /** Brings a registered visible component layout view straight to the front layer of its parent dock side. */
    fun showDock(id: String) {
        docks.firstOrNull { it.id == id }?.toFront()
    }

    /**
     * Programmatically dynamically requests the construction and layout insertion of an item using global factory rules.
     * @param id The key identity of the layout node view element to mount.
     */
    fun dock(id: String) {
        singleDockFactory.invoke(id)?.let { (dock, loc) ->
            if (isAlreadyDocked(id)) {
                log.warning("Single dock with id $id already docked")
                return
            }
            addDock(dock, loc)
        }
    }

    /**
     * Appends an active customized view object component directly into the targeted panel container mapping.
     * @param tool The panel view class instance reference to load.
     * @param location Structural index targets constants (WEST, EAST, SOUTH).
     */
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
     * @param component The new center component to draw.
     */
    fun setMainComponent(component: JComponent) {
        when (southLayoutMode) {
            SouthLayoutMode.EXTENDED -> rightSplit.leftComponent = component
            SouthLayoutMode.COMPRESSED -> southSplit.topComponent = component
        }
    }

    /**
     * Looks up up the ancestral tree structure fetching the parent [JFrame] context hosting this workspace panel.
     */
    val frame: JFrame?
        get() = SwingUtilities.getWindowAncestor(this) as? JFrame

    /**
     * Saves the current workspace layout topology into a file destination.
     * @param targetFile The file instance reference where data properties are written.
     */
    fun saveLayoutConfiguration(targetFile: File) {
        PersistenceManager.run {
            layoutPersister.save(captureState(this@Workspace), targetFile)
        }
    }

    /**
     * Attempts to load and restore the workspace layout configuration state hierarchy from a properties file.
     * Aborts parsing processing cleanly if the referenced configuration file does not exist.
     * @param sourceFile The layout state properties database target file to process.
     */
    fun loadLayoutConfiguration(sourceFile: File) {
        if (!sourceFile.exists()) {
            log.info { "Configuration file not found. Skipping layout restoration." }
            return
        }

        PersistenceManager.run {
            val state = layoutPersister.load(sourceFile)

            if (state == null) {
                log.info { "Cannot load layout from file. Skipping layout restoration." }
                return
            }

            restoreState(this@Workspace, state)
        }
    }

    companion object {
        val log: Logger = Logger.getLogger(Workspace::class.java.name)
    }
}