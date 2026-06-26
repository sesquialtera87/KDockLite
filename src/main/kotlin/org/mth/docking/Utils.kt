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

import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Converts any Swing [Icon] into an AWT [Image].
 * This extension function provides a polymorphic way to extract or render
 * an image from any [Icon] implementation (such as `ImageIcon`, vector-based `FontIcon`
 * from Ikonli, or FlatLaf SVG icons).
 *
 * ### Implementation Details
 * - **Fast Path:** If the icon is already an instance of [ImageIcon], it returns its underlying native [Image]
 * directly without triggering an expensive redraw operation.
 * - **Fallback Path:** For other icon types, it allocates a [BufferedImage] in memory with an alpha channel (`TYPE_INT_ARGB`)
 * matching the icon's dimensions (falling back to 16x16 pixels if dimensions are invalid) and paints the icon onto its graphics context.
 *
 * @return An [Image] representation of the icon, guaranteed to be compatible with AWT/Swing components
 * that require a bitmap image (e.g., [java.awt.Window.setIconImage]).
 * @see Icon
 * @see BufferedImage
 * @author Mattia Marelli
 * @since 2026
 */
internal fun Icon.toImage(): Image {
    // Fast path: if it's already an ImageIcon, extract the native image without redrawing
    if (this is ImageIcon) return this.image

    // Fallback path: create a BufferedImage with the exact dimensions of the icon
    val width = if (iconWidth > 0) iconWidth else 16
    val height = if (iconHeight > 0) iconHeight else 16

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d: Graphics2D = image.createGraphics()

    try {
        // Paint the icon (regardless of its type, including FontIcon) onto the graphics buffer
        this.paintIcon(null, g2d, 0, 0)
    } finally {
        g2d.dispose() // Always release graphics resources
    }

    return image
}

/**
 * A specialized Kotlin property delegate that automates Java Beans `PropertyChangeEvent` notifications
 * for Swing [JComponent] properties.
 *
 * This implementation accesses the public [JComponent.propertyChangeListeners] array to propagate
 * changes safely, allowing [javax.beans.PropertyChangeListenerProxy] instances to automatically filter
 * named properties without event duplication.
 *
 * @param T The type of the property being encapsulated.
 * @param comp The target [JComponent] host that will dispatch the property change notifications.
 * @param initialValue The default fallback baseline state assigned to the property wrapper.
 * @return A [ReadWriteProperty] instance capable of intercepting setter triggers.
 */
internal fun <T> swingProperty(comp: JComponent, initialValue: T): ReadWriteProperty<Any, T> =
    object : ReadWriteProperty<Any, T> {
        private var value = initialValue

        override fun getValue(thisRef: Any, property: KProperty<*>): T = value

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            val oldValue = this.value
            if (oldValue != value) {
                this.value = value

                // Creiamo l'evento standard dei Java Beans
                val event = java.beans.PropertyChangeEvent(comp, property.name, oldValue, value)

                // Sfruttiamo l'array pubblico dei listener. I listener con nome (Proxy)
                // filtreranno automaticamente l'evento in base a property.name senza duplicazioni.
                comp.propertyChangeListeners.forEach { listener ->
                    listener.propertyChange(event)
                }
            }
        }
    }

/**
 * Global fallback typography color token mapped directly from the active LookAndFeel [UIManager] context.
 * Resolves the primary foreground boundary color assigned to standard label component structures.
 */
internal val DEFAULT_COLOR get() = UIManager.getColor("Label.foreground")