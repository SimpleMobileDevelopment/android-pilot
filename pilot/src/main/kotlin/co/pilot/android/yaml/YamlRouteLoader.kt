package co.pilot.android.yaml

import android.content.res.AssetManager
import co.pilot.android.dsl.Route
import java.io.File
import java.io.InputStream

object YamlRouteLoader {

    fun loadFromDirectory(directory: File): List<Route> {
        require(directory.isDirectory) { "Not a directory: ${directory.absolutePath}" }
        return directory.listFiles { file ->
            file.extension in listOf("yaml", "yml")
        }?.sorted()?.map { file ->
            file.inputStream().use { YamlRouteParser.parse(it, sourceName = file.name) }
        } ?: emptyList()
    }

    fun loadFromAssets(assets: AssetManager, path: String = "routes"): List<Route> =
        assets.list(path)
            ?.filter { it.endsWith(".yaml") || it.endsWith(".yml") }
            ?.sorted()
            ?.map { filename ->
                assets.open("$path/$filename").use { YamlRouteParser.parse(it, sourceName = filename) }
            } ?: emptyList()

    fun loadFromStreams(streams: List<Pair<String, InputStream>>): List<Route> =
        streams.map { (name, stream) -> stream.use { YamlRouteParser.parse(it, sourceName = name) } }
}
