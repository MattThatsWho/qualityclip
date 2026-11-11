# QualityClip
*Your quality-of-life clipboard, notebook and reminder tool for Minecraft.*

[Website](https://mattsmods.vercel.app/qualityclip) · [Download](https://mattsmods.vercel.app/qualityclip/download) · [Report a bug](https://mattsmods.vercel.app/qualityclip/report-bug) · [Support](https://mattsmods.vercel.app/qualityclip/support) · [Community](https://mattsmods.vercel.app/qualityclip/threads)

Client-side Fabric mod. Supports **26.1.2**, **26.2** and **26.3**.

## What it does
- **Chat Clips** - save messages (up to 256 chars) and paste them into chat with one click. Use placeholders like `{x} {y} {z}`, `{biome}`, `{dimension}`, `{time}`, `{mctime}`, `{day}`, `{player}`.
- **Signs** - four-line sign clips (front and back) with the real 90px line limit; paste onto an open sign screen.
- **Quick Clips** - four slots with hotkeys (set in Controls): grab your coordinates/biome/time, save as a clip, copy, or both.
- **Favorites** (star a clip) and **JSON import/export** (export one or everything; drop `.json` files on the clipboard screen to import).
- **Notes** - multi-page notebooks that follow Book & Quill rules (114px wide, 14 lines, 1023 chars/page, 100 pages) so they paste 1:1. Real text selection (drag, shift+arrows, double-click, Ctrl+A/C/X/V); the Scribble-style toolbar colours/formats the selection. Optional two-page view.
- **Reminders** - a name, optional details, and a time ("in 20 minutes", "in 1 Minecraft day" or "Saturday at 8 PM"), with Daily/Weekly repeat and a snooze button. Clicking the notification opens it.
- **Scheduled clips** - repeat any clip every 5-60 min (once / until midnight / forever).
- **Notifications** in three styles (Standard / Bold / SUPER), a reminder panel in chat (click, X, Clear All, scroll).
- **Book & Quill and sign integration** - buttons on the book and sign screens: open QualityClip, and *Copy as a new clip*. Pasting a note/sign into the open screen replaces its contents (with a confirmation). Scribble 2.x ships its own book screen, so it is detected by class name; the log prints what it finds.
- Open with **K**, Mod Menu, or the clipboard button above the chat box. Data is saved in `config/qualityclip/`.

## Building
Needs **JDK 25** and **Gradle 9.x**.
```
gradle build                      # builds the default target (26.1.2)
gradle build -Pmc_target=26.2     # or 26.3
```
Jar: `build/libs/qualityclip-<mod version>-<mc version>.jar` (not `-sources`).
Each target's Minecraft / Fabric API / Mod Menu versions are in `versions/<target>/version.properties`
(26.3 values taken from a working 26.3 install; double check at https://fabricmc.net/develop).
GitHub Actions (`.github/workflows/build.yml`) builds all three and uploads the jars as build artifacts; pushing a tag like `v0.0.3` (`release.yml`) publishes them as a GitHub Release.

## How multiple Minecraft versions are handled
One shared codebase in `src/main/java`. The few things that differ between versions live in
`versions/<target>/java` and are tiny:

| | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|
| open / get the current screen | `Minecraft` | `Minecraft.gui` | `Minecraft.gui` |
| key codes, mouse buttons | GLFW | GLFW | **SDL** (scancodes, left = 1, right = 3) |
| hotkey registration | normal constructor | normal constructor | looked up reflectively |
| HUD hook target | `Gui` | `Hud` | `Hud` |

Bigger projects usually automate this with [Stonecutter](https://stonecutter.kikugie.dev/) (Scribble does);
this repo keeps it dependency-free. If you outgrow it, a common alternative is one branch per Minecraft version.

## Code map
- `screen/` ClipboardScreen (sidebar + cards + chat-clip and schedule popups), SettingsScreen, NotebookScreen, ReminderScreen
- `ui/` pixel icons, theme, immediate-mode buttons/sliders/switches (`Imm`), multi-line editor (`TextArea`), time helpers
- `notify/` schedules, toasts, reminder panel, page layout (`Fmt`), chat / book / HUD hooks
- `data/` Clip, Schedule, Settings, JSON storage
- `compat/` guarded access to anything version-sensitive, and the reflection-based Book & Quill paste
- `mixin/` thin forwarders, each in its own **non-required** mixin config

## Safety design
Anything a game version might not provide is isolated behind try/catch, screens save and close themselves if they ever
throw, and every mixin config is optional. Worst case a feature switches itself off and logs a `QualityClip` line.

## License
MIT
