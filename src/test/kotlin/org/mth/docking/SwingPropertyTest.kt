package org.mth.docking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.beans.PropertyChangeEvent
import javax.swing.JPanel

/**
 * Verifies that [swingProperty] correctly notifies only listeners registered
 * for the specific property name that actually changed.
 *
 * The second test below documents the known bug: swingProperty currently
 * invokes ALL registered listeners regardless of the property name they
 * were registered for, instead of relying on JComponent.firePropertyChange
 * (which performs the name filtering itself). It is expected to FAIL until
 * the fix is applied.
 */
class SwingPropertyTest {

    /** Minimal test double exposing two independently delegated swing properties. */
    private class TestComponent : JPanel() {
        var title: String? by swingProperty(this, null)
        var tooltip: String? by swingProperty(this, null)
    }

    private lateinit var component: TestComponent

    @BeforeEach
    fun setUp() {
        component = TestComponent()
    }

    @Test
    fun `listener registered for the changed property is notified`() {
        val received = mutableListOf<PropertyChangeEvent>()
        component.addPropertyChangeListener("title") { received.add(it) }

        component.title = "Project View"

        assertEquals(1, received.size, "Listener bound to 'title' should fire exactly once")
        assertEquals("title", received[0].propertyName)
        assertEquals("Project View", received[0].newValue)
    }

    @Test
    fun `listener registered for a different property is NOT notified`() {
        val titleEvents = mutableListOf<PropertyChangeEvent>()
        val tooltipEvents = mutableListOf<PropertyChangeEvent>()

        component.addPropertyChangeListener("title") { titleEvents.add(it) }
        component.addPropertyChangeListener("tooltip") { tooltipEvents.add(it) }

        component.title = "Project View"

        assertEquals(1, titleEvents.size, "The 'title' listener should react to the title change")
        assertEquals(
            0, tooltipEvents.size,
            "The 'tooltip' listener must NOT react to a 'title' change " +
                    "(currently FAILS: swingProperty broadcasts to ALL listeners, ignoring property.name)"
        )
    }

    @Test
    fun `unnamed listener still receives every property change`() {
        val received = mutableListOf<PropertyChangeEvent>()
        component.addPropertyChangeListener(received::add) // no name filter

        component.title = "A"
        component.tooltip = "B"

        assertEquals(2, received.size)
    }

    @Test
    fun `setting the same value does not fire any event`() {
        component.title = "Same"

        val received = mutableListOf<PropertyChangeEvent>()
        component.addPropertyChangeListener("title") { received.add(it) }

        component.title = "Same"

        assertEquals(0, received.size, "No event should fire when the new value equals the old one")
    }
}