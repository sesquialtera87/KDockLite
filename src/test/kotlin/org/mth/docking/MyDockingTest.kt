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

import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.FlatDarkLaf
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.*

/**
 * Un'implementazione concreta di un dock per il test.
 */
class DemoTool(
    override val id: String,
    title: String,
    backgroundColor: Color,
    val preferredLocation: Int
) : AbstractDock() {

    override val content: JComponent =
        JScrollPane(JTextArea("Contenuto del dock: $title\n\nPuoi minimizzarmi, trascinarmi o rendermi float.")).apply {
            border = BorderFactory.createEmptyBorder()
        }

    init {
        this.title = title
        // Usiamo icone di default di FlatLaf per il test senza dipendere da file SVG esterni
        this.largeIcon = UIManager.getIcon("Tree.leafIcon")

        content.background = backgroundColor
        setUpDock() // Inizializza l'header e il contenuto
    }

    override fun onHide() {
        log.fine { "Dock $id nascosto" } // LOG
    }

    override fun onShow() {
        log.fine { "Dock $id mostrato" } // LOG
    }
}

val log: Logger = Logger.getLogger("DemoTool")

fun main() {
    Logger.getLogger("").handlers.forEach { handler ->
        if (handler is java.util.logging.ConsoleHandler) {
            handler.level = Level.FINE
        }
    }

    log.level = Level.ALL

    // Configura FlatLaf prima di avviare l'UI
    UIManager.setLookAndFeel(FlatDarculaLaf())

    // Ottimizzazioni grafiche globali consigliate per un look minimale stile IDE
    UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder())
    UIManager.put("Component.focusWidth", 0)
    UIManager.put("Component.innerFocusWidth", 0)

    val frame = JFrame("KDockLite - Framework Demo App")

    // 3. Registra la Factory per la persistenza del layout
    val dockRegistry = mutableMapOf<String, DemoTool>()

    fun createAndRegisterTool(id: String, title: String, color: Color, location: Int): DemoTool {
        return DemoTool(id, title, color, location).also { dockRegistry[id] = it }
    }

    val centralComponent = JPanel(BorderLayout()).apply {
        background = Color(35, 38, 40)
        border = BorderFactory.createLineBorder(Color(50, 53, 55), 1)
        add(JLabel("AREA CENTRALE FISSA (Editor principale)", SwingConstants.CENTER).apply {
            foreground = Color.LIGHT_GRAY
        }, BorderLayout.CENTER)
    }

    // Creiamo i componenti iniziali
    val projectView = createAndRegisterTool("project.view", "Project Explorer", Color(43, 45, 48), SwingConstants.WEST)
    val structureView = createAndRegisterTool("structure.view", "Structure", Color(43, 45, 48), SwingConstants.WEST)
    val consoleView = createAndRegisterTool("console.view", "Terminal & Output", Color(30, 31, 34), SwingConstants.SOUTH)
    val propertiesView = createAndRegisterTool("properties.view", "Properties", Color(43, 45, 48), SwingConstants.EAST)

    SwingUtilities.invokeLater {
        val workspace = Workspace.WorkspaceBuilder()
            .setCentralComponent(centralComponent)
            .southDockVisible(true)
            .southDockLocation(SwingConstants.SOUTH_WEST)
            .dock(projectView.id,projectView.preferredLocation)
            .dock(structureView.id,structureView.preferredLocation)
            .dock(consoleView.id,consoleView.preferredLocation)
            .dock(propertiesView.id,propertiesView.preferredLocation)
            .setSingleDockFactory { id ->
                val dock = dockRegistry[id] ?: when (id) {
                    "project.view" -> createAndRegisterTool("project.view", "Project Explorer", Color(43, 45, 48), SwingConstants.WEST)
                    "structure.view" -> createAndRegisterTool("structure.view", "Structure", Color(43, 45, 48), SwingConstants.WEST)
                    "console.view" -> createAndRegisterTool("console.view", "Terminal & Output", Color(30, 31, 34), SwingConstants.SOUTH)
                    "properties.view" -> createAndRegisterTool("properties.view", "Properties", Color(43, 45, 48), SwingConstants.EAST)
                    else -> DemoTool(id, "Dynamic Dock $id", Color(45, 45, 45), SwingConstants.WEST)
                }
                Pair(dock, dock.preferredLocation)
            }
            .build()

        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.preferredSize = Dimension(1200, 800)
        frame.contentPane = workspace

        // 5. Gestione del caricamento dello stato precedente (se presente)
        val configFile = File("kdock_layout_demo.properties")

        // 6. Hook di chiusura per salvare automaticamente lo stato del layout
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                log.fine { "Salvataggio del layout in corso: ${configFile.absolutePath}" } // LOG
                workspace.saveLayoutConfiguration(configFile)
            }
        })

        // Mostra l'applicazione
        frame.contentPane = workspace
        frame.pack()
        log.fine { "Packing frame" } // LOG
        workspace.loadLayoutConfiguration(configFile)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}