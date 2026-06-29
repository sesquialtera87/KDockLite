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

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import java.awt.*
import java.util.*
import javax.swing.*

/**
 * Base abstract class representing a dockable tool window panel within the workspace hierarchy.
 * It encapsulates life-cycle events, floating states, layout positioning, and an integrated top header.
 *
 * @author Mattia Marelli
 * @since 2026
 */
abstract class AbstractDock : JPanel() {

    @Suppress("HardCodedStringLiteral")
    companion object {
        /**
         * Lazily retrieves or puts the vectorized minimize icon into the [UIManager],
         * adapting dynamically to the current LookAndFeel brightness theme mode.
         * @return The cached theme-aware [Icon] resource.
         */
        fun getMinimizeIcon(): Icon {
            val key = "kdock.icon.minimize"
            return UIManager.getIcon(key) ?: run {
                val path =
                    if (FlatLaf.isLafDark()) "org/mth/docking/minimize_dark.svg" else "org/mth/docking/minimize.svg"
                FlatSVGIcon(path, 18, 18, Workspace::class.java.classLoader).also { UIManager.put(key, it) }
            }
        }

        /**
         * Cached theme-aware close/hide [Icon] mapped directly inside the global LookAndFeel context.
         */
        val closeIcon: Icon
            get() {
                val key = "kdock.icon.close"
                return UIManager.getIcon(key) ?: run {
                    val path =
                        if (FlatLaf.isLafDark()) "org/mth/docking/close_dark.svg" else "org/mth/docking/close.svg"
                    FlatSVGIcon(path, 18, 18, Workspace::class.java.classLoader).also { UIManager.put(key, it) }
                }
            }

        /**
         * Fallback typography font family model used globally by the docking layout title labels.
         */
        val headerFont: Font
            get() = UIManager.getFont("kdock.header.font") ?: UIManager.getFont("h4.font")

        /**
         * Resolves the background color for the dock header bar, fallbacking to the theme's default menu item background.
         */
        val headerBackground: Color
            get() = UIManager.getColor("kdock.header.background")
                ?: UIManager.getColor("MenuItem.background")

        /**
         * Structural resource bundle containing internal localizations for tooltips and accessibility parameters.
         */
        val bundle: ResourceBundle = ResourceBundle.getBundle("org.mth.docking.i18n")
    }

    /** Unique alphanumeric identifier assigned to this specific tool view component. */
    abstract val id: String

    /** The real working canvas component wrapped inside this container panel. */
    abstract val content: JComponent

    /** Optional shortcut global key assignment to programmatically toggle this dock's visibility. */
    open val accelerator: KeyStroke? = null

    /** Compact small icon descriptor used when rendering minimized tool area action bars. */
    open var smallIcon: Icon? = null

    /** High-resolution icon applied directly into the main localized top window descriptor header. */
    open var largeIcon: Icon? = null

    /** Internal check state property tracking whether this dock component is currently mounted on screen. */
    var visibleOnScreen: Boolean = false

    /** Reference pointing directly to the structural side container owner currently anchoring this dock view. */
    var side: DockSide? = null

    /** Top contextual title bar containing localized structural captions and control interaction triggers. */
    open val header: AbstractDockHeader = DefaultHeader(this)

    /** Historical dimensions memory buffer assigned and modified when detached into external float dialog blocks. */
    var floatDimension: Dimension? = null

    /** Geometric desktop coordinates reference specifying the precise placement on screen when detached. */
    var lastFloatPosition: Point? = null

    /** Reactive localized string title property binding. Triggers global [java.beans.PropertyChangeEvent] notifications. */
    var title: String? by swingProperty(this, null)

    /** Accessibility description string tooltip applied as structural hints across menu entries and toolbars. */
    var tooltip: String? by swingProperty(this, null)

    /**
     * Determines whether this dock component is currently detached and floating inside an autonomous utility dialog frame.
     */
    val floating: Boolean
        get() = visibleOnScreen && SwingUtilities.windowForComponent(this) is JDialog

    /** Lifecycle hook executed right after the dock has been hidden or collapsed from the screen canvas view tree. */
    open fun onHide() {}

    /** Lifecycle hook executed right after the component becomes active and visible on the user desktop canvas. */
    open fun onShow() {}

