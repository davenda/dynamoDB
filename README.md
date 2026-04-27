# Handoff: DynamoDB Plugin for IntelliJ

## Overview

A modern redesign of a DynamoDB browser/query plugin for IntelliJ-family IDEs. Replaces a cramped, low-hierarchy single-pane UI with a structured 3-zone layout: **sidebar** (connections / schema / indexes / history) + **query editor** (with mode tabs and PartiQL syntax highlighting) + **results table** (with semantic cell rendering and a slide-in row inspector). Includes an insert/edit row modal and live tweaks for theme, density, accent, and cell rendering.

## About the Design Files

The files in this bundle are **design references created in HTML** — interactive prototypes showing the intended look and behavior. They are **not** production code to copy directly. The task is to recreate these designs inside the **IntelliJ Platform plugin environment** (Kotlin/Java + Swing/JCEF) using JetBrains' existing UI patterns, components (`JBPanel`, `JBList`, `JBTabbedPane`, `JBTable`, `EditorTextField`), color keys (`JBColor`, `JBUI.CurrentTheme`), and icon set (`AllIcons`).

If you're targeting a different shell (e.g., a web companion app), use the codebase's existing framework — **do not ship the prototype HTML**.

## Fidelity

**High-fidelity.** Pixel-perfect mockups with final colors, type, spacing, and interactions. Recreate pixel-faithfully using the IntelliJ Platform's component library — match the IDE's New UI conventions, but the specific layouts, cell renderers, inspector panel, and modal flow shown here are the source of truth.

## Screens / Views

### 1. Main window — 4-row grid

```
┌─────────────────────────────────────────────────────────────┐
│ Title bar (28px) — breadcrumbs + connection pill            │
├─────────────────────────────────────────────────────────────┤
│ Toolbar (36px) — Run / Clear │ New / Edit / Delete │ ...    │
├──────────────┬──────────────────────────────────────────────┤
│              │ Query editor (auto, 100–180px)               │
│  Sidebar     │ ┌────────────────────────────────────────┐   │
│  280px       │ │ tabs · gutter · code · foot            │   │
│              │ ├────────────────────────────────────────┤   │
│              │ │ Results toolbar                        │   │
│              │ │ Results table         │ Inspector 360px│   │
│              │ │ Pager                  │               │   │
├──────────────┴──────────────────────────────────────────────┤
│ Status bar (24px)                                           │
└─────────────────────────────────────────────────────────────┘
```

### 2. Sidebar (280 px wide)

- **Tab strip** (top): `Tables` · `Schema` · `Indexes` · `History`. Active tab gets a 2px bottom border in `--accent`.
- **Search row** (26px tall): rounded `--bg-2` field with magnifier icon and `⌘P` kbd hint.
- **Tree** (Tables tab): `My DynamoDB` (cloud icon, green status dot) → `us-east-1` → individual tables. Active table has `--accent-soft` background and a 2px left accent rail.
- **Schema tab**: lists columns with type pills (`S`, `N`, `BOOL`) on the right. PK columns show a key icon in `--accent`.
- **Indexes tab**: grouped by table with `GSI` (info-blue) and `LSI` (warn-amber) type pills.
- **History tab**: list of recent queries (monospace), with relative time + ms.

### 3. Query editor

- **Tab strip** (32px): `query.sql` active tab, `+ New` ghost tab, right-aligned `⌘↵ run` kbd hint and 3 mini icon buttons (format, save, copy).
- **Body**: 44px gutter (line numbers, JetBrains Mono 12px) + code area (JetBrains Mono 13px, `line-height: 1.6`).
- **Syntax colors**:
  - Keywords (`SELECT`, `FROM`, `WHERE`, `LIMIT`): `--syn-kw` `#cf8eff` dark / `#7d2db8` light, weight 500
  - Numbers: `--syn-num` `#d6a772` / `#8a5a16`
  - Strings: `--syn-str` `#a3c780` / `#2c6e2c`
  - Comments: `--syn-dim`, italic
- **Footer** (1 line): segmented control `PartiQL | Native API | Raw JSON` + chips for PK, consistency, last-run timing + RCU.

### 4. Results toolbar

Title row: table icon + table name + 3 stat pills (`<rows>`, `<ms> green dot`, `<RCU>`). Right side: filter input (220px min) + 2 mini icon buttons (sort/columns, inspector toggle).

### 5. Results table

Cell rendering — **semantic** mode (default):
| Column | Render |
|---|---|
| `cafe_id` (PK) | JetBrains Mono, color `--accent` |
| `cafe_name` | plain text, `--fg-1` |
| `city` | dimmed `--fg-3` |
| `created_at` | "2 min ago" + dim absolute date right of it |
| `open_24h`, `wifi` | colored pill: `true` green-tinted bg + green text + dot, `false` red-tinted |
| `price_tier` | filled `$` glyphs in `--good`, ghost `$` in `--fg-3` to fill to 4 |
| `rating` | 56×4px progress bar (filled with `--accent`) + numeric value |
| `seats` | mono right-aligned, turns `--warn` if < 20 |
| `specialty` | small bordered pill in `--bg-2` |

**Raw** mode: drop pills/bars; render literal values in JetBrains Mono. Booleans colored green/red.

Header: sticky, `--bg-1` background, 11.5px `--fg-2` weight 500. PK header columns get a `PK` badge prefix in `--accent` on `--accent-soft`. Tiny `↕` sort indicator.

Rows: alternating subtle stripe. Hover → `--bg-2`. Selected → `--accent-soft`.

### 6. Pager

`Prev / Page X of Y / Next` + per-page select + right-aligned `Showing N–M of T` summary. Comfortable spacing (8/12px), JetBrains Mono for numerics.

### 7. Row Inspector (360 px slide-in panel)

- **Head**: monospace ID in `--accent`, name in `--fg-1`, edit/copy mini buttons, close `×`.
- **Tabs**: `Attributes` · `JSON` · `History`.
- **Attributes**: `<dl>` grid, 110px label column (mono name + type pill + optional PK pill), value column rendered with the same semantic cell components as the table.
- **JSON**: pretty-printed DynamoDB attribute-value JSON, with k/s/n/b color tokens.
- **History**: timeline of edits with diff-style before → after.

### 8. Insert row modal

560px modal, scrim `rgba(0,0,0,.45)` + 2px backdrop blur. Form rows: 130px label column (mono field name + type pill + PK pill + required asterisk) + input column. Boolean fields use a true/false segmented control. Pick-list fields use a select. Footer shows estimated WCU cost + Cancel + primary Save button.

### 9. Status bar (24 px)

Connection pill (green dot) · `UTF-8` · `LF` · `PartiQL` … right-aligned: `Ln X, Col Y` · plugin version.

## Interactions & Behavior

- **Run query**: `⌘↵` → submits whatever is in the editor. While running, swap Run icon to spinner, gray out toolbar.
- **Row click**: select row (highlight + persist as `selectedId`), open inspector if not already shown.
- **Inspector close**: hides panel + posts state so toolbar reflects it.
- **Insert flow**: `New item` toolbar button → modal. Submit fires no-op in prototype; in plugin, call `PutItem`, optimistically prepend to results, then refresh.
- **Filter input**: live filters the visible result set (case-insensitive substring across all columns).
- **Pager**: prev/next + page-size select (10/20/50/100). Reset to page 1 when filter or page size changes.
- **Tab switching** (sidebar + query + inspector): instant; underline transitions are CSS only.
- **Theme**: dark by default; light theme provided. Match the IDE's `LafManager` setting in production — don't expose a separate toggle.
- **Density**: `compact` (24px rows) / `comfortable` (30px rows). Affects table only.

## State Management

| Key | Type | Notes |
|---|---|---|
| `activeTable` | string | Selected table from sidebar |
| `mode` | `"sql" \| "native" \| "raw"` | Query mode tab |
| `query` | string | Editor content |
| `page`, `perPage` | number | Pagination |
| `selected` | string \| null | Selected row PK |
| `showInsert` | boolean | Insert modal open |
| `filter` | string | Live filter on results |
| `cellMode` | `"raw" \| "semantic"` | Cell rendering mode |
| `density` | `"compact" \| "comfortable"` | Row density |
| `dark` | boolean | Theme |
| `accent` | hex string | Accent color |
| `showInspector` | boolean | Inspector visibility |

In the IntelliJ plugin, persist these in `PropertiesComponent` (per-project for `activeTable`/`query`, per-app for `density`/`cellMode`/`accent`).

