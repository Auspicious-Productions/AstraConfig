# AstraConfig

**A config menu library for Fabric mods.** Mod developers declare their features and settings, and
AstraConfig turns them into a clean, themed settings menu, with saving, keybinds and a
drag-and-drop HUD editor built in.

> **Made with AI.** Most of AstraConfig's code and documentation was written by Claude (Anthropic's
> AI model) using Claude Code. We directed the work and tested it in game.

## For players

AstraConfig doesn't add anything to the game on its own. Install it if a mod you use needs it.
Mods that use AstraConfig give you:

- **A proper settings menu** with its own command and key, a search that finds any setting,
  and pages for keybinds, profiles and appearance
- **Keybinds that stay out of vanilla Controls**, with clashes between mods pointed out
- **A HUD editor**: drag, snap and scale HUD elements, change their style, undo and redo
- **Profiles**: save sets of settings, switch between them, and share them as text
- **Your look, everywhere**: dark or light theme, accent colour, animations and menu size
  are shared by every AstraConfig mod

### Requirements

- Fabric Loader 0.19 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

## For developers

Write your settings as annotated fields in Java or as delegates in Kotlin. AstraConfig builds the
menu, saves the values and keeps them up to date:

```java
@Slider(title = "Zoom Level", min = 1.5f, max = 50, step = 0.5f, logarithmic = true)
public static float level = 4f;

@Keybind(title = "Zoom Key")
public static AstraKeybind zoomKey = AstraKeybind.of(InputConstants.KEY_C).onPress(Zoom::start);
```

- Switches, sliders (incl. range and logarithmic), dropdowns, multi-selects, lists, text,
  colours, keybinds, buttons, coordinates and HUDs
- Show, hide or grey out settings based on others, and react to changes
- An event bus that only calls a feature's handlers while it's on
- A HUD API with a drawing context and ready-made cards that follow the player's style
- Custom pages, PNG and SVG icons, and your own logo in the menu
- Saving with migrations and support for renamed fields
- Mistakes caught early: at startup, and at compile time for Java with the bundled annotation
  processor

Add it from the Modrinth Maven:

```kotlin
repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}
dependencies {
    include(implementation("maven.modrinth:astraconfig:0.1.0+26.1")!!)
}
```

Use the version for your Minecraft version: `0.1.0+26.1`, `0.1.0+26.2` or `0.1.0+26.3`.

**[Full documentation on GitHub](https://github.com/Auspicious-Productions/AstraConfig#readme)**:
every option type, keybinds, events, HUDs, custom pages, saving and migrations.

## License

MIT. Source code and issue tracker on
[GitHub](https://github.com/Auspicious-Productions/AstraConfig).
