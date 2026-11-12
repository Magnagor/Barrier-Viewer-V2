# Barrier Viewer (Minecraft 1.21.11 - Forge)

Shows barrier blocks as a thin red outline with a see-through red fill.

* Press **B** in-game to toggle it on/off.
* Change the key in **Options > Controls > Key Binds > Barrier Viewer**.
* Client-side only - put the jar in your `mods` folder, not on a server.

## Build
Push to GitHub -> Actions tab -> "Build mod jar" -> download the `BarrierViewer-jar` artifact.

Or locally (Java 21 + Gradle 9.3):

    gradle build

The jar ends up in `build/libs/`.

## Tweaking the look
Open `src/main/java/com/barrierviewer/BarrierViewerClient.java` - the colours, outline thickness,
range and default key are all constants at the top.