## Design Tokens

### Dark theme
```
--bg-0  #1f2126   app
--bg-1  #25272d   panel
--bg-2  #2b2d34   raised / hover-target
--bg-3  #34363e   hover
--line  #34363e   1px borders
--line-strong #3e4049
--fg-0  #e6e6ea   primary text
--fg-1  #c4c5cc   secondary
--fg-2  #8e9099   tertiary / labels
--fg-3  #62646c   dimmed / placeholder
--accent #6e8eff  (default — user-tweakable)
--good  #7fb377
--warn  #d8a657
--bad   #e26d6d
--info  #6ea7d8
```

### Light theme
```
--bg-0  #fbfbfa
--bg-1  #ffffff
--bg-2  #f5f5f3
--bg-3  #ececea
--line  #e7e7e3
--line-strong #d6d6d1
--fg-0  #1d1f24
--fg-1  #3a3d44
--fg-2  #6f7280
--fg-3  #9a9ca4
--accent #4f6df0
--good  #2f7d2f
--warn  #a86b00
--bad   #b53a3a
--info  #266ea1
```

In the plugin, prefer JetBrains-native color keys when they map cleanly:
- `--bg-0` → `UIUtil.getPanelBackground()`
- `--bg-1` → `JBUI.CurrentTheme.ToolWindow.background()`
- `--accent` → `JBUI.CurrentTheme.Focus.focusColor()` (or expose as a setting)
- `--good` / `--bad` / `--warn` → `JBColor.namedColor("Component.successColor"...)` etc.

### Spacing
4 / 6 / 8 / 10 / 12 / 14 / 16 px

### Radius
- 3px: small chips, kbd
- 4px: buttons, segmented controls, form inputs
- 6px: search/filter inputs
- 10px: modal corners

### Type
- UI: Inter 400/450/500/600 (substitute the IDE's default sans, e.g., `JBFont.regular()` / `JBFont.label()` in plugin).
- Code/data: JetBrains Mono 400/500/600. Already shipped with the IDE.
- Sizes: 10–11.5px (labels/meta), 12.5–13px (body), 14px (modal heading).

### Shadows
- Modal: `0 30px 80px rgba(0,0,0,.5)`
- Tweaks panel only: backdrop blur (cosmetic, prototype-only)

## Components in the Prototype

- `Sidebar` — `src/sidebar.jsx`
- `QueryEditor` — `src/query-editor.jsx` (with `highlight()` SQL tokenizer)
- `ResultsTable` + cell renderers (`PriceCell`, `BoolCell`, `RatingCell`) — `src/results-table.jsx`
- `Inspector` — `src/inspector.jsx`
- `InsertModal` — `src/insert-modal.jsx`
- `App` (root, state, layout glue) — `src/app.jsx`
- Icon set — `src/icons.jsx` (replace with `AllIcons.*` in plugin)
- Sample data — `src/data.jsx` (cafe domain — replace with real DynamoDB calls)

## Assets

Icons are inline SVG (16×16 viewBox, currentColor stroke 1.5). In the IntelliJ plugin, replace with `AllIcons` equivalents:
- play → `AllIcons.Actions.Execute`
- clear → `AllIcons.Actions.GC`
- refresh → `AllIcons.Actions.Refresh`
- plus → `AllIcons.General.Add`
- trash → `AllIcons.Actions.GC`
- search → `AllIcons.Actions.Search`
- table → `AllIcons.Nodes.DataTables`
- index → `AllIcons.Nodes.DataColumn`
- key → `AllIcons.Nodes.PpLib` (or custom)
- close → `AllIcons.Actions.Close`

No external image assets are used.

## Files

The prototype HTML lives in `prototype/`:

- `DynamoDB Plugin.html` — entry point, all CSS, loads JSX modules
- `tweaks-panel.jsx` — design-tweak shell (prototype-only, do not port)
- `src/icons.jsx`
- `src/data.jsx`
- `src/sidebar.jsx`
- `src/query-editor.jsx`
- `src/results-table.jsx`
- `src/inspector.jsx`
- `src/insert-modal.jsx`
- `src/app.jsx`

Open `DynamoDB Plugin.html` in a browser to interact with the prototype. Toggle the **Tweaks** button to compare semantic vs raw cell rendering, switch density, and try light theme.
