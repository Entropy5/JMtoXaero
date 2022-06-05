# JMtoXaero

**JMtoXaero** is a tool to convert [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) tiles to regions used by [Xaero's World Map](https://chocolateminecraft.com/worldmap.php)

![Journeymap to Xaero mapping visualization](https://i.imgur.com/LP8HuKX.png)

# Description 

- Reads images from the Journeymap folder
- Writes Xaero regions that are relevant for multiplayer maps (Singleplayer maps don't need this conversion, Xaero's mod will just automap everything over time)
- Confirmed to work with XaerosWorldMap_1.22.0_Forge_1.12.jar
- Uses the Journeymap colormapping to decode what block likely generated each pixel
- Visual difference is significant, as Journeymap pixel colors are also affected by height level, shading, transparency, and biomes

# Usage

Download the latest jar from releases

`java -jar JMtoXaero-2.0.jar <input folder> <output folder> <dimension> (-1, 0, 1, all)`

Input folder should point to your journeymap singleplayer or multiplayer data folder, where DIM0 etc resides.

Example input:
`.minecraft/journeymap/data/sp/2b2t_256k²_spawn_download/`

Output folder should point to your XaeroWorldMap folder + server or singleplayer listing.

Example output:
`.minecraft/XaeroWorldMap/Multiplayer_connect.2b2t.org/`


# Full command example

`java -jar JMtoXaero-2.0.jar ".minecraft\journeymap\data\mp\2b2t" ".minecraft\XaeroWorldMap\Multiplayer_2b2t.org" all`
