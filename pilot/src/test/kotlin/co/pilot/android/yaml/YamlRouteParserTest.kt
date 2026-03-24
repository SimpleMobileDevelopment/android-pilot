package co.pilot.android.yaml

import co.pilot.android.dsl.Instruction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YamlRouteParserTest {

    @Test
    fun `parse complete route with all instruction types`() {
        val yaml = """
            route: Login flow
            steps:
              - step: Enter credentials
                instructions:
                  - action: Tap the email field
                  - action: Type 'user@test.com'
                  - verify: Email field shows the entered text
                  - remember:
                      key: email
                      description: The email address in the field
        """.trimIndent()

        val route = YamlRouteParser.parse(yaml)

        assertEquals("Login flow", route.name)
        assertEquals(1, route.steps.size)
        assertEquals("Enter credentials", route.steps[0].name)

        val instructions = route.steps[0].instructions
        assertEquals(4, instructions.size)
        assertTrue(instructions[0] is Instruction.Action)
        assertTrue(instructions[1] is Instruction.Action)
        assertTrue(instructions[2] is Instruction.Verify)
        assertTrue(instructions[3] is Instruction.Remember)

        assertEquals("Tap the email field", instructions[0].description)
        val remember = instructions[3] as Instruction.Remember
        assertEquals("email", remember.key)
        assertEquals("The email address in the field", remember.description)
    }

    @Test
    fun `parse route with multiple steps`() {
        val yaml = """
            route: Multi-step flow
            steps:
              - step: First
                instructions:
                  - action: Do something
              - step: Second
                instructions:
                  - verify: Check something
        """.trimIndent()

        val route = YamlRouteParser.parse(yaml)

        assertEquals(2, route.steps.size)
        assertEquals("First", route.steps[0].name)
        assertEquals("Second", route.steps[1].name)
    }

    @Test
    fun `parse route with no steps`() {
        val yaml = """
            route: Empty route
        """.trimIndent()

        val route = YamlRouteParser.parse(yaml)

        assertEquals("Empty route", route.name)
        assertTrue(route.steps.isEmpty())
    }

    @Test
    fun `missing route name throws exception`() {
        val yaml = """
            steps:
              - step: Something
                instructions:
                  - action: Do thing
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml)
        }
        assertTrue(exception.message?.contains("Missing 'route' name") == true)
    }

    @Test
    fun `missing step name throws exception`() {
        val yaml = """
            route: Test
            steps:
              - instructions:
                  - action: Do thing
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml)
        }
        assertTrue(exception.message?.contains("Each step must have a 'step' name") == true)
    }

    @Test
    fun `unknown instruction type throws exception`() {
        val yaml = """
            route: Test
            steps:
              - step: Bad step
                instructions:
                  - click: Something
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml)
        }
        assertTrue(exception.message?.contains("Instruction must be 'action', 'verify', or 'remember'") == true)
    }

    @Test
    fun `remember missing key throws exception`() {
        val yaml = """
            route: Test
            steps:
              - step: Bad step
                instructions:
                  - remember:
                      description: Some description
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml)
        }
        assertTrue(exception.message?.contains("'remember' requires a 'key' field") == true)
    }

    @Test
    fun `remember missing description throws exception`() {
        val yaml = """
            route: Test
            steps:
              - step: Bad step
                instructions:
                  - remember:
                      key: some_key
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml)
        }
        assertTrue(exception.message?.contains("'remember' requires a 'description' field") == true)
    }

    @Test
    fun `instruction ordering is preserved`() {
        val yaml = """
            route: Test
            steps:
              - step: Ordered step
                instructions:
                  - action: first
                  - verify: second
                  - remember:
                      key: k
                      description: third
                  - action: fourth
                  - verify: fifth
        """.trimIndent()

        val instructions = YamlRouteParser.parse(yaml).steps[0].instructions

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
    fun `parse from input stream`() {
        val yaml = """
            route: Stream test
            steps:
              - step: One step
                instructions:
                  - action: Do something
        """.trimIndent()

        val route = YamlRouteParser.parse(yaml.byteInputStream())

        assertEquals("Stream test", route.name)
        assertEquals(1, route.steps.size)
    }

    @Test
    fun `error message includes source name when provided`() {
        val yaml = """
            steps:
              - step: Something
                instructions:
                  - action: Do thing
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml, sourceName = "my-route.yaml")
        }
        assertTrue(exception.message?.contains("my-route.yaml") == true)
    }

    @Test
    fun `step error message includes source name when provided`() {
        val yaml = """
            route: Test
            steps:
              - instructions:
                  - action: Do thing
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            YamlRouteParser.parse(yaml, sourceName = "my-route.yaml")
        }
        assertTrue(exception.message?.contains("my-route.yaml") == true)
    }
}
