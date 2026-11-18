# AstraConfig

> [!NOTE]
> **Made with AI.** Most of AstraConfig's code and documentation was written by Claude, Anthropic's
> AI model, using Claude Code. EssenceXC directed the work and tested it in game. Review it like
> any other third-party code before you rely on it.

A config menu library for Fabric mods on Minecraft 26.1–26.3. You declare **modules** and their
**settings** as annotated fields (OneConfig style) or Kotlin delegates. AstraConfig gives your mod:

- its own branded menu: Modules page with search, module pages, Bound Keys, Profiles, Settings,
  Customize, and any custom pages you add
- saving to `config/<modId>/config.json`, with migrations and renamed-field support
- keybinds that only live in your menu (never in vanilla Controls)
- an event bus (Orbit) that only calls a module's handlers while it's enabled
- HUD elements with a drag-and-drop HUD editor

All of it works the same from Java and Kotlin.

**Status:** 0.1.0, for Minecraft 26.1, 26.2 and 26.3.

## Contents

- [Setup](#setup)
- [A module in Java](#a-module-in-java)
- [A module in Kotlin](#a-module-in-kotlin)
- [Registering your mod](#registering-your-mod)
- [Option types](#option-types)
- [Behaviour: listeners, hiding, dependencies](#behaviour)
- [Keybinds](#keybinds)
- [Events](#events)
- [HUDs](#huds)
- [Custom pages](#custom-pages)
- [Catching mistakes at compile time](#catching-mistakes-at-compile-time)
- [Saving, migrations, profiles](#saving-migrations-profiles)
- [What the player gets](#what-the-player-gets)
- [Building AstraConfig](#building-astraconfig)

## Setup

AstraConfig is published on [Modrinth](https://modrinth.com/mod/astraconfig), which doubles as a
Maven repository:

```kotlin
// build.gradle.kts of your mod
repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}
dependencies {
    // one version per Minecraft version: 0.1.0+26.1, 0.1.0+26.2, 0.1.0+26.3
    include(implementation("maven.modrinth:astraconfig:0.1.0+26.1")!!)
}
```

Bundling it with `include` means players don't have to install it separately. If you'd rather
have them install it from Modrinth themselves, use `implementation(...)` alone and add
`"astraconfig": "*"` to `depends` in your `fabric.mod.json`.

To use a build of your own instead, run `./gradlew publishToMavenLocal` here, then use
`mavenLocal()` and `xyz.essencexc:astraconfig:0.1.0+26.1`.

AstraConfig bundles Cascade, Snowbird, Kommand, KBus and Orbit. It needs `fabric-api` and
`fabric-language-kotlin` at runtime (Java mods too).

## A module in Java

A module is one card in the menu. Fields are the source of truth: read them anywhere, and AstraConfig
writes them when the player changes something.

A zoom mod, for example:

```java
public final class ZoomModule extends Module {
    public static final ZoomModule INSTANCE = new ZoomModule();

    @MasterToggle
    public static boolean enabled = true;

    @Keybind(title = "Zoom Key", section = "Zoom")
    public static AstraKeybind zoomKey = AstraKeybind.of(InputConstants.KEY_C)
            .onPress(Zoom::start)
            .onRelease(Zoom::stop);

    @Dropdown(title = "Key Behaviour", section = "Zoom")
    public static ZoomMode mode = ZoomMode.HOLD;              // your own enum: HOLD, TOGGLE

    @Slider(title = "Zoom Level", section = "Zoom", min = 1.5f, max = 50, step = 0.5f, logarithmic = true)
    public static float level = 4f;

    @Switch(title = "Scroll to Adjust", section = "Zoom")
    public static boolean scrollAdjust = true;

    @RangeSlider(title = "Scroll Limits", section = "Zoom", min = 1, max = 50, step = 0.5f, minSpread = 1)
    @DependsOn("scrollAdjust")                                 // greyed out while the switch is off
    public static FloatRange scrollLimits = FloatRange.of(2f, 20f);

    @Switch(title = "Smooth Zoom", section = "Feel")
    public static boolean smooth = true;

    @Slider(title = "Zoom Duration", section = "Feel", min = 0, max = 500, step = 10,
            formatter = ValueFormatter.Millis.class)
    public static int duration = 150;

    @Color(title = "Vignette Colour", section = "Visuals")
    public static int vignette = 0x80000000;

    @Section("Mouse")                              // a nested class becomes its own section
    public static final class Mouse {
        @Slider(title = "Sensitivity While Zoomed", min = 10, max = 100, step = 5)
        @PreviousNames("zoomSensitivity")          // renamed field: the old saved value is kept
        public static int sensitivity = 50;
    }

    @Button(title = "Reset Zoom Level", text = "Reset", section = "Zoom")
    private void resetLevel() { set("level", 4f); }

    private ZoomModule() {
        super("zoom", "Zoom");
        description("Zoom in with a key, like a spyglass you don't have to carry.");
        accent(0xFF3B82F6);                         // card and page colour
        hideIf("duration", () -> !smooth);          // only shown while Smooth Zoom is on
        onChange("level", (Float v) -> Zoom.setTarget(v));
    }

    @EventHandler                                   // Orbit; only called while enabled
    private void onTick(TickEvent e) { Zoom.tick(); }

    @Override public void onEnable()  { }
    @Override public void onDisable() { Zoom.stop(); }
}
```

Rules: option fields must be non-`final` (except `@Hud`), and either `static` or in a Kotlin
`object`. Wrong types or impossible ranges fail at startup with a message saying what to fix.

## A module in Kotlin

The same annotations work on `object` properties:

```kotlin
object FullbrightModule : Module("fullbright", "Fullbright") {
    @MasterToggle var enabled = false
    @Dropdown(title = "Method", section = "Brightness") var method = BrightMethod.GAMMA   // GAMMA, NIGHT_VISION
    @Slider(title = "Brightness", section = "Brightness", min = 1f, max = 15f, step = 0.5f) var gamma = 10f

    init { hideIf(::gamma) { method == BrightMethod.NIGHT_VISION } }   // night vision has no strength
}
```

Or extend `KtModule` and use delegates. A waypoints mod:

```kotlin
object WaypointsModule : KtModule("waypoints", "Waypoints") {
    var enabled by masterToggle(true)
    var showBeams by switch("Show Beams", true, section = "Display")
        .onChange { WaypointRenderer.refresh() }
    var labelScale by slider("Label Scale", 1.0, 0.5..2.0, step = 0.05, section = "Display").hideIf { !showBeams }
    var fadeDistance by rangeSlider("Fade Distance", 32..256, bounds = 0..1024, minSpread = 16, section = "Display")
    var maxDistance by slider("Max Distance", 1000.0, 10.0..100_000.0, logarithmic = true, section = "Display")
    var dimensions by multiSelect("Show In", setOf(Dim.OVERWORLD, Dim.NETHER))   // your own enum
    var waypoints by itemList("Waypoints", listOf("Home 120 64 -40"), placeholder = "name x y z")
    var spawn by position("Spawn Point", BlockPos.ZERO)                          // X/Y/Z + "Here"
    val addKey by keybind("Add Waypoint Here", InputConstants.KEY_B) { onPress { addHere() } }
    val nearest by hud(TextHud("nearest_waypoint", "Nearest Waypoint", { nearestText() }, "Nearest"))
    val clear by button("Clear All", text = "Clear") { waypoints = emptyList() }
}
```

A module property can't be called `description`, `icon`, `accent`, `name`, `id` or `mod` (they
clash with `Module`). `enabled` is fine.

## Registering your mod

Once, in `onInitializeClient`:

```java
Astra.builder("mymod")
     .name("My Mod")                          // sidebar title
     .command("mymod")                        // /mymod opens the menu
     .openKey(InputConstants.KEY_RSHIFT)      // optional; the player can rebind it
     .logo("mymod:icons/logo.svg")            // optional; PNG or SVG, shown in the sidebar
     .lookup(MethodHandles.lookup())          // required: lets AstraConfig reach your private fields
     .category("Camera", ZoomModule.INSTANCE)
     .category("World", FullbrightModule.INSTANCE)
     .page("Changelog", "What's new", () -> new ChangelogPage())   // optional custom page
     .build();
```

```kotlin
Astra.create("mymod") {
    name = "My Mod"
    command = "mymod"
    openKey = InputConstants.KEY_RSHIFT
    lookup = MethodHandles.lookup()
    category("World", WaypointsModule)
}
```

Use `InputConstants.KEY_*` for key codes, not `GLFW.GLFW_KEY_*`: 26.3 moved input to SDL and the
numbers differ. AstraConfig saves keys by name, so configs move between versions.

## Option types

| Annotation | Field type | Widget |
|---|---|---|
| `@MasterToggle` | `boolean` | Switch on the card and page header; turns the module on/off |
| `@Switch` / `@Checkbox` | `boolean` | Switch / tick box |
| `@Slider` | `int`, `float`, `double` | Slider with a number box; `formatter` for units; `logarithmic = true` for wide ranges (min > 0) |
| `@RangeSlider` | `IntRange`, `FloatRange` | Two knobs; `minSpread`; `.random()` picks a value |
| `@Number` | `int`, `float`, `double` | Number box with −/+; `unit` |
| `@Dropdown` | any `enum`, or `int` + `options` | Dropdown |
| `@MultiSelect` | `EnumSet<E>`, `Set<E>`, `List<E>`, or `List<String>` + `options` | Checklist popup |
| `@ItemList` | `List<String>` | Editable list: add, edit, reorder, remove |
| `@Text` | `String` | Text field; `multiline = true` for a text area |
| `@Color` | `int` (ARGB) | Colour picker; `alpha = false` hides the alpha bar |
| `@Keybind` | `AstraKeybind` | Key recorder (keys, mouse buttons, Ctrl/Shift/Alt) |
| `@Info` | `String` | A tinted note; `type = InfoType.WARNING` etc. |
| `@Button` | a method | A button that calls it |
| `@Position` | `BlockPos` or `Vec3` | X / Y / Z boxes and a "Here" button (player position) |
| `@Hud` | a `HudElement` (may be `final`) | Live thumbnail, Style popup, HUD editor, show/hide |

Every annotation also takes `title`, `description`, `section`, `icon` (a PNG or SVG resource id,
e.g. `"mymod:icons/zoom.svg"`) and `keywords` (extra
search terms).

## Behaviour

- **`onChange(name, listener)`** / `.onChange { }`: runs when the player (or `module.set(...)`)
  changes the option. A plain field write doesn't fire it, but is still saved.
- **`hideIf(name, condition)`** / `.hideIf { }`: hides the row while true. Rows slide in and out.
- **`@DependsOn("switchName")`** / `.dependsOn(::other)`: greys the row out while that switch is off.
- **`module.set("name", value)`**, `get`, `reset`: change options from code with listeners firing.
- **`enable()`, `disable()`, `toggle()`, `isEnabled()`**: the master toggle from code.
- **For the player:** a dot marks settings changed from their default; hover shows a reset arrow
  (right-clicking the row also resets), and a section heading gets "reset section" while anything
  in it changed. Descriptions longer than two lines are shortened, with the full text in a tooltip.

## Keybinds

Declare an `AstraKeybind` with `@Keybind` (or `keybind(...)` in Kotlin), then chain `onPress`,
`onRelease`, `whileHeld`, and `worksInGui()` if it should fire with a screen open. Keybinds fire
whether or not the module is enabled. For a keybind outside any module, call `.register("Title")`.
They appear only on your menu's Bound Keys page, grouped by key, with conflicts flagged (across
every AstraConfig mod).

## Events

`Astra.EVENTS` is an Orbit bus. A module's `@EventHandler` methods (private ones too) are
subscribed while it's enabled. AstraConfig posts:

| Event | When |
|---|---|
| `TickEvent` | Every client tick |
| `ModuleToggleEvent` | Before a module turns on/off; cancel it to refuse |
| `OptionChangedEvent` | After an option changes through the menu or `set` |
| `KeybindEvent` | An AstraConfig keybind was pressed or released |
| `ConfigLoadEvent` / `ConfigSaveEvent` | Config loaded / about to be saved |

Post your own events with `Astra.EVENTS.post(...)`.

## HUDs

Subclass `HudElement` and draw through the `HudContext` at (0, 0); AstraConfig handles position, scale,
visibility and the player's style choices:

```java
public final class SpeedHud extends HudElement {
    public SpeedHud() { super("speed", "Speed", HudAnchor.TOP_LEFT, 4, 4); }

    // Measure with the element's own helpers: they follow the player's font choice.
    @Override public float width()  { return 8 + textWidth("Speed") + 6 + textWidth("00.0 m/s", 8, true); }
    @Override public float height() { return 6 + lineHeight(); }

    @Override public void draw(HudContext ctx) {
        ctx.background(width(), height());                    // only if the player has it on
        ctx.text("Speed", 4, 3, ctx.mutedColor());
        String value = ctx.preview() ? "12.4 m/s" : currentSpeed();   // preview = editor / style popup
        ctx.text(value, 4 + ctx.textWidth("Speed") + 6, 3, ctx.accent(), 8, true);
    }
}

@Hud(section = "Overlay")
public static final SpeedHud speed = new SpeedHud();
```

`HudContext` has `text` / `textRight` / `textCentered`, `textWidth`, `lineHeight`, `background`,
`rect`, `outline`, `circle`, `line`, `progressArc`, `keyValueGrid`, `item(ItemStack, x, y)` and
`image("mymod:icons/x.svg", ...)`, plus `accent()`, `textColor()`, `mutedColor()`, `theme()` and
`graphics()` for anything else.

The HUD kit has ready-made pieces that already follow the style:

```java
@Hud(section = "Overlay")
public static final StatCard world = new StatCard("world_info", "World")
        .subtitle(() -> biomeName())
        .headline(() -> String.valueOf(day()), "day")
        .progress(() -> timeOfDay())                         // the ring, 0..1
        .row("Time", () -> clockTime())
        .row("Weather", () -> raining() ? "Rain" : "Clear", () -> raining() ? 0xFF60A5FA : 0xFFFBBF24);
```

Also `TextHud` (one labelled line), and `ProgressArc` / `KeyValueGrid` for your own elements.

**What the player controls.** Each HUD's row on the module page shows a live thumbnail plus:

- **Style**: background on/off and colour, text colour, accent, Astra or Minecraft font, text
  shadow, hide while chat is open, hide with F3 open, with a live preview.
- **Edit**: the HUD editor. Drag with snapping (screen edges, centre, the hotbar, other elements;
  Shift = free), scroll to scale (0.25–4×), right-click or R to reset, Tab to select, arrows to
  nudge, Space to show/hide, Ctrl+Z / Ctrl+Y to undo/redo. The side panel can show every AstraConfig
  mod's HUDs at once.
- A show/hide switch.

HUDs draw while their module is enabled and hide with F1. Positions are stored as a screen anchor
plus an offset, so they stay put across window sizes and GUI scales.

## Custom pages

`.page("Changelog", "subtitle", factory)` adds a sidebar entry. The factory returns any AstraConfig
element; build it from the same widgets the menu uses:

```kotlin
page("Changelog", "What's new") {
    ScrollPane().apply {
        content.padding = 20f
        content.adopt(Label("1.2: smoother zoom, new vignette colour.", muted = true))
        content.adopt(Button("Open website", Button.Style.PRIMARY) { openWebsite() })
    }
}
```

Widgets (`Switch`, `Slider`, `Dropdown`, `TextField`, `ColorPicker`, ...) take a
`Binding.of(getter, setter)`, so they can drive anything, not just options.

## Catching mistakes at compile time

AstraConfig validates every module when the game starts and stops with a message saying what to fix.
Java mods can get the same checks when they compile: the AstraConfig jar contains an annotation
processor.

```kotlin
dependencies { annotationProcessor("maven.modrinth:astraconfig:0.1.0+26.1") }
```

It reports wrong field types, `final` option fields, impossible slider ranges (including
logarithmic sliders starting at 0), int dropdowns or string multi-selects without `options`,
`@DependsOn` naming a missing or non-boolean field, `@Button` methods with parameters, non-static
fields in `@Section` classes, and more than one option annotation on a field. Kotlin sources are
still checked at startup.

## Saving, migrations, profiles

- Values save to `config/<modId>/config.json` a few seconds after a change, every 5 s if fields
  were changed directly, and when the game closes. `AstraMod.save()` writes immediately.
- Rename a field with `@PreviousNames("oldName")`. For bigger changes, bump
  `.schemaVersion(2)` and add `.migrate((from, json) -> ...)` to rewrite the old JSON.
- Players can save **profiles** (named copies of all settings), switch between them, and export or
  import settings as text through the clipboard (Profiles page). Loading fires your `onChange`
  listeners like a normal change.

## What the player gets

- `/<command>` or the menu key opens the menu; it reopens on the page they left.
- Search on the Modules page finds modules and individual settings and jumps to them.
- Bound Keys, Profiles, Settings (menu key, animations, blur, menu size, open config folder,
  reset) and Customize (Night/Day theme, accent colour) pages.
- Full keyboard control: Tab / Shift+Tab, Space/Enter, arrows.
- Theme, accent, animations, blur and menu size are shared by every AstraConfig mod
  (`config/astraconfig/prefs.json`).

## Building AstraConfig

JDK 25 is required.

| Command | What it does |
|---|---|
| `./gradlew build` | Builds and tests all three Minecraft versions |
| `./gradlew :26.1:test` | Unit tests only |
| `./gradlew :26.1:runTestmodClient` | Game with the Java and Kotlin test mods (`/astrajava`, `/astrakt`, `/astragallery`) |
| `./gradlew :26.1:dokkaGenerate` | API docs (KDoc/Javadoc) into `versions/26.1/build/dokka` |
| `./gradlew updateKotlinAbi` | Rewrites the public-API snapshots (`versions/<mc>/api/*.api`) after an intended API change. `build` runs `checkKotlinAbi`, which fails on unintended ones |
| `./gradlew publishToMavenLocal` | Publishes `xyz.essencexc:astraconfig:0.1.0+<mc>` to `~/.m2` |

The build setup is based on the Template by Starred, used under the BSD 3-Clause License (see [`LICENSE-TEMPLATE`](LICENSE-TEMPLATE)).

## License

MIT, see [LICENSE](LICENSE).
