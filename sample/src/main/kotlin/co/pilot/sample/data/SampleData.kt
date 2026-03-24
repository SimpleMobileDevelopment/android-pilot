package co.pilot.sample.data

data class SampleItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val description: String,
)

object SampleData {
    val items: List<SampleItem> = (1..25).map { i ->
        SampleItem(
            id = i,
            title = "Item $i",
            subtitle = "This is the subtitle for item $i",
            description = "This is a detailed description for item $i. " +
                "It contains enough text to demonstrate the detail screen layout.",
        )
    }

    fun findById(id: Int): SampleItem? = items.find { it.id == id }
}
