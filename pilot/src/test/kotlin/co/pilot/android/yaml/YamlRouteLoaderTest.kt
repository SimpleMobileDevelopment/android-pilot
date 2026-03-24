package co.pilot.android.yaml

import co.pilot.android.dsl.Instruction
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlRouteLoaderTest {

    @Test
    fun `load all yaml routes from test resources`() {
        val resourceUrl = javaClass.classLoader!!.getResource("routes")!!
        val directory = File(resourceUrl.toURI())

        val routes = YamlRouteLoader.loadFromDirectory(directory)

        assertEquals(5, routes.size)
        val names = routes.map { it.name }
        assertTrue("Complete test route" in names)
        assertTrue("Landing screen basics" in names)
        assertTrue("Login with phone number" in names)
        assertTrue("Navigate to internal settings" in names)
        assertTrue("Toggle App Reskin flag" in names)
    }

    @Test
    fun `loaded route has correct instruction types`() {
        val resourceUrl = javaClass.classLoader!!.getResource("routes")!!
        val directory = File(resourceUrl.toURI())

        val routes = YamlRouteLoader.loadFromDirectory(directory)
        val completeRoute = routes.first { it.name == "Complete test route" }
        val secondStep = completeRoute.steps[1]

        assertEquals(3, secondStep.instructions.size)
        assertTrue(secondStep.instructions[0] is Instruction.Action)
        assertTrue(secondStep.instructions[1] is Instruction.Remember)
        assertTrue(secondStep.instructions[2] is Instruction.Verify)
    }

    @Test
    fun `login route parses with correct steps`() {
        val resourceUrl = javaClass.classLoader!!.getResource("routes")!!
        val directory = File(resourceUrl.toURI())

        val route = YamlRouteLoader.loadFromDirectory(directory).first { it.name == "Login with phone number" }

        assertEquals(3, route.steps.size)
        assertEquals("Launch and tap Let's Start", route.steps[0].name)
        assertEquals("Enter phone number and submit", route.steps[1].name)
        assertEquals("Verify successful login", route.steps[2].name)
        assertEquals(30, route.steps[2].timeoutSeconds)
        assertEquals(null, route.steps[0].timeoutSeconds)
    }

    @Test
    fun `toggle app reskin route uses remember instruction`() {
        val resourceUrl = javaClass.classLoader!!.getResource("routes")!!
        val directory = File(resourceUrl.toURI())

        val route = YamlRouteLoader.loadFromDirectory(directory).first { it.name == "Toggle App Reskin flag" }
        val toggleStep = route.steps[1]
        val rememberInstruction = toggleStep.instructions[0] as Instruction.Remember

        assertEquals("initial_state", rememberInstruction.key)
    }

    @Test
    fun `load from streams`() {
        val yaml = """
            route: Stream route
            steps:
              - step: First
                instructions:
                  - verify: Something is visible
        """.trimIndent()

        val routes = YamlRouteLoader.loadFromStreams(
            listOf("test.yaml" to yaml.byteInputStream()),
        )

        assertEquals(1, routes.size)
        assertEquals("Stream route", routes[0].name)
    }

    @Test
    fun `empty directory returns empty list`() {
        val tempDir = createTempDirectory("empty-routes").toFile()
        try {
            val routes = YamlRouteLoader.loadFromDirectory(tempDir)
            assertTrue(routes.isEmpty())
        } finally {
            tempDir.delete()
        }
    }
}
