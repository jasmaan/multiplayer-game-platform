# Design Constants & Constraints

All FXML files and controllers **must** adhere to these values. Do not invent new colours, fonts, or spacing - pick from this list only. This is what ensures every page looks like it belongs to the same app.

---

## Colour Palette

| Token | Hex / RGBA | Usage |
|---|---|---|
| `COLOR_BG_GRADIENT` | `linear-gradient(to bottom right, #0b1f33, #4da6ff)` | Root background on every page |
| `COLOR_PANEL_DARK` | `rgba(5,12,24,0.97)` | Sidebar, header bar, left/right panel backgrounds |
| `COLOR_CARD` | `rgba(10,25,45,0.8)` | List row cards, stat cards |
| `COLOR_CARD_DEEP` | `rgba(8,20,42,0.92)` | Elevated popup/modal card backgrounds |
| `COLOR_CARD_INPUT` | `rgba(10,25,45,0.9)` | Text field backgrounds (inactive) |
| `COLOR_CARD_INPUT_ACTIVE` | `rgba(10,25,50,0.95)` | Text field backgrounds (focused/edit mode) |
| `COLOR_PRIMARY` | `#4da6ff` | Active nav, borders, icons, leaderboard rank numbers |
| `COLOR_PRIMARY_DIM` | `rgba(77,166,255,0.15)` | Button hover fills, selected avatar card bg |
| `COLOR_PRIMARY_BORDER` | `rgba(77,166,255,0.25)` | Standard border on cards and buttons |
| `COLOR_PRIMARY_BORDER_FAINT` | `rgba(77,166,255,0.1)` | Dividers, panel edge borders |
| `COLOR_TEXT_PRIMARY` | `#e6f3ff` | Page titles, active username, primary labels |
| `COLOR_TEXT_SECONDARY` | `#b9d9ff` | Body text, inactive field values |
| `COLOR_TEXT_MUTED` | `#7fbfff` | Subheadings, helper text, secondary buttons |
| `COLOR_TEXT_GHOST` | `#3d6080` | Placeholder text, empty states, metadata |
| `COLOR_DIVIDER` | `rgba(77,166,255,0.08)` | Thin horizontal rule between sections |
| `COLOR_SUCCESS` | `#40d090` | Success toasts, Checkers accent, win indicators |
| `COLOR_ERROR` | `#ff6b6b` | Error toasts, error label text |
| `COLOR_DANGER_BG` | `rgba(200,50,50,0.15)` | Forfeit / cancel / destructive button fill |
| `COLOR_DANGER_BORDER` | `rgba(255,100,100,0.3)` | Forfeit / cancel button border |
| `COLOR_DANGER_TEXT` | `#ff8080` | Forfeit / cancel button label |

## Game accent colours (use exactly these - referenced in controller logic too)

| Game | Accent | Usage |
|---|---|---|
| Tic-Tac-Toe | `#4da6ff` | Top bar stripe, icon tint, dot colour |
| Connect Four | `#ff8040` | Top bar stripe, icon tint, dot colour |
| Checkers | `#40d090` | Top bar stripe, icon tint, dot colour |

## Rank colours / assets

| Rank | Shield image path |
|---|---|
| Bronze | `/images/BronzeRankShield.png` |
| Silver | `/images/SilverRankShield.png` |
| Gold | `/images/GoldRankSheild.png` *(keep typo - matches file on disk)* |

---

## Typography

| Role | Font size | Font weight | Colour |
|---|---|---|---|
| Page / modal title | `22px` | bold | `#e6f3ff` |
| Section heading | `16px` | bold | `#e6f3ff` |
| Card title / username | `15px` | bold | `#e6f3ff` |
| Nav button / CTA label | `13px` | bold | context-dependent |
| Body / list item | `12px` | normal | `#b9d9ff` |
| Small label / stat | `11px` | normal | `#7fbfff` |
| Micro / metadata | `10px` | bold or normal | `#3d6080` |
| Leaderboard rank # | `12px` | bold | `#4da6ff` |
| Stat card value | `20px` | bold | `#4da6ff` |
| Stat card label | `10px` | normal | `#b9d9ff` |

## Spacing & Sizing

