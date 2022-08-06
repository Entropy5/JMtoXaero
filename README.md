# JMtoXaero

**JMtoXaero** is a tool to convert [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) tiles to regions used by [Xaero's World Map](https://chocolateminecraft.com/worldmap.php)  -  [Video guide](https://www.youtube.com/watch?v=-SiCD_DgfHE)

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
`C:/appdata/.minecraft/journeymap/data/sp/2b2t_256k²_spawn_download/`

Output folder should point to your XaeroWorldMap folder + server or singleplayer listing.

Example output:
`C:/appdata/.minecraft/XaeroWorldMap/Multiplayer_connect.2b2t.org/`

Within the input folder, DIM0 is overworld, DIM1 is end, and DIM-1 is nether. Use the last argument to select a dimension or 'all' to process them all.


# Full command example

`java -jar JMtoXaero-2.0.jar "C:\appdata\.minecraft\journeymap\data\mp\2b2t" "C:\appdata\.minecraft\XaeroWorldMap\Multiplayer_2b2t.org" all`

**Used journeymap with a resourcepack other than vanilla?**
[Read here](./MAPPINGS.md)
