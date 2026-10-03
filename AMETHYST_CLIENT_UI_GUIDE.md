# Amethyst Client — UI Design Guide

This guide describes the visual language of **Amethyst Launcher** so the
**Amethyst Client** (in-game menus, ClickGUI, HUD, title screen) looks and feels
like the same product. Every value here is taken from the launcher source
(`src/index.css`, `src/components/*`), so if the launcher changes, update this
file from there.

> Rule of thumb: if a player alt-tabs from the launcher into the client, the
> colours, corner radii, the pixel font on big actions, and the accent glow
> should feel continuous.

---

## 1. Design principles

1. **Dark, quiet surfaces + one loud accent.** Almost everything is a near-black
   neutral. Only the active/primary thing gets the accent colour.
2. **Pixel font only for big actions and headlines.** `Press Start 2P` is used on
   PLAY / STOP buttons and the player name. Everything else is a clean sans.
3. **Layered surfaces, not lines.** Depth comes from stepping through the
   surface ramp (`bg-sidebar` → `bg` → `bg-card` → `bg-card-hover`), with a 1px
   `border` on cards.
4. **Rounded, soft corners.** 8–24px radii depending on size (see §4).
5. **Fast, small motion.** 150–300ms, ease-out, short travel (6–10px). Respect
   "reduce motion".
6. **Always-visible affordances.** Clickable rows show their action button all
   the time, not only on hover.

---

## 2. Colour tokens

### 2.1 Default theme — Dark (Amethyst / magenta)

| Token | Hex | ARGB int (Java) | Used for |
|---|---|---|---|
| `accent` | `#FF00FF` | `0xFFFF00FF` | Primary buttons, active nav, toggles on, progress, icons in section titles |
| `accent-hover` | `#E000E0` | `0xFFE000E0` | Primary button hover |
| `accent-pressed` | `#C400C4` | `0xFFC400C4` | Primary button pressed |
| `accent-fg` | `#000000` | `0xFF000000` | Text/icon **on** an accent-filled button |
| `bg` | `#0D0D10` | `0xFF0D0D10` | Main content area background |
| `bg-sidebar` | `#09090B` | `0xFF09090B` | Outer chrome: title bar, side rail, bottom bar |
| `bg-elevated` | `#17171B` | `0xFF17171B` | Inputs, chips, inset areas, progress track |
| `bg-card` | `#1B1B20` | `0xFF1B1B20` | Cards, panels, dropdowns, modals |
| `bg-card-hover` | `#232329` | `0xFF232329` | Card / button hover |
| `border` | `#2A2A30` | `0xFF2A2A30` | Card borders, dividers |
| `border-light` | `#34343C` | `0xFF34343C` | Input borders, ghost-button borders, toggle-off track, scrollbar thumb |

### 2.2 Text ramp (Tailwind zinc)

| Role | Hex | ARGB |
|---|---|---|
| Headline (`zinc-50`) | `#FAFAFA` | `0xFFFAFAFA` |
| Primary text (`zinc-100`) | `#F4F4F5` | `0xFFF4F4F5` |
| Secondary (`zinc-300`) | `#D4D4D8` | `0xFFD4D4D8` |
| Label / section title (`zinc-400`) | `#A1A1AA` | `0xFFA1A1AA` |
| Muted / description (`zinc-500`) | `#71717A` | `0xFF71717A` |
| Faint / hint (`zinc-600`) | `#52525B` | `0xFF52525B` |
| Separator glyph (`zinc-700`) | `#3F3F46` | `0xFF3F3F46` |

### 2.3 Status colours

| Meaning | Hex | ARGB |
|---|---|---|
| Danger / STOP button | `#EF4444` (hover `#DC2626`) | `0xFFEF4444` |
| Success / online | `#10B981` (text `#34D399`) | `0xFF10B981` |
| Warning / not installed | `#FBBF24` | `0xFFFBBF24` |
| Minecraft news tag | `#F97316` (text `#FB923C`) | `0xFFF97316` |

### 2.4 Tinted accent fills

The launcher constantly uses the accent at low alpha. Use these exact alphas:

| Use | Alpha | Dark-theme ARGB |
|---|---|---|
| Active nav pill, tag chips | 15% | `0x26FF00FF` |
| Icon tile behind loader icon | 12% (20% on hover) | `0x1FFF00FF` / `0x33FF00FF` |
| Highlighted chip background | 10% | `0x1AFF00FF` |
| Highlighted chip border | 30% | `0x4DFF00FF` |
| Card border on hover | 50% | `0x80FF00FF` |
| Modal backdrop | black 60% | `0x99000000` |

### 2.5 The other themes

The launcher ships 4 themes. The client should support at least **Dark**; ideally
read the same theme name the launcher saved (`settings.theme`) so both match.

| Token | Blue | Green | Light |
|---|---|---|---|
| `accent` | `#4C9EFF` | `#3AE07A` | `#6D28D9` |
| `accent-hover` | `#3B8CE8` | `#2BC566` | `#5B21B6` |
| `accent-pressed` | `#2A76C8` | `#1FA553` | `#4C1D95` |
| `accent-fg` | `#04101F` | `#04140A` | `#FFFFFF` |
| `bg` | `#0B111D` | `#0A1210` | `#F4F2FA` |
| `bg-sidebar` | `#070C15` | `#060D0B` | `#E5E0F0` |
| `bg-elevated` | `#131C2C` | `#111E18` | `#EAE5F5` |
| `bg-card` | `#172134` | `#15241C` | `#FFFFFF` |
| `bg-card-hover` | `#1E2A42` | `#1C3126` | `#F5F2FC` |
| `border` | `#233149` | `#21362A` | `#DDD6EC` |
| `border-light` | `#2F4060` | `#2C4A38` | `#C4BADE` |

Light theme also inverts the text ramp (`zinc-100` → `#1C1529`, `zinc-500` →
`#6B6184`, etc. — see `src/index.css` lines 80–100) and uses violet-tinted
shadows instead of black. Note: in-game, a light theme over the world render is
hard to read; it is fine to support only the dark-family themes in HUD elements.

**Implementation tip:** keep every colour in one `Theme` class/record with these
exact field names, and never hard-code a hex in a widget. That's how the
launcher re-skins everything by swapping variables.

```java
public record AmethystTheme(
    int accent, int accentHover, int accentPressed, int accentFg,
    int bg, int bgSidebar, int bgElevated, int bgCard, int bgCardHover,
    int border, int borderLight) {

  public static final AmethystTheme DARK = new AmethystTheme(
      0xFFFF00FF, 0xFFE000E0, 0xFFC400C4, 0xFF000000,
      0xFF0D0D10, 0xFF09090B, 0xFF17171B, 0xFF1B1B20, 0xFF232329,
      0xFF2A2A30, 0xFF34343C);

  /** accent with a new alpha, e.g. withAlpha(accent, 0.15f) */
  public static int withAlpha(int argb, float a) {
    return ((int) (a * 255) << 24) | (argb & 0x00FFFFFF);
  }
}
```

---

## 3. Typography

| Role | Font | Size (launcher px) | Weight / style |
|---|---|---|---|
| Big action label (PLAY, STOP, INSTALL & PLAY) | **Press Start 2P** | 15 | uppercase, slight letter-spacing |
| Hero headline (player name) | **Press Start 2P** | 22 | — |
| Watermark (selected version, huge, 4% opacity) | **Press Start 2P** | 76 | decorative only |
| Eyebrow / greeting ("Good evening") | Sans | 11 | bold, UPPERCASE, tracking 0.18em, accent colour |
| Section title ("JUMP BACK IN", "SERVERS") | Sans | 11 | bold, UPPERCASE, tracking 0.14em, `zinc-400`, accent icon 13px before it |
| Card title | Sans | 14 | bold, `zinc-50` |
| Body / description | Sans | 14 | regular, `zinc-500`, relaxed line height |
| Meta / small print | Sans | 11–12 | `zinc-500` / `zinc-600` |
| Field label inside chip | Sans | 9 | bold, UPPERCASE, tracking 0.14em |

- Sans stack: `Inter`, `Segoe UI`, `Helvetica Neue`, Arial.
- Pixel font: `Press Start 2P` (OFL licence — free to bundle in the client as a
  TTF font resource).
- In Minecraft, if loading a custom TTF is not possible, use the vanilla font for
  body text and **only** bring in Press Start 2P for the big labels/headlines.
- Numbers that change (percent, FPS, ping) use tabular/monospaced digits so they
  don't jitter.

---

## 4. Shape, spacing, elevation