    /** Lifecycle hook triggered when this specific dock is completely unmounted from its toolbar side anchor. */
    open fun onUndock() {}

    /** Lifecycle hook executed when this component is successfully attached to a structural side action bar. */
    open fun onDock() {}

    /**
     * Structurally sets up the internal component hierarchy layout tree, pinning the control header
     * and the viewport content safely inside the Event Dispatch Thread (EDT).
     */
    fun setUpDock() = SwingUtilities.invokeLater {
        layout = BorderLayout()
        header.title = title
        header.icon = largeIcon

        add(header, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)

        revalidate()
        repaint()
    }

    /** Programmatically shifts the focus hierarchy tree, bringing this dock pane to the front layout tier. */
    fun toFront() = side?.showComponent(this)

    /** Minimizes and collapses the active host sidebar panel holding this dock layout framework. */
    fun minimize() = side?.hideSideComponent()

    /** Completely detaches and unregisters this window component from its sidebar container layout tree. */
    fun undock() {
        side?.removeDock(this)
    }

    /** Extracts this dock container from its internal split frame layout, wrapping it inside a floating utility dialog window. */
    fun makeFloat() = side?.detach(this)

    /**
     * Migrates this dock component onto an alternative target window side container location.
     * @param location Structural target direction matching [SwingConstants] layout parameters (WEST, EAST, SOUTH).
     */
    fun moveToSide(location: Int) = side?.move(this, location)

    /** Reads the precise alignment position identifier where this view's anchor side is deployed. */
    val sideLocation: Int? get() = side?.sideLocation

    /**
     * Base contract descriptor representing the top operational toolbar header pinned above a dock view.
     * Custom header designs must extend this class to bridge title and icon state assignments.
     */
    abstract class AbstractDockHeader(val dock: AbstractDock) : JPanel() {
        /** The main text title caption rendered inside the header layout area. */
        abstract var title: String?

        /** The primary graphic brand icon pinned next to the header text caption. */
        abstract var icon: Icon?
    }

    /**
     * Standard contextual layout component pinned on top of the dock canvas frame to host caption labels
     * and operational action button items.
     */
    class DefaultHeader(dock: AbstractDock) : AbstractDockHeader(dock) {

        /** Action button used to minimize the active dock sidebar panel container. */
        val minimizeButton = FlatButton().apply {
            icon = getMinimizeIcon()
            buttonType = FlatButton.ButtonType.toolBarButton
            isFocusable = false
            toolTipText = bundle.getString("minimize.tooltip")
            name = "btn:minimize"
            addActionListener { dock.minimize() }
        }

        /** Action button used to completely close or hide the parent dock element. */
        val hideButton = FlatButton().apply {
            icon = closeIcon
            buttonType = FlatButton.ButtonType.toolBarButton
            isFocusable = false
            toolTipText = bundle.getString("hide.tooltip")
            name = "btn:hide"
            addActionListener { dock.undock() }
        }

        /** Text label backing container displaying the active dock descriptive title string. */
        internal val titleLabel = JLabel().apply {
            iconTextGap = 10
            isOpaque = false
            font = headerFont
            UIManager.getColor("kdock.header.foreground")?.let { this.foreground = it }
        }

        /** Sub-panel container grouping functional structural action window buttons. */
        val toolBar = JPanel().apply {
            layout = FlowLayout(FlowLayout.RIGHT, 0, 0)
            isOpaque = false
            add(minimizeButton)
            add(hideButton)
        }

        /** Maps the abstract title property directly into the wrapped [titleLabel] component. */
        override var title: String?
            get() = titleLabel.text
            set(value) {
                titleLabel.text = value
            }

        /** Maps the abstract icon property directly into the wrapped [titleLabel] component. */
        override var icon: Icon?
            get() = titleLabel.icon
            set(value) {
                titleLabel.icon = value
            }

        init {
            layout = BorderLayout()
            border = BorderFactory.createEmptyBorder(5, 7, 6, 2)

            add(titleLabel, BorderLayout.WEST)
            add(toolBar, BorderLayout.EAST)

            dock.addPropertyChangeListener("title") {
                titleLabel.text = it.newValue.toString()
            }

            isOpaque = true
            background = headerBackground
        }
    }
}