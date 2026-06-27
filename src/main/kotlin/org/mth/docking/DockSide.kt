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

import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatSplitPane
import com.formdev.flatlaf.extras.components.FlatToggleButton
import com.formdev.flatlaf.extras.components.FlatToolBar
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.*
import java.util.logging.Logger
import javax.swing.*

/**
 * Represents a specific sidebar anchor region (West, East, or South) within the workspace container hierarchy.
 * It coordinates structural toolbar buttons, handles toggle lifecycle state changes, and updates
 * collapsible split panes based on visibility transitions.
 *
 * @property workspace The root workspace container manager owning this side panel.
 * @property split The look-and-feel split pane assigned to animate and layout this side layer.
 * @property sideLocation Structural alignment parameter matching [SwingConstants] coordinates (WEST, EAST, SOUTH).
 * @author Mattia Marelli
 * @since 2026
 */
class DockSide(val workspace: Workspace, var split: FlatSplitPane, var sideLocation: Int) : JPanel() {

    /** The reactive set tracking all dock tool components currently registered to this side panel. */
    val docks: MutableSet<AbstractDock> = mutableSetOf()

    /** The specialized vector icon container hosting individual tool selection toggle triggers. */
    val toolBar: FlatToolBar = FlatToolBar().apply {
        setOrientation(FlatToolBar.VERTICAL)
        setLayout(BoxLayout(this, BoxLayout.Y_AXIS))
    }

    /** Memory cache tracking the split divider coordinate location before being collapsed out of view. */
    var lastDividerLocation: Int = -1

    /** Tracks the structural thickness of the split divider component before folding into hidden mode. */
    var lastDividerSize: Int =
        if (UIManager.getInt("kdock.divider.size") == 0) 8 else UIManager.getInt("kdock.divider.size")

    init {
        require(sideLocation == SwingConstants.WEST || sideLocation == SwingConstants.EAST || sideLocation == SwingConstants.SOUTH)

        split.dividerSize = 0
        setLayout(BorderLayout())
    }

    /**
     * Exposes the collection of underlying action behaviors bound directly to the toolbar toggle items.
     */
    val dockActions: Collection<Action>
        get() = toolBar.components
            .filterIsInstance<FlatToggleButton>()
            .map { bt -> bt.action }

    /**
     * Dynamically alters the toolbar geometric orientation and swaps sub-layout strategies safely.
     * @param orientation Layout direction constants matching [SwingConstants.HORIZONTAL] or [SwingConstants.VERTICAL].
     */
    fun setOrientation(orientation: Int) {
        require(orientation == SwingConstants.VERTICAL || orientation == SwingConstants.HORIZONTAL)

        if (toolBar.orientation == orientation) {
            return
        }

        toolBar.orientation = orientation

        when (orientation) {
            SwingConstants.HORIZONTAL -> toolBar.layout = BoxLayout(toolBar, BoxLayout.X_AXIS)
            SwingConstants.VERTICAL -> toolBar.layout = BoxLayout(toolBar, BoxLayout.Y_AXIS)
        }

        toolBar.revalidate()
        toolBar.repaint()
    }

    /**
     * Mounts and registers a tool panel view onto this side layout tree, building its toolbar selection item.
     * @param dock The abstract dockable target component to mount.
     * @param fire Flag determining whether lifecycle callbacks (`onDock`) should be notified.
     */
    fun addDock(dock: AbstractDock, fire: Boolean = true) {
        dock.side = this

        SwingUtilities.invokeLater {
            if (toolBar.componentCount != 0) {
                toolBar.add(Box.createRigidArea(Dimension(10, 5)))
            }

            dock.setUpDock()

            toolBar.add(createDockButton(dock, this))
            toolBar.revalidate()
            toolBar.repaint()
        }

        docks.add(dock)
        if (fire) dock.onDock()
    }

