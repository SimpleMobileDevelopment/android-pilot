package co.pilot.android.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RouteDslTest {

    @Test
    fun `build route with multiple steps`() {
        val result = route("Login flow") {
            step("Open app") {
                action("Launch the application")
            }
            step("Enter credentials") {
                action("Type username")
                action("Type password")
            }
        }

        assertEquals("Login flow", result.name)
        assertEquals(2, result.steps.size)
        assertEquals("Open app", result.steps[0].name)
        assertEquals("Enter credentials", result.steps[1].name)
        assertEquals(1, result.steps[0].instructions.size)
        assertEquals(2, result.steps[1].instructions.size)
    }

    @Test
    fun `action instruction is created correctly`() {
        val result = route("test") {
            step("step") {
                action("Tap the button")
            }
        }

        val instruction = result.steps[0].instructions[0]
        assertTrue(instruction is Instruction.Action)
        assertEquals("Tap the button", instruction.description)
    }

    @Test
    fun `verify instruction is created correctly`() {
        val result = route("test") {
            step("step") {
                verify("Screen shows welcome message")
            }
        }

        val instruction = result.steps[0].instructions[0]
        assertTrue(instruction is Instruction.Verify)
        assertEquals("Screen shows welcome message", instruction.description)
    }

    @Test
    fun `remember instruction is created correctly`() {
        val result = route("test") {
            step("step") {
                remember("auth_token", "Store the authentication token")
            }
        }

        val instruction = result.steps[0].instructions[0] as Instruction.Remember
        assertEquals("auth_token", instruction.key)
        assertEquals("Store the authentication token", instruction.description)
    }

    @Test
    fun `empty route throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            route("empty") {}
        }
    }

    @Test
    fun `empty step throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            route("test") {
                step("empty step") {}
            }
        }
    }

    @Test
    fun `instruction ordering is preserved`() {
        val result = route("test") {
            step("ordered") {
                action("first")
                verify("second")
                remember("key", "third")
                action("fourth")
                verify("fifth")
            }
        }

        val instructions = result.steps[0].instructions
        assertEquals(5, instructions.size)
        assertTrue(instructions[0] is Instruction.Action)
        assertTrue(instructions[1] is Instruction.Verify)
        assertTrue(instructions[2] is Instruction.Remember)
        assertTrue(instructions[3] is Instruction.Action)
        assertTrue(instructions[4] is Instruction.Verify)

        assertEquals("first", instructions[0].description)
        assertEquals("second", instructions[1].description)
        assertEquals("third", instructions[2].description)
        assertEquals("fourth", instructions[3].description)
        assertEquals("fifth", instructions[4].description)
    }

    @Test
    fun `description property works for all instruction variants`() {
        val instructions: List<Instruction> = listOf(
            Instruction.Action("action desc"),
            Instruction.Verify("verify desc"),
            Instruction.Remember("key", "remember desc"),
        )

        assertEquals("action desc", instructions[0].description)
        assertEquals("verify desc", instructions[1].description)
        assertEquals("remember desc", instructions[2].description)
    }
}
