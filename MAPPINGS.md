# Mappings

JourneyMap generates its tiles based on the resource pack you have applied. 
JMtoXaero by default assumes that your tiles are from the default 1.12 resource pack and so it might break if you use a different one.

Luckily, you can override the mappings file used by adding the argument `-m="path/to/mapping/file.txt"` to the end of your command.

Mappings for some of the most popular resource packs can be found in [the mappings directory](./mappings).

## Generating your own mappings
With [bsdiff](https://github.com/mendsley/bsdiff) or [jbsdiff](https://github.com/malensek/jbsdiff) you can apply a patch to JourneyMap to make it write the mappings for you.
Just run `bsdiff patch <original journeymap.jar> <new journeymap.jar> jmpatch.diff` and it will create a new JourneyMap jar which will write the mappings to the file "mappings.txt" in your root .minecraft directory when you disconnect from a server.