### Corner radii

| Element | Radius |
|---|---|
| Window / root | 12px |
| Small buttons, inputs, dropdown selects, tag chips | 8px (chips 6px) |
| Nav buttons, icon tiles, info chips, modals' inner buttons | 12px |
| Cards, list rows, sections, dropdown panels | 16px |
| Hero / launch panel | 24px |
| Primary big button (hero) | 16px; bottom-bar PLAY 12px |
| Toggles, progress bars | fully round |

In a Minecraft GUI (scaled pixels), divide by ~2: cards ≈ 6–8 GUI px, buttons ≈
4 GUI px. If rounded rects are too expensive, use square corners but keep the
proportions consistent everywhere.

### Spacing scale

Use multiples of 4px: `4, 6, 8, 10, 12, 14, 16, 20, 24, 32`.

- Page padding: 24px
- Gap between sections/columns: 20px
- Gap between list rows: 10px
- Card padding: 14–20px (hero 32px)
- Right rail width: 330px

### Elevation

- Default: no shadow, just `bg-card` + 1px `border`.
- Floating (dropdowns, modals): large soft black shadow.
- **Primary button glow:** `0 14px 38px -14px` of accent at 85% — a coloured
  bloom under the PLAY button. Recreate in-game as a blurred accent rectangle
  under the button.
- **Logo glow:** drop shadow `0 0 24px rgba(255,0,255,0.35)`.

---

## 5. Components

Each component below lists its states. Build these once and reuse.

### 5.1 Pixel button (signature component)

The launcher's "chunky" button — used for PLAY, STOP, version picker, account.

- 1px border `rgba(0,0,0,0.45)`
- Bottom "ledge": `box-shadow: 0 2px 0 rgba(0,0,0,0.45)` (a 2px darker strip
  under the button)
- **Pressed:** moves down 2px and the ledge disappears (feels like a physical key)
- **Disabled:** desaturated + darkened (`grayscale 0.6, brightness 0.7`), no
  pointer
- Transition: 80ms

Variants:

| Variant | Fill | Text | Hover |
|---|---|---|---|
| Primary | `accent` | `accent-fg`, pixel font | `accent-hover` + lifts 2px |
| Danger (STOP) | `#EF4444` | white, pixel font, filled square icon | `#DC2626` |
| Neutral (picker/account) | `bg-card` | `accent` | `bg-card-hover` |

Sizes: big hero button 64px tall; bottom-bar PLAY 56px tall with 64px side
padding; neutral 44px tall, min-width 132px.

Labels change with state: `PLAY` → `WORKING` (with spinner) → `STOP`.

### 5.2 Ghost button

Transparent, 1px `border-light`, `zinc-300` text, 8px radius.
Hover: border **and** text become `accent`. Used for secondary actions
("Browse…", "New instance", refresh).

### 5.3 Card

`bg-card`, 1px `border`, 16px radius.
Hover (if clickable): `bg-card-hover`, border → accent 50%.

### 5.4 Settings section

Card with a header row: 36×36 icon tile (`bg-elevated`, 12px radius, accent icon)
+ title (14px semibold `zinc-100`) + description (12px `zinc-500`). Content below
with 16px gap. **Use this exact pattern for every client settings/module page.**

### 5.5 Toggle switch

- Track 44×24, fully round. On = `accent`, off = `border-light`.
- Knob 20×20 white circle with a small shadow, 2px inset; slides left↔right.
- Animate colour + position (~150ms).

### 5.6 Slider

Native-style range with `accent` fill/thumb. Show value on the right in tabular
digits (e.g. `4 GB`, `120 FPS`). Optional preset chips under it.

### 5.7 Input / select

`bg-elevated`, 1px `border-light`, 8px radius, 14px `zinc-100` text, 12×8
padding. Focus: border → `accent`. No glow ring.

### 5.8 Info chip (label + value)

Rounded 12px, `bg-elevated` + `border`, icon 16px `zinc-500`, tiny uppercase
label (9px `zinc-600`) above bold value (14px `zinc-100`).
**Highlighted variant:** `accent` 10% fill, `accent` 30% border, accent icon and
label. Good for HUD info panels and "currently active" indicators.

### 5.9 List row (instance/server/module row)

Full-width card row, 14px padding, 16px gap:

