# KDockLite

[![Kotlin Version](https://img.shields.io/badge/kotlin-2.x-purple?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java Compatibility](https://img.shields.io/badge/java-17%2B-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![UI Style](https://img.shields.io/badge/L%26F-FlatLaf-blue?style=flat)](https://www.formdev.com/flatlaf/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![](https://jitpack.io/v/sesquialtera87/KDockLite.svg)](https://jitpack.io/#sesquialtera87/KDockLite)
[![GitHub repo size](https://img.shields.io/github/repo-size/sesquialtera87/KDockLite?color=blueviolet)](https://github.com/sesquialtera87/KDockLite)

A lightweight, high-performance, and fully customizable docking layout system for
Java and Kotlin desktop applications built on top of [FlatLaf](https://www.formdev.com/flatlaf/).

This framework allows developers to build flexible IDE-like user interfaces with collapsible sidebars,
tear-out floating utility windows, and robust layout persistence.

## Key Features

- **Tri-Sidebar Anchor Topology**: Seamlessly dock panels to the `WEST`, `EAST`, or `SOUTH` regions of your application.
- **Floating Windows (Detachment)**: Context-click any dock to detach it into an autonomous floating `JDialog` utility
  window that remembers its dimensions and coordinates.
- **Dynamic Layout**: Advanced routing of the central canvas component using `COMPRESSED` or `EXTENDED` horizontal
  bounding modes for the southern dock layout.
- **State Persistence**: Capture, serialize, and fully restore complex workspace topologies, divider locations, and
  floating window bounds using standard JVM `.properties` files.
- **FlatLaf Native Look & Feel**: Built from the ground up utilizing `FlatSplitPane`, `FlatToolBar`, and
  `FlatToggleButton` for a modern, sleek interface.

## Workspace Setup & Initialization

The framework leverages a fluent `WorkspaceBuilder` pattern to construct and assemble the structural topography of your
user interface.

```kotlin
val centralCanvas = JPanel()

// Assemble the workspace topology using the Builder
val workspace = Workspace.WorkspaceBuilder()
    .southLayoutMode(Workspace.SouthLayoutMode.EXTENDED)
    .setCentralComponent(centralCanvas)
    // Register a factory to resolve components on-demand (essential for state restoration)
    .setSingleDockFactory { id ->
        when (id) {
            "project-view" -> Pair(ProjectViewDock(), SwingConstants.WEST)
            "console-log" -> Pair(ConsoleDock(), SwingConstants.SOUTH)
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

- `COMPRESSED`: The southern dock pane is constrained in the middle, sitting strictly between the `WEST` and `EAST`
  sidebars.
- `EXTENDED`: The southern dock pane spans across the entire horizontal footprint of the window, sitting completely
  underneath the sidebars.

## UI Customization via UIManager

The look and feel of the docking framework is fully decoupled from the core logic and can be stylized globally using
`UIManager` look-and-feel tokens.

By targeting the custom `"kdock.*"` property keys before building your UI, you can fully match your application's active
`FlatLaf` dark or light theme.

| Key Property              | Type    | Description                                                                         |
|:--------------------------|:--------|:------------------------------------------------------------------------------------|
| `kdock.header.background` | `Color` | The background color of the title bar (`AbstractDockHeader`) for each docked panel. |
| `kdock.header.foreground` | `Color` | The foreground (text) color of the dock title.                                      |
| `kdock.header.font`       | `Font`  | The typography font applied to the title text label inside the header.              |
| `kdock.divider.size`      | `Int`   | The divider size of the component splitter (`FlatSplitPane`).                       |
| `kdock.icon.minimize`     | `Icon`  | The icon of the header button which minimizes the dock.                             |
| `kdock.icon.close`        | `Icon`  | The icon of the header button which closes the dock.                                |

## State Persistence

Saving and loading the user's customized sidebar state layout requires just a simple method invoke target:

```kotlin
val configFile = File("user-layout.properties")

// Save current topology state layout configuration
workspace.saveLayoutConfiguration(configFile)

// Load and restore layout configuration on next startup
workspace.loadLayoutConfiguration(configFile)
```

### Custom Layout Persisters

By default, `Workspace` serializes the layout topology into a flat standard `.properties`
file through an internal implementation.
However, the architecture is fully decoupled from any specific storage format.

If you prefer to store the layout configuration using other formats (such as `JSON`, `XML`, or `INI`)
you can implement your own custom serialization logic by implementing the `LayoutPersister` interface
and registering it into the `PersistenceManager`.

1. **Implement the Interface**
   Create a class that implements `LayoutPersister` and handle the serialization/deserialization logic
   using your library of choice (e.g., Jackson, Gson, or kotlinx.serialization for JSON):

    ```kotlin
    class JsonLayoutPersister : LayoutPersister {
        override fun save(state: WorkspaceState, output: File) {
            // Your custom logic to convert WorkspaceState to JSON string 
            // and write it down to the output file
            val jsonString = customJsonSerializer.encodeToString(state)
            output.writeText(jsonString)
        }
    
        override fun load(output: File): WorkspaceState? {
            // Your custom logic to read the file and map it back 
            // to a valid WorkspaceState instance topology tree
            if (!output.exists()) return null
            return try {
                customJsonSerializer.decodeFromString<WorkspaceState>(output.readText())
            } catch (e: Exception) {
                null
            }
        }
    }
    ```

2. **Register Your Custom Engine**
   Before triggering any save or load operation (typically during your application bootstrap sequence),
   swap the default engine in the global configuration instance:

```kotlin
// Register your custom persistence layout strategy
PersistenceManager.layoutPersister = JsonLayoutPersister()
```

Once registered, all subsequent invocations of `workspace.saveLayoutConfiguration(file)` and
`workspace.loadLayoutConfiguration(file)` will seamlessly delegate processing to your custom engine
behind the scenes.