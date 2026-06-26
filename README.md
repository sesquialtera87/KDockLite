# KDockLite

[![Kotlin Version](https://img.shields.io/badge/kotlin-2.x-purple?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java Compatibility](https://img.shields.io/badge/java-11%2B-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![UI Style](https://img.shields.io/badge/L%26F-FlatLaf-blue?style=flat)](https://www.formdev.com/flatlaf/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub repo size](https://img.shields.io/github/repo-size/sesquialtera87/KDockLite?color=blueviolet)](https://github.com/IL_TUO_USERNAME/KDockLite)

A lightweight, high-performance, and fully customizable docking layout system for
Java and Kotlin desktop applications built on top of [FlatLaf](https://www.formdev.com/flatlaf/).

This framework allows developers to build flexible IDE-like user interfaces with collapsible sidebars,
tear-out floating utility windows, and robust layout persistence.

## Key Features
- **Tri-Sidebar Anchor Topology**: Seamlessly dock panels to the `WEST`, `EAST`, or `SOUTH` regions of your application.
- **Floating Windows (Detachment)**: Context-click any dock to detach it into an autonomous floating `JDialog` utility window that remembers its dimensions and coordinates.
- **Dynamic Layout**: Advanced routing of the central canvas component using `COMPRESSED` or `EXTENDED` horizontal bounding modes for the southern dock layout.
- **State Persistence**: Capture, serialize, and fully restore complex workspace topologies, divider locations, and floating window bounds using standard JVM `.properties` files.
- **FlatLaf Native Look & Feel**: Built from the ground up utilizing `FlatSplitPane`, `FlatToolBar`, and `FlatToggleButton` for a modern, sleek interface.

## Workspace Setup & Initialization
The framework leverages a fluent `WorkspaceBuilder` pattern to construct and assemble the structural topography of your user interface.

```kotlin
val centralCanvas = JPanel()

// Assemble the workspace topology using the Builder
val workspace = Workspace.WorkspaceBuilder()
    .setDividerSize(6)
    .setToolIconSize(24)
    .southLayoutMode(Workspace.SouthLayoutMode.EXTENDED)
    .setCentralComponent(centralCanvas)
    // Register a factory to resolve components on-demand (essential for state restoration)
    .setSingleDockFactory { id ->
        when (id) {
            "project-view" -> Pair(ProjectViewDock(), SwingConstants.WEST)
            "console-log"  -> Pair(ConsoleDock(), SwingConstants.SOUTH)
            else -> null
        }
    }
    // Pre-dock default components at startup
    .dock("project-view", SwingConstants.WEST)
    .dock("console-log", SwingConstants.SOUTH)
    .build()

// Mount onto a standard JFrame
JFrame("My App IDE").apply {
    defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    contentPane = workspace
    setSize(1024, 768)
    setLocationRelativeTo(null)
    isVisible = true
}
```

## Layout Topologies (`SouthLayoutMode`)
The framework offers two structural layout hierarchy options via `.southLayoutMode(...)`:

- `COMPRESSED`: The southern dock pane is constrained in the middle, sitting strictly between the `WEST` and `EAST` sidebars.
- `EXTENDED`: The southern dock pane spans across the entire horizontal footprint of the window, sitting completely underneath the sidebars.

## UI Customization via UIManager
The look and feel of the docking framework is fully decoupled from the core logic and can be stylized globally using `UIManager` look-and-feel tokens.

By targeting the custom `"dock.*"` property keys before building your UI, you can fully match your application's active `FlatLaf` dark or light theme.

| Key Property                     | Type     | Description                                                                                                          |
|:---------------------------------|:---------|:---------------------------------------------------------------------------------------------------------------------|
| `dock.header.background`         | `Color`  | The background color of the title bar (`AbstractDockHeader`) for each docked panel.                                  |
| `dock.header.foreground`         | `Color`  | The foreground (text) color of the active dock title.                                                                |
| `dock.header.font`               | `Font`   | The typography font applied to the title text label inside the header.                                               |
| `dock.header.border`             | `Border` | An optional border surrounding the dock header UI area.                                                              |
| `dock.button.selectedBackground` | `Color`  | The background color of the toolbar toggle buttons (e.g., `FlatToggleButton`) when their corresponding dock is open. |
| `dock.toolbar.background`        | `Color`  | The background color of the side tools panel framework (`FlatToolBar`).                                              |
| `dock.divider.hoverColor`        | `Color`  | The accent highlight color applied to the component splitter (`FlatSplitPane`) during drag or hover interactions.    |

## State Persistence
Saving and loading the user's customized sidebar state layout requires just a simple method invoke target:

```kotlin
val configFile = File("user-layout.properties")

// Save current topology state layout configuration
workspace.saveLayoutConfiguration(configFile)

// Load and restore layout configuration on next startup
workspace.loadLayoutConfiguration(configFile)
```