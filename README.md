# JOSM Elevation Plugin

A JOSM plugin that adds elevation data to selected nodes using the USGS Elevation Point Query Service (EPQS).

## Features

- Lookup elevation data for selected nodes from the USGS EPQS web service
- Adds `ele` tag with elevation in meters to selected nodes
- Background processing to keep JOSM responsive
- Proper error handling and user feedback
- Undo/redo support for all elevation data additions

## Building the Plugin

The plugin can be built using standard Maven commands:

```bash
mvn clean package
```

This will create `target/josm-elev-1.0-SNAPSHOT.jar` which is your JOSM plugin.

## Installation

1. Build the plugin JAR as described above
2. Copy the JAR file to your JOSM plugins directory:
   - Windows: `%APPDATA%\JOSM\plugins\`
   - macOS: `~/Library/JOSM/plugins/`
   - Linux: `~/.josm/plugins/`
3. Restart JOSM
4. The "Elevation Lookup" option will appear in the Tools menu

## Usage

1. Select one or more nodes in JOSM
2. Go to **Tools** → **Elevation Lookup**
3. The plugin will query the EPQS service for each selected node
4. Elevation data (in meters) will be added as `ele` tags
5. A completion dialog shows the number of successful lookups and errors

## Web Service

This plugin uses the USGS Elevation Point Query Service (EPQS):
- **URL**: `https://epqs.nationalmap.gov/v1/json`
- **Coverage**: United States and territories
- **Units**: Meters
- **Accuracy**: Varies by location (typically 1-3 meters)

## Error Handling

The plugin handles various error conditions:
- Network timeouts and connection errors
- Invalid responses from the web service
- Missing elevation data (service returns -1000000 for no data)
- No active data layer or selected nodes

## Technical Details

- **Language**: Kotlin
- **JSON Parsing**: Jackson with Kotlin module
- **Threading**: Background threads for web requests
- **JOSM Integration**: Proper command system integration for undo/redo
- **Minimum JOSM Version**: 19044
