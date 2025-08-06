package com.github.nathanhowell.elevation

import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.MainMenu
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation

class ElevationPlugin(info: PluginInformation) : Plugin(info) {
    init {
        MainMenu.add(MainApplication.getMenu().toolsMenu, ElevationLookupAction())
    }
}
