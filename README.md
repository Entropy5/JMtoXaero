# JMtoXaero

Tool to convert [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) tiles to regions used by [Xaero's World Map](https://chocolateminecraft.com/worldmap.php)

Usage: 

`java -jar JMtoXaero.jar <input folder> <output folder> (optional) <dimension id> <journeymap slice[0-15 or day/night/topo]>`

Input folder should point to your journeymap singleplayer or multiplayer data folder, where DIM0 etc resides. 

Example:
`.minecraft/journeymap/data/mp/2b2t/`
or 
`.minecraft/journeymap/data/sp/2b2t_256k²_spawn_download/`

Output folder should point to your XaeroWorldMap folder + server or singleplayer listing.

Example:
`.minecraft/XaeroWorldMap/Multiplayer_connect.2b2t.org/`

