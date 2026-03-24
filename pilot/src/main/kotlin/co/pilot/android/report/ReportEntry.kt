package co.pilot.android.report

import co.pilot.android.ai.InstructionType

data class ReportEntry(
    val timestamp: Long,
    val routeName: String,
    val stepName: String,
    val instructionDescription: String,
    val instructionType: InstructionType,
    val status: EntryStatus,
    val reasoning: String?,
    val screenshotPath: String?,
    val durationMs: Long,
)

enum class EntryStatus { PASS, FAIL, ERROR }