    /**
     * Completely unmounts and unregisters a tool window view, stripping its matching toggle item out of the toolbar.
     * @param dock The active dock component instance to remove.
     * @param fire Flag specifying whether lifecycle events (`onUndock`) should be triggered.
     */
    fun removeDock(dock: AbstractDock, fire: Boolean = true) {
        val cleanUpToolbar = {
            findButtonForDock(dock)?.let { btn ->
                val index = toolBar.getComponentIndex(btn)

                // Removes the tool action item button reference
                toolBar.remove(btn)

                // Structured spacer cleanup management to avoid dead visual paddings
                if (index > 0) {
                    val previousComponent = toolBar.getComponent(index - 1)
                    if (previousComponent is Box.Filler) {
                        toolBar.remove(previousComponent)
                    }
                } else if (toolBar.componentCount > 0) {
                    val firstComponent = toolBar.getComponent(0)
                    if (firstComponent is Box.Filler) {
                        toolBar.remove(firstComponent)
                    }
                }

                toolBar.revalidate()
                toolBar.repaint()
            }
        }

        if (dock.visibleOnScreen) {
            SwingUtilities.invokeLater {
                hideSideComponent()
                docks.remove(dock)
                cleanUpToolbar()
            }
        } else {
            docks.remove(dock)
            SwingUtilities.invokeLater { cleanUpToolbar() }
        }

        if (fire) dock.onUndock()
    }

    /** Checks whether any action toggle item inside the toolbar is currently selected. */
    val isCollapsed get() = toolBar.components.filterIsInstance<FlatToggleButton>().count { it.isSelected } != 0

    /**
     * Commands the parent split frame infrastructure to unfold, sliding out to make the requested tool view visible.
     * @param dock The registered target view to unfold and focus.
     */
    fun showComponent(dock: AbstractDock) = SwingUtilities.invokeLater {
        if (dock.visibleOnScreen) {
            log.fine { "Dock already opened" } //LOG
            return@invokeLater
        }

        // Remove preceding shown component view wrappers
        components.filterIsInstance<AbstractDock>().forEach { d -> remove(d) }

        // Hide and notify the previously focused open dock view element
        docks.filter { d -> d !== dock }
            .firstOrNull { it.visibleOnScreen }
            ?.run {
                onHide()
                visibleOnScreen = false
            }

        toolBar.components.filterIsInstance<FlatToggleButton>()
            .forEach { b -> b.setSelected(getRelatedDock(b) === dock) }

        add(dock, BorderLayout.CENTER)

        dock.visibleOnScreen = true
        dock.onShow()

        when (sideLocation) {
            SwingConstants.WEST -> {
                if (split.getLeftComponent() == null) split.setLeftComponent(this)
            }

            SwingConstants.SOUTH, SwingConstants.EAST -> {
                if (split.getRightComponent() == null) split.setRightComponent(this)
            }
        }

        if (isCollapsed) {
            if (lastDividerLocation < 0) {
                when (sideLocation) {
                    SwingConstants.WEST -> split.setDividerLocation(0.3)
                    SwingConstants.SOUTH, SwingConstants.EAST -> split.setDividerLocation(0.7)
                }
            } else {
                split.setDividerLocation(lastDividerLocation)
            }

            split.dividerSize = lastDividerSize
        }

        SwingUtilities.updateComponentTreeUI(dock)
    }

    /**
     * Collapses the active split panel viewport, sliding its dividers back into a zero-size hidden state tier.
     * @param fire Flag regulating whether hide lifecycle hooks (`onHide`) should be broadcasted.
     */
    fun hideSideComponent(fire: Boolean = true) {
        components.filterIsInstance<AbstractDock>().forEach { comp -> this.remove(comp) }

        docks.firstOrNull { it.visibleOnScreen }?.run {
            if (fire) onHide()
            visibleOnScreen = false
        }

        toolBar.components.filterIsInstance<FlatToggleButton>()
            .forEach { bt -> bt.setSelected(false) }

        lastDividerLocation = split.dividerLocation

        when (sideLocation) {
            SwingConstants.WEST -> split.setDividerLocation(0.0)
            SwingConstants.SOUTH, SwingConstants.EAST -> split.setDividerLocation(1.0)
        }

        lastDividerSize = split.dividerSize
        split.dividerSize = 0
    }

    /**
     * Swing [AbstractAction] bridge linking tool items to visibility toggles inside the layout system.
     */
    internal class DockToolAction(var dock: AbstractDock, var side: DockSide) : AbstractAction() {
        init {
            putValue(SMALL_ICON, dock.smallIcon)
            putValue(LARGE_ICON_KEY, dock.largeIcon)
            putValue(ACCELERATOR_KEY, dock.accelerator)
            putValue(NAME, dock.tooltip)
        }