```
[ 48×48 icon tile ]  Title (bold)                     [meta]  [ 36×36 action ]
  accent 12% fill    sub · line · text (zinc-500)              bg-elevated →
  accent icon        "/" separators in zinc-700                accent on hover
```

Rows fade/slide in from the left, staggered 40ms each.

### 5.10 Tag chip

6px radius, 2×8 padding, 10px semibold text, colour at 15% fill + full-colour
text. Accent = launcher/client, emerald = server, orange = Minecraft, zinc = misc.

### 5.11 Progress bar

6px tall, fully round, track `bg-elevated`, fill `accent`, width animates 200ms
ease-out. Status text above it on the left (11px `zinc-500`), `phase – NN%` on
the right. Indeterminate: a segment sliding left→right every 1.2s.

### 5.12 Navigation rail

64px-wide vertical rail on `bg-sidebar`. 44×44 icon buttons, 12px radius, 6px gap,
19px icons. Inactive `zinc-500` (hover `zinc-200`); active `accent` with **one
accent-15% pill that slides** to the active item (200ms ease-out) rather than each
button lighting up separately. Utility button (Logs) pinned to the bottom.

For a **ClickGUI**, reuse this: rail of category icons on the left, content well
on the right.

### 5.13 Content well

The main area sits inside the chrome as an inset panel: `bg` colour, 1px
`border` on top/left/bottom, rounded 12px on its left corners only. The chrome
(`bg-sidebar`) wraps around it.

### 5.14 Modal

Backdrop black 60%, fades in. Panel `bg-card`, 1px `border`, 16px radius, big
shadow, scales from 0.97 + slides 8px. Clicking the backdrop closes it.

### 5.15 Dropdown / popover

Same as modal panel but anchored; opens upward from the bottom bar with
opacity + 8px + scale 0.97, 150ms.

### 5.16 Empty state

Dashed 1px `border`, 16px radius, 24px padding, muted 14px text, optional ghost
button on the right. Never leave a section blank.

### 5.17 Skeleton loading

`bg-card` box with `bg-elevated` bars, pulsing opacity.

### 5.18 Logo

Hexagon frame (dark violet gradient `#2A1A30`→`#150A1A`, accent stroke at 50%)
with a faceted crystal (`#FF8CFF`→`#FF00FF`, `#E000E0`→`#7A00A3`,
`#C400C4`→`#5B0080`), two twinkling 4-point sparkles (`#FF8CFF`), the whole
thing bobbing 8px up/down over 4s. Source: `src/components/AmethystLogo.tsx`
(SVG — export it to PNG at 2×/4× for the client).

Wordmark: "Amethyst" in `zinc-400` + "Launcher" in `accent`, 12px semibold.
For the client use **"Amethyst Client"** with the same split.

---

## 6. Backgrounds & decoration

Use sparingly — one decorative layer per hero panel.

- **Accent wash:** radial gradient from the top-left corner, accent at 22% →
  transparent at 62%.
- **Dot field:** 1px dots of `border-light` every 18px, faded out toward the
  left (mask) so it never sits behind text.
- **Watermark text:** a huge pixel-font word (e.g. the MC version or "AMETHYST")
  at 4% opacity, right-aligned, vertically centred.
- **Stage glow** (skin viewer / player model): accent ellipse glow on the floor
  (42%) + softer accent glow behind the model (20%), over a vertical
  `bg-elevated` → `bg-card` gradient.
- **Grid:** 1px `border-light` (55%) lines every 28px on `bg-elevated`.

---

## 7. Motion

| What | Duration | Easing | Detail |
|---|---|---|---|
| Page switch | 160ms | ease-out | opacity 0 → 1 |
| Card/result appear | 220ms | ease-out | opacity + translateY 6px → 0 |
| Hero panel appear | 300ms | default | opacity + translateY 10px |
| List rows | 250ms | default | translateX -8px → 0, stagger 40ms |
| Nav pill slide | 200ms | ease-out | translateY only |
| Dropdown / modal | 150ms | default | opacity + 8px + scale 0.97 |
| Button press | 80ms | ease | 2px down, scale 0.97–0.98 |
| Primary hover | — | — | lift 2px |
| Colour changes | ~150ms | — | hover/active colours |
| Logo bob | 4s loop | ease-in-out | ±8px |

Animate only opacity/translate/scale. Provide a **"Reduce motion"** setting that
turns off all of the above (the launcher follows the OS setting).

