package co.pilot.android.yaml

import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import timber.log.Timber
import java.io.InputStream

object YamlRouteParser {

    private const val TAG = "YamlRouteParser"

    private fun createYaml() = Yaml(SafeConstructor(LoaderOptions()))

    fun parse(input: InputStream, sourceName: String? = null): Route {
        val doc = createYaml().load<Map<String, Any>>(input)
        return parseRoute(doc, sourceName)
    }

    fun parse(yamlString: String, sourceName: String? = null): Route {
        val doc = createYaml().load<Map<String, Any>>(yamlString)
        return parseRoute(doc, sourceName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRoute(doc: Map<String, Any>, sourceName: String? = null): Route {
        val sourceContext = sourceName?.let { " in $it" } ?: ""
        val name = doc["route"] as? String
            ?: throw IllegalArgumentException("Missing 'route' name at root level$sourceContext")
        val rawSteps = doc["steps"] as? List<Map<String, Any>> ?: emptyList()
        if (rawSteps.isEmpty()) {
            Timber.tag(TAG).w("Route '$name' has no steps")
        }
        return Route(name = name, steps = rawSteps.map { parseStep(it, sourceContext) })
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStep(raw: Map<String, Any>, sourceContext: String): Step {
        val name = raw["step"] as? String
            ?: throw IllegalArgumentException("Each step must have a 'step' name$sourceContext")
        val timeoutSeconds = (raw["timeout"] as? Number)?.toInt()
        val rawInstructions = raw["instructions"] as? List<Map<String, Any>> ?: emptyList()
        if (rawInstructions.isEmpty()) {
            Timber.tag(TAG).w("Step '$name' has no instructions")
        }
        return Step(
            name = name,
            instructions = rawInstructions.map { parseInstruction(it, sourceContext) },
            timeoutSeconds = timeoutSeconds,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseInstruction(raw: Map<String, Any>, sourceContext: String): Instruction = when {
        raw.containsKey("action") -> {
            val value = raw["action"] as? String
                ?: throw IllegalArgumentException("'action' value must be a string, got: ${raw["action"]}$sourceContext")
            Instruction.Action(value)
        }
        raw.containsKey("verify") -> {
            val value = raw["verify"] as? String
                ?: throw IllegalArgumentException("'verify' value must be a string, got: ${raw["verify"]}$sourceContext")
            Instruction.Verify(value)
        }
        raw.containsKey("remember") -> {
            val rememberMap = raw["remember"] as? Map<String, String>
                ?: throw IllegalArgumentException("'remember' must be an object with 'key' and 'description'$sourceContext")
            Instruction.Remember(
                key = rememberMap["key"]
                    ?: throw IllegalArgumentException("'remember' requires a 'key' field$sourceContext"),
                description = rememberMap["description"]
                    ?: throw IllegalArgumentException("'remember' requires a 'description' field$sourceContext"),
            )
        }
        else -> throw IllegalArgumentException(
            "Instruction must be 'action', 'verify', or 'remember'. Found: ${raw.keys}$sourceContext",
        )
    }
}