        override fun actionPerformed(e: ActionEvent) {
            if (!dock.visibleOnScreen) {
                side.showComponent(dock)
            } else {
                side.hideSideComponent()
            }
        }
    }

    /**
     * Looks up and filters out the exact toggle button element associated with a given tool frame specification.
     */
    private fun findButtonForDock(dock: AbstractDock): FlatToggleButton? = toolBar.components
        .filterIsInstance<FlatToggleButton>()
        .find { getRelatedDock(it) === dock }

    /**
     * Reactive context mouse adapter triggering floating options and alignment migration popup controls on right click.
     */
    private object DockButtonListener : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount != 1) return
            if (!SwingUtilities.isRightMouseButton(e)) return

            val button = e.component as FlatToggleButton
            val dock = getRelatedDock(button)

            val closeItem = JMenuItem("Hide").apply {
                addActionListener { dock.undock() }
            }

            val floatItem = JMenuItem("Float").apply {
                addActionListener { dock.makeFloat() }
            }

            val moveToMenu = JMenu("Move to")

            mapOf(
                SwingConstants.EAST to "Right",
                SwingConstants.SOUTH to "Bottom",
                SwingConstants.WEST to "Left"
            ).forEach { (loc, text) ->
                val item = JMenuItem(text).apply {
                    isEnabled = dock.sideLocation != loc
                    addActionListener { dock.moveToSide(loc) }
                }
                moveToMenu.add(item)
            }

            JPopupMenu().apply {
                add(closeItem)
                add(floatItem)
                add(moveToMenu)

                show(e.component, e.x, e.y)
            }
        }
    }

    /**
     * Extracts and detaches a tool pane component, wrapping it safely inside an autonomous floating utility window frame.
     * @param dock The dock component target destined for detachment.
     */
    fun detach(dock: AbstractDock) {
        removeDock(dock, false)

        val screenSize = Toolkit.getDefaultToolkit().screenSize

        SwingUtilities.invokeLater {
            docks.add(dock)
            dock.header.isVisible = false

            JDialog(workspace.frame, false).apply {
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                type = Window.Type.UTILITY
                title = dock.title
                contentPane = dock

                dock.smallIcon?.let { setIconImage(it.toImage()) }

                preferredSize = dock.floatDimension ?: when (sideLocation) {
                    SwingConstants.SOUTH -> Dimension(screenSize.width * 3 / 4, 400)
                    else -> Dimension(400, screenSize.height * 3 / 4)
                }

                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) {
                        dock.run {
                            floatDimension = this@apply.size
                            lastFloatPosition = this@apply.location
                            header.isVisible = true
                        }
                        addDock(dock)
                    }
                })

                pack()
                setLocationRelativeTo(workspace.frame)

                dock.lastFloatPosition?.let { location = it }

                isVisible = true
            }
        }
    }

    /**
     * Migrates a registered tool window across onto an alternative structural sidebar alignment location.
     * @param dock The dock target pane shifting positions.
     * @param location Target placement coordinates (WEST, EAST, SOUTH).
     */
    fun move(dock: AbstractDock, location: Int) {
        require(location == SwingConstants.WEST || location == SwingConstants.EAST || location == SwingConstants.SOUTH)

        removeDock(dock)

        when (location) {
            SwingConstants.EAST -> workspace.rightSide.addDock(dock)
            SwingConstants.WEST -> workspace.leftSide.addDock(dock)
            SwingConstants.SOUTH -> workspace.southSide.addDock(dock)
        }
    }

    companion object {

        private val log = Logger.getLogger(DockSide::class.java.name)

        /**
         * Resolves the matching inner [AbstractDock] link assigned to a toolbar item's action context.
         */
        private fun getRelatedDock(button: FlatToggleButton): AbstractDock {
            val action = button.action as DockToolAction
            return action.dock
        }

        /**
         * Factory utility initializer configuring an integrated, theme-aware action toggle item for sidebar layouts.
         */
        fun createDockButton(dock: AbstractDock, parent: DockSide) = FlatToggleButton().apply {
            val action = DockToolAction(dock, parent)
            setAction(action)
            text = null
            toolTipText = action.getValue(Action.NAME)?.toString() ?: ""
            alignmentX = 0.5f
            isFocusable = false
            buttonType = FlatButton.ButtonType.toolBarButton
            addMouseListener(DockButtonListener)
        }
    }
}