| Element | Value |
|---|---|
| Page dimensions (full dashboard/game) | `1060-1200 × 700-720` |
| Page dimensions (modal/queue) | `440-560 × 400-480` |
| Left/right sidebar width | `210-240px` |
| Header bar height | `~60px (padding 16 24)` |
| Standard card border radius | `10-12px` |
| Large card / modal border radius | `18-22px` |
| Button border radius | `8-10px` |
| Avatar circle border radius | `19px (radius on Circle clip)` |
| Standard button height | `42-44px` |
| Small back/action button height | `32-34px` |
| Card padding | `8 12 (small) · 12 14 (medium) · 24 22 (large)` |
| Section spacing between cards | `10px` |
| Sidebar padding | `28 16 24 16` |

---

## Button Styles (copy these exactly)

| Type | Style string |
|---|---|
| Primary CTA (Play, Create Room) | `linear-gradient(to right, #4da6ff, #1e90ff)` fill, white text, bold, `13px`, radius `10` |
| Secondary outline (Back, Refresh, Settings nav) | `rgba(77,166,255,0.12)` fill, `#7fbfff` text, bold, `13px`, border `rgba(77,166,255,0.25)`, radius `8` |
| Ghost / queue join | `rgba(77,166,255,0.15)` fill, `#4da6ff` text, bold, `13px`, border `rgba(77,166,255,0.4)`, radius `10` |
| Destructive (Forfeit, Cancel) | `rgba(200,50,50,0.15)` fill, `#ff8080` text, bold, `13px`, border `rgba(255,100,100,0.3)`, radius `10` |
| Game CTA (e.g. Drop Piece in C4) | `linear-gradient(to right, #ff8040, #e05520)` fill, white text, bold, `13px`, radius `10` |

## Layout Rules

1. Every page root uses the blue gradient background: `linear-gradient(to bottom right, #0b1f33, #4da6ff)`. No exceptions.
2. Sidebars and headers always use `rgba(5,12,24,0.97)` - the near-black panel colour. Never a solid colour.
3. No Alert dialogs for user-facing feedback. All errors and success messages must be inline labels or the toast notification pattern (`settingsNotification` HBox with auto-dismiss `Timeline`).
4. No hardcoded player names or game state in FXML. All dynamic content is set in the controller via `setPlatformCore()`.
5. `setPlatformCore(PlatformCore)` is the single entry point for every controller. No controller instantiates `PlatformCore` directly (`new PlatformCore()` is banned in UI code).
6. Navigation always passes `platformCore` to the destination controller. Never load an FXML and skip wiring `setPlatformCore`.
7. Border widths: use `1` for standard cards, `1.5` for emphasis (selected state, active player card), `0 0 1 0` for bottom-only header borders, `0 1 0 0` for right-only sidebar borders.
8. `-fx-cursor: hand` must be set on every clickable element (buttons, leaderboard rows, avatar cards, user card).
9. Images are always referenced via relative `@../images/filename.png` paths in FXML. Never absolute paths.

---

## Controller Constraints

| Rule | Detail |
|---|---|
| Game key strings | Always lowercase, no spaces: `"tictactoe"`, `"connectfour"`, `"checkers"` |
| Display name mapping | `"tictactoe" → "Tic-Tac-Toe"`, `"connectfour" → "Connect Four"`, `"checkers" → "Checkers"` |
| Rank key strings | Exactly: `"Bronze"`, `"Silver"`, `"Gold"` (capital first letter) |
| Avatar path format | Always `/images/avatar1.png` through `/images/avatar4.png` (leading slash, no `@`) |
| Toast auto-dismiss | Use `Timeline` with `KeyFrame` at `Duration.seconds(3)` - never `Thread.sleep()` |
| JavaFX thread safety | Any callback from `PlatformCore` (e.g. `roomJoinNotificationCallback`, `chatCallback`) must wrap UI updates in `Platform.runLater()` |
| Scene switching | Always get the `Stage` via `(Stage) ((Node) event.getSource()).getScene().getWindow()` - never create a new `Stage` |

10. `xmlns:fx="http://javafx.com/fxml/1"` and `xmlns="http://javafx.com/javafx/25"` on every root element - no other JavaFX version string.
