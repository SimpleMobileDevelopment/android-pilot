package co.pilot.android.dsl

data class Route(val name: String, val steps: List<Step>)

data class Step(
    val name: String,
    val instructions: List<Instruction>,
    val timeoutSeconds: Int? = null,
)

sealed class Instruction {
    abstract val description: String

    data class Action(override val description: String) : Instruction()
    data class Verify(override val description: String) : Instruction()
    data class Remember(val key: String, override val description: String) : Instruction()
}
