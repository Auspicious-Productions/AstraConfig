# AstraConfig

A config menu library for Fabric mods on Minecraft 26.1–26.3. You declare **modules** and their
**settings** as annotated fields (OneConfig style) or Kotlin delegates. AstraConfig gives your mod:

- its own branded menu: Modules page with search, module pages, Bound Keys, Profiles, Settings,
  Customize, and any custom pages you add
- saving to `config/<modId>/config.json`, with migrations and renamed-field support
- keybinds that only live in your menu (never in vanilla Controls)
- an event bus (Orbit) that only calls a module's handlers while it's enabled
- HUD elements with a drag-and-drop HUD editor

All of it works the same from Java and Kotlin.

**Status:** all five phases of [`../PLAN.md`](../PLAN.md) are done. Published to `mavenLocal` only.

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

```kotlin
// build.gradle.kts of your mod
repositories { mavenLocal() }
dependencies {
    // one artifact per Minecraft version: 0.1.0+26.1, 0.1.0+26.2, 0.1.0+26.3
    include(implementation("xyz.essencexc:astraconfig:0.1.0+26.1")!!)
}
```

AstraConfig bundles Cascade, Snowbird, Kommand, KBus and Orbit. It needs `fabric-api` and
`fabric-language-kotlin` at runtime (Java mods too).

## A module in Java

A module is one card in the menu. Fields are the source of truth: read them anywhere, and AstraConfig
writes them when the player changes something.

```java
public final class FishingModule extends Module {
    public static final FishingModule INSTANCE = new FishingModule();

    @MasterToggle
    public static boolean enabled = false;

    @Keybind(title = "Toggle Fishing Macro", section = "Fishing Macro")
    public static AstraKeybind toggleKey = AstraKeybind.of(InputConstants.KEY_R).onPress(() -> INSTANCE.toggle());

    @Dropdown(title = "Fishing Type", section = "Fishing Macro")
    public static FishingType type = FishingType.NORMAL;

    @Switch(title = "Sneak While Fishing", section = "Fishing Macro")
    public static boolean sneak = false;

    @Slider(title = "Use Axe Below HP", section = "Fishing Macro", min = 0, max = 1_000_000, step = 1_000)
    @DependsOn("sneak")
    public static int axeHp = 100_000;

    @RangeSlider(title = "Reel Delay", section = "Fishing Macro", min = 0, max = 1000, step = 10,
                 minSpread = 20, formatter = ValueFormatter.Millis.class)
    public static IntRange reelDelay = IntRange.of(120, 250);   // reelDelay.random() picks a delay

    @Color(title = "Highlight Colour", section = "Visuals")
    public static int highlight = 0xFF3B82F6;

    @Section("Pond Scanning")                  // a nested class becomes its own section
    public static final class Pond {
        @Slider(title = "Pond Edge Threshold", min = 0, max = 10)
        @PreviousNames("edgeThreshold")        // renamed field: the old saved value is kept
        public static int edge = 2;
    }

    @Button(title = "Reset Statistics", text = "Reset", section = "Statistics")
    private void resetStats() { /* ... */ }

    private FishingModule() {
        super("fishing", "Fishing");
        description("A feature-rich fishing macro.");
        accent(0xFF3B82F6);                     // card and page colour
        hideIf("axeHp", () -> type != FishingType.NORMAL);
        onChange("sneak", (Boolean v) -> System.out.println("sneak = " + v));
    }

    @EventHandler                               // Orbit; only called while enabled
    private void onTick(TickEvent e) { /* ... */ }

    @Override public void onEnable()  { }
    @Override public void onDisable() { }
}
```

Rules: option fields must be non-`final` (except `@Hud`), and either `static` or in a Kotlin
`object`. Wrong types or impossible ranges fail at startup with a message saying what to fix.

## A module in Kotlin

The same annotations work on `object` properties:

```kotlin
object MiningModule : Module("mining", "Mining") {
    @MasterToggle var enabled = false
    @Dropdown(title = "Target", section = "Mining Macro") var target = MiningTarget.GEMSTONE
    @Slider(title = "Rotation Speed", min = 1f, max = 20f, step = 0.5f) var rotationSpeed = 8.5f

    init { hideIf(::rotationSpeed) { target == MiningTarget.GLACITE } }
}
```

Or extend `KtModule` and use delegates:

```kotlin
object DianaModule : KtModule("diana", "Diana") {
    var enabled by masterToggle()
    var autoWarp by switch("Auto Warp", true, section = "Burrows")
        .onChange { println("autoWarp = $it") }
    var walkSpeed by slider("Walk Speed", 1.0, 0.5..2.0, step = 0.05).hideIf { !autoWarp }
    var clickDelay by rangeSlider("Click Delay", 80..140, bounds = 0..500, minSpread = 10)
    val toggleKey by keybind("Toggle Diana", InputConstants.KEY_G) { onPress { toggle() } }
    var mobs by multiSelect("Highlight Mobs", setOf(DianaMob.MINOS_INQUISITOR))
    var waypoints by itemList("Waypoints", listOf("12 70 -40"), placeholder = "x y z")
    val burrows by hud(StatCard("diana_burrows", "Burrows"))
    val reset by button("Reset Session", text = "Reset") { /* ... */ }
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
     .category("Skills", FishingModule.INSTANCE)
     .category("Mining", MiningModule.INSTANCE)
     .page("Routes", "Saved routes", () -> new RoutesPage())   // optional custom page
     .build();
```

```kotlin
Astra.create("mymod") {
    name = "My Mod"
    command = "mymod"
    openKey = InputConstants.KEY_RSHIFT
    lookup = MethodHandles.lookup()
    category("Skills", FishingModule)
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
e.g. `"mymod:icons/fish.svg"`) and `keywords` (extra
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
@Hud(section = "Visuals")
public static final StatCard stats = new StatCard("fishing_stats", "Fishing")
        .subtitle(() -> "Session running")
        .headline(() -> String.valueOf(catches), "catches")
        .progress(() -> catches / 100.0)                     // the ring
        .row("Per hour", () -> perHour)
        .row("Sneak", () -> sneak ? "on" : "off", () -> sneak ? 0xFF47DE9C : 0xFFFF6B7D);
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

`.page("Routes", "subtitle", factory)` adds a sidebar entry. The factory returns any AstraConfig element;
build it from the same widgets the menu uses:

```kotlin
page("Routes", "Saved routes") {
    ScrollPane().apply {
        content.padding = 20f
        content.adopt(Label("No routes yet.", muted = true))
        content.adopt(Button("Record route", Button.Style.PRIMARY) { startRecording() })
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
dependencies { annotationProcessor("xyz.essencexc:astraconfig:0.1.0+26.1") }
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

Built from the Template by Starred (BSD-3-Clause, see `LICENSE`).

## License

MIT, see [LICENSE](LICENSE).
