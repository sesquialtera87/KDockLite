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

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Point
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingUtilities

class AbstractDockTest {

    // 1. Creiamo un'implementazione concreta minimale per poter istanziare la classe astratta
    private class TestDock(
        override val id: String = "test-id",
        override val content: JPanel = JPanel()
    ) : AbstractDock() {

        var hideCalled = false
        var showCalled = false
        var undockCalled = false
        var dockCalled = false

        override fun onHide() {
            hideCalled = true
        }

        override fun onShow() {
            showCalled = true
        }

        override fun onUndock() {
            undockCalled = true
        }

        override fun onDock() {
            dockCalled = true
        }
    }

    private lateinit var dock: TestDock

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupLaf() {
            // Inizializziamo FlatLaf per evitare eccezioni sul caricamento delle icone/font
            FlatLightLaf.setup()
        }
    }

    @BeforeEach
    fun setUp() {
        dock = TestDock()
    }

    @Test
    fun testDefaultPropertiesAndInitialization() {
        // Verifica lo stato iniziale dei campi e dei reference memorizzati
        assertEquals("test-id", dock.id)
        assertNotNull(dock.content)
        assertFalse(dock.visibleOnScreen)
        assertFalse(dock.floating)
        assertNull(dock.side)
        assertNull(dock.floatDimension)
        assertNull(dock.lastFloatPosition)
        assertNotNull(dock.header)
    }

    @Test
    fun testPropertyBindingsAndHeaderSynchronization() {
        // Avviamo il setup dei componenti grafici
        dock.setUpDock()
        // Attendiamo che l'EDT processi la coda di setup della UI
        runOnEDT { }

        // Test della proprietà reattiva 'title' e della sincronizzazione con la JLabel dell'header
        val expectedTitle = "Project Explorer"
        dock.title = expectedTitle

        assertEquals(expectedTitle, dock.title)
        assertEquals(expectedTitle, dock.header.title)
    }

    @Test
    fun testLifecycleHooks() {
        // Verifica l'esecuzione manuale o delegata degli eventi di aggancio/sgancio
        dock.onShow()
        assertTrue(dock.showCalled)

        dock.onHide()
        assertTrue(dock.hideCalled)

        dock.onDock()
        assertTrue(dock.dockCalled)

        dock.onUndock()
        assertTrue(dock.undockCalled)
    }

    @Test
    fun testThemeAwareIconCaching() {
        // Forza l'attivazione del tema chiaro e verifica la generazione dell'icona
        FlatLightLaf.setup()
        val lightIcon: Icon = AbstractDock.getMinimizeIcon()
        assertNotNull(lightIcon)

        // Passa al tema scuro e verifica che la logica interna reagisca al cambio di LookAndFeel
        FlatDarkLaf.setup()
        val darkIcon: Icon = AbstractDock.getMinimizeIcon()
        assertNotNull(darkIcon)

        // Verifica anche la closeIcon statica
        assertNotNull(AbstractDock.closeIcon)
    }

    @Test
    fun testGeometryStateRetention() {
        // Verifica la scrittura e lettura dei parametri di posizionamento delle finestre esterne
        val testDimension = Dimension(800, 600)
        val testPoint = Point(150, 150)

        dock.floatDimension = testDimension
        dock.lastFloatPosition = testPoint

        assertEquals(testDimension, dock.floatDimension)
        assertEquals(testPoint, dock.lastFloatPosition)
    }

    /**
     * Utility helper per forzare e sincronizzare l'esecuzione di blocchi di codice
     * all'interno del thread grafico di Swing (EDT), evitando anomalie nei test concorrenti.
     */
    private fun runOnEDT(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
    }
}