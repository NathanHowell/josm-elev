package com.github.nathanhowell.elevation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.TextNode
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.LatLon
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.tools.HttpClient
import org.openstreetmap.josm.tools.Logging
import java.awt.event.ActionEvent
import java.net.URI
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class ElevationLookupAction : JosmAction(
    "Elevation Lookup",
    null,
    "Add elevation data to selected nodes",
    null,
    true,
    true
) {
    companion object {
        private val objectMapper = ObjectMapper()
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

        val response = HttpClient.create(URI.create(url).toURL())
            .connect()

        if (response.responseCode != 200) {
            Logging.error("Failed to fetch elevation data: HTTP ${response.responseCode} - ${response.content}")
            throw IllegalStateException("Failed to fetch elevation data: HTTP ${response.responseCode}")
        }

        val result = objectMapper.readTree(response.content)

        val elevationResults = result.get("value")
        if (elevationResults == null || elevationResults.isNull) {
            Logging.error("No elevation data found for coordinates: ${latLon.lat()}, ${latLon.lon()}")
            throw IllegalStateException("No elevation data found for coordinates: ${latLon.lat()}, ${latLon.lon()}")
        }

        when (elevationResults) {
            is TextNode -> {
                // Handle case where elevation is a single text node
                Logging.info("Elevation lookup successful for coordinates: ${latLon.lat()}, ${latLon.lon()}")
                return elevationResults.asDouble()
            }
            else -> {
                Logging.error("Unexpected response format: $elevationResults")
                throw IllegalStateException("Unexpected response format: $elevationResults")
            }
        }
    }
}
