# Tindi

Turn cooking videos into your own recipe book. Android app for the Indian market. Premium, modern, playful while cooking.

**Chosen direction:** Beet Rani. Berry accent with mango highlights, Sora headings, Manrope text. Light and dark are first-class. Prototype: https://claude.ai/artifact/3BWoX4p3aWMsJc7yAuvwrE (artboards "C · Beet Rani, Part 1" and "Part 2").

## Platform rules

- Android native, Material 3 structure. Design baseline 390 by 844 dp. Build in Jetpack Compose or equivalent.
- Light and dark mode. Toggle sits in the Library header, Recipe hero, Cooking view and as a switch on Profile. Default to the system setting until the user toggles, then remember the choice.
- Side margin is `space-5` (20dp) on every screen.
- Every tappable target is at least `touch-min` (48dp). Visual size can be smaller if the hit area is 48dp.
- Never more than 5 primary inputs or actions on one screen. Long forms use tabs (see Review).
- Every data screen ships Loading, Empty and Error states.
- Never rely on colour alone. Icons and labels carry meaning.
- Text and icon colours are chosen to reach 4.5:1 on their ground in both themes. Verify in build.

## Screens

| Screen | Contents | States |
| --- | --- | --- |
| Login and sign up | Wordmark, Log in / Sign up toggle, name (sign up only), email, password, primary button | Field errors to build |
| Library | Title, theme toggle, search, category chips, 2-column recipe cards, extended FAB "Add recipe", bottom nav | Ready, Loading skeleton, First run empty, No matches for a category |
| Recipe | Hero, name, category chips, "Watch original video" row, Ingredients / Steps tabs, sticky "Start cooking" | Ready |
| Add recipe | Pasted link, video card, Description / Pinned / Comments tabs, source text, "Use this text", "Paste text instead" | Ready, Loading skeleton, Error with Try again |
| Review and save | Tabs Details / Ingredients / Steps, sticky "Save recipe" | Ready |
| Cooking view | Close, Step n of 5, theme toggle, 5-segment progress, step number tile, 31sp instruction, "You need" panel, Back and Next | Ready. Keep screen on |
| Categories | Create field, your categories with rename and delete, starter set (read-only), bottom nav | Ready, Empty (no own categories), Delete sheet |
| Profile | Avatar initial, name, email, recipe and category counts, dark mode switch, Log out | Ready |

## Components

| Component | Spec |
| --- | --- |
| Primary button | `button-h`, `radius-pill`, `accent` fill, `on-accent` text, `body-lg` weight 700 |
| Secondary button | Same size, transparent, 1.5dp `line` border, `ink` text |
| Danger button | Same size, `danger` fill, `on-danger` text. Delete only |
| Icon button | 48 by 48 circle, `chip` fill, `ink` icon |
| Search bar | `button-h`, `radius-pill`, `surface` fill, 1.5dp `line` border, search icon, placeholder "Search recipes or ingredients" |
| Category chip | `chip-h`, `radius-pill`. Selected: `accent` fill. Unselected: `surface` with `line` border. "+ New" has a dashed border |
| Recipe card | `radius-card`, `surface`, 1dp `line`. Tile `thumb-card` high with plate illustration, "YouTube" or "Pasted" badge, name `body-lg` 700, meta `caption` in `muted` |
| Extended FAB | `fab-h`, `radius-fab`, `accent`, `elevation-fab`, 20dp from right, above the nav bar |
| Bottom nav | `nav-h`, `surface`, top `line` border, 3 items: Library, Categories, Profile. Active item has a 64 by 32 `accent` pill with `on-accent` icon |
| Tabs | 48dp tall, 3dp `accent` underline on the active tab, inactive text `muted` |
| Text field | `button-h`, `radius-input`, `surface`, 1.5dp `line` border. Focused border `accent`. Label above in `caption` |
| Bottom sheet | `radius-sheet` top corners, `surface`, `elevation-sheet`, 50% black scrim |
| Switch | 52 by 32, off: `line` track, on: `accent` track. Needs label and switch role |
| Skeleton | `radius-sm`, shimmer between `chip` and `line`. Stop the shimmer when reduced motion is on |
| Plate illustration | Two circles (`plate`, `plate-inner`) with `accent`, `mango` and `ink` (25%) dots. Thumbnails sit on `tile-1` to `tile-4` |

## Behaviour

- **Library filter:** category narrows the list, then search looks inside it by name or ingredient.
- **Cooking view:** one step at a time, Back on the first step returns to the Recipe, Finish returns to the Library. Keep the screen on while open.
- **Recipe source:** every YouTube recipe keeps its link. "Watch original video" opens it outside the app.
- **Add recipe:** Description, Pinned comment and Top comments come from the official YouTube API. The user picks one, then splits it into ingredients and steps in Review. No automatic structuring in v1.
- **Screen reader:** icon-only buttons need a label. The theme toggle says "Switch to dark mode" or "Switch to light mode". Chips expose selected state.

## Decisions and assumptions

- Deleting a category moves its recipes to "Uncategorised". Recipes are never deleted. The sheet tells the user this before they confirm.
- Starter categories (Breakfast, Lunch, Snacks, Dinner, Healthy, Protein) cannot be renamed or deleted. Users can add and manage their own.
- Review is split into three tabs because one long form exceeds the 5-input limit.
- Thumbnails are drawn plates. A real video thumbnail from the API can replace them later, keeping the badge.
- Not in v1: automatic structuring with AI, Instagram link fetching, scaling servings, favourites and tags, shared family vault.