---

## 8. Iconography

- Icon set: **Lucide** (outline, 2px stroke). Download SVGs from lucide.dev and
  rasterise for the client.
- Sizes: 19px nav, 16–17px in chips/buttons, 13px in section titles, 12px meta.
- Icons inherit the text colour; active/primary → `accent`.
- Mapping already used: Play, Library, Compass (Browse), Palette (Skins),
  Settings, Terminal (Logs), Blocks (vanilla), Layers (Fabric), Hammer (Forge),
  Gem (Amethyst), Server, Clock (playtime), Newspaper, Shirt.
- Player heads: 18px avatar from crafatar (`/avatars/{uuid}?size=32&overlay`),
  4px radius, **pixelated** scaling (nearest-neighbour). Never smooth-scale
  Minecraft textures.

---

## 9. Copy / voice

- Short, plain, helpful. Explain what will happen:
  "First launch downloads everything it needs, then starts the game."
- Big action labels are UPPERCASE in the pixel font. Everything else is sentence
  case.
- Time-of-day greeting on the main screen: "Still up" (<5), "Good morning"
  (<12), "Good afternoon" (<18), "Good evening" (<22), "Good night".
- Relative times: "Just now", "5m ago", "2h ago", "3d ago", "1mo ago",
  "Never played".
- Empty states tell the user the next step, not just "Nothing here".

---

## 10. Mapping launcher → client screens

| Client screen | Launcher reference | Notes |
|---|---|---|
| **Title screen** | Play page `LaunchPanel` | Greeting eyebrow + player name in pixel font, big accent **SINGLEPLAYER / MULTIPLAYER** pixel buttons, watermark version text, accent wash + dot field. Logo top-left or centred with bob. |
| **Server list / multiplayer** | `ServersSection` + `InstanceRow` | Rows with icon tile, name, sub-line, ping on the right, always-visible play button. |
| **ClickGUI / mod menu** | `Sidebar` + `SettingsPage` | Left icon rail for categories with sliding pill; right side = settings sections with toggles, sliders, inputs. |
| **Module settings** | `Section` + `Toggle` + `RamAllocation` slider | Same card/section header pattern. |
| **HUD (FPS, CPS, coords, keystrokes, armour)** | Info chip (§5.8) | `bg-card` at ~70–80% alpha so the world shows through, 1px `border`, uppercase tiny label + bold value. Highlight variant for active states (e.g. key pressed = accent fill). |
| **Pause / in-game menu** | Modal (§5.14) | Backdrop 60% black, centred card, pixel-font primary "BACK TO GAME", ghost buttons for the rest. |
| **Loading / connecting** | Bottom-bar progress strip | Status text + `phase – %` + accent progress bar. |
| **Notifications / toasts** | `UpdateBanner`, tag chips | Card with accent left icon, auto-dismiss, slide in. |
| **Cosmetics / skins** | `SkinsPage` stage | Player model on the "stage" background with accent floor glow. |

---

## 11. Do / Don't

**Do**
- Use one accent per screen for the single most important action.
- Keep the pixel font for big labels only.
- Use the surface ramp for depth, 1px borders for edges.
- Read colours from the theme object everywhere.
- Use nearest-neighbour scaling for all Minecraft textures.

**Don't**
- Don't use pure `#000000` or pure `#FFFFFF` for surfaces in dark themes.
- Don't put accent-coloured text on accent fills — use `accent-fg`.
- Don't use the vanilla grey stone Minecraft button texture for primary
  actions; it breaks continuity with the launcher.
- Don't animate layout (width/height) for frequent things; animate transform
  and opacity.
- Don't hard-code hex colours inside widgets.

---

## 12. Checklist before shipping a screen

- [ ] Colours only from the theme object (Dark minimum; Blue/Green ideally)
- [ ] Primary action uses the pixel button (accent, ledge, press-down)
- [ ] Section headers use the uppercase 11px + accent icon pattern
- [ ] Cards: `bg-card`, 1px `border`, consistent radius
- [ ] Hover, pressed, disabled and focus states all exist
- [ ] Motion ≤ 300ms and disabled by "Reduce motion"
- [ ] Empty and loading states designed
- [ ] Textures/avatars render pixelated
- [ ] Text readable over the world (HUD panels have enough background alpha)
