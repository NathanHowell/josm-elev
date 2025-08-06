package com.github.nathanhowell.elevation

import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.MainMenu
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation
import org.openstreetmap.josm.tools.Logging

class ElevationPlugin(info: PluginInformation) : Plugin(info) {
    init {
        Logging.info("Elevation Plugin initialized")
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, ElevationLookupAction())
    }
}
