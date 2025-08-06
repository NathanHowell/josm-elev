package org.example

import com.fasterxml.jackson.databind.JsonNode
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.MainMenu
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.data.coor.LatLon
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.tools.Logging
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.openstreetmap.josm.data.UndoRedoHandler
import java.awt.event.ActionEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class ElevationPlugin(info: PluginInformation) : Plugin(info) {
    init {
        MainMenu.add(MainApplication.getMenu().toolsMenu, ElevationLookupAction())
    }
}

class ElevationLookupAction : JosmAction(
    "Elevation Lookup",
    "elevation",
    "Add elevation data to selected nodes",
    null,
    true
) {
    companion object {
        private val client: Lazy<HttpClient> = lazy {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
        }

        private val objectMapper = ObjectMapper().registerKotlinModule()
    }

    override fun actionPerformed(e: ActionEvent) {
        val layer = MainApplication.getLayerManager().editLayer
        if (layer == null) {
            JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                "No active data layer found",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val selection = layer.data.selected
        val nodes = selection.filterIsInstance<Node>()

        if (nodes.isEmpty()) {
            JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                "Please select one or more nodes to lookup elevation",
                "No nodes selected",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        // Process elevation lookup in background thread
        thread {
            processElevationLookup(nodes)
        }
    }

    private fun processElevationLookup(nodes: Collection<Node>) {
        val commands = mutableListOf<Command>()

        for (node in nodes) {
            try {
                val elevation = lookupElevation(node.coor)
                val command = ChangePropertyCommand(
                    listOf(node),
                    "ele",
                    elevation.toString()
                )
                commands.add(command)
            } catch (e: Exception) {
                Logging.error("Failed to lookup elevation for node ${node.id}: ${e.message}")
            }
        }

        // Execute commands on EDT
        SwingUtilities.invokeLater {
            if (commands.isNotEmpty()) {
                val sequenceCommand = SequenceCommand("Add elevation data", commands)
                val editLayer = MainApplication.getLayerManager().editLayer
                if (editLayer != null) {
                    UndoRedoHandler.getInstance().add(sequenceCommand)
                }
            }
        }
    }

    private fun lookupElevation(latLon: LatLon): Double {
        val url = "https://epqs.nationalmap.gov/v1/json?" +
                "x=${latLon.lon()}&y=${latLon.lat()}&units=Meters&wkid=4326&includeDate=True"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "JOSM Elevation Plugin")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = client.value.send(request, HttpResponse.BodyHandlers.ofString())
        val result = objectMapper.readTree(response.body())

        val elevationResults = result.get("USGS_Elevation_Point_Query_Service")?.get("Elevation_Query")
        assert(elevationResults != null)
        assert(elevationResults?.size() == 1)
        when (elevationResults) {
           is ArrayNode if elevationResults.size() == 1 -> {
                // Valid response with one elevation result
                Logging.info("Elevation lookup successful for coordinates: ${latLon.lat()}, ${latLon.lon()}")
               return elevationResults.get(0).get("Elevation")?.asDouble()!!
           }
           else -> {
                Logging.error("Unexpected response format: $elevationResults")
               throw IllegalArgumentException("Unexpected response format: $elevationResults")
        }
        }
    }
}
