package co.pilot.android.dsl

@DslMarker
annotation class RouteDslMarker

fun route(name: String, block: RouteBuilder.() -> Unit): Route {
    val builder = RouteBuilder()
    builder.block()
    return builder.build(name)
}

@RouteDslMarker
class RouteBuilder {
    private val steps = mutableListOf<Step>()

    fun step(name: String, block: StepBuilder.() -> Unit) {
        val builder = StepBuilder()
        builder.block()
        steps.add(builder.build(name))
    }

    internal fun build(name: String): Route {
        require(steps.isNotEmpty()) { "Route '$name' must have at least one step" }
        return Route(name = name, steps = steps.toList())
    }
}

@RouteDslMarker
class StepBuilder {
    private val instructions = mutableListOf<Instruction>()
    var timeout: Int? = null

    fun action(description: String) {
        instructions.add(Instruction.Action(description))
    }

    fun verify(description: String) {
        instructions.add(Instruction.Verify(description))
    }

    fun remember(key: String, description: String) {
        instructions.add(Instruction.Remember(key = key, description = description))
    }

    internal fun build(name: String): Step {
        require(instructions.isNotEmpty()) { "Step '$name' must have at least one instruction" }
        return Step(name = name, instructions = instructions.toList(), timeoutSeconds = timeout)
    }
}
