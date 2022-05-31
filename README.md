# JMtoXaero

Tool to convert [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) tiles to regions used by [Xaero's World Map](https://chocolateminecraft.com/worldmap.php)

Usage: 

`java -jar JMtoXaero.jar <input folder> <output folder> (optional) <dimension id> (-1, 0, 1, or all)`

Input folder should point to your journeymap singleplayer or multiplayer data folder, where DIM0 etc resides.

Example input:
`.minecraft/journeymap/data/mp/2b2t/`
or 
`.minecraft/journeymap/data/sp/2b2t_256k²_spawn_download/`

Output folder should point to your XaeroWorldMap folder + server or singleplayer listing.

Example output:
`.minecraft/XaeroWorldMap/Multiplayer_connect.2b2t.org/`


Full command example:

`java -jar JMtoXaero-1.0.jar ".minecraft\journeymap\data\mp\2b2t" ".minecraft\XaeroWorldMap\Multiplayer_2b2t.org" 0`

`java -jar JMtoXaero-1.0.jar ".minecraft\journeymap\data\mp\2b2t" ".minecraft\XaeroWorldMap\Multiplayer_2b2t.org" all`

