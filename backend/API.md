# Tindi API

Base URL: `http://<tailscale-host>:7000/api/v1` (plain HTTP). JSON in and out, UTF-8.
Send `Content-Type: application/json` on requests with a body.
Everything except `/health`, `/auth/signup`, `/auth/login` needs `Authorization: Bearer <token>`.

- IDs are UUID strings. Timestamps are ISO-8601 UTC (`2026-11-25T15:36:20.137879Z`).
- Strings are trimmed by the server. Email is stored lowercase.
- Tokens never expire. Only logout revokes one. One user can hold many tokens (one per device).
- A resource owned by another user returns `404 NOT_FOUND`, never 403.
- Unknown JSON fields are ignored.

## Errors

Every error has this shape (`fields` only when relevant):

```json
{"error":{"code":"VALIDATION_FAILED","message":"Please fix the highlighted fields","fields":{"email":"Enter a valid email"}}}
```

| HTTP | code | when |
|---|---|---|
| 400 | `VALIDATION_FAILED` | bad body or field. `fields` maps field path to a short message, e.g. `name`, `ingredients[0].item`, `steps[2]`, `categoryIds`, `stepDetails[0].illustration` |
| 401 | `INVALID_CREDENTIALS` | login failed (does not say whether email or password) |
| 401 | `UNAUTHORIZED` | missing, unknown or revoked token. Clear the stored token and show login |
| 403 | `STARTER_READ_ONLY` | rename or delete of a starter category |
| 404 | `NOT_FOUND` | missing, or belongs to someone else |
| 409 | `EMAIL_TAKEN` | `fields.email` |
| 409 | `CATEGORY_NAME_TAKEN` | `fields.name` |
| 405 / 500 | `METHOD_NOT_ALLOWED` / `INTERNAL_ERROR` | unexpected |
| 404/429/502/503 | `NOT_FOUND`, `SOURCE_QUOTA_EXCEEDED`, `SOURCE_UNAVAILABLE`, `SOURCE_NOT_CONFIGURED` | only from `POST /source/fetch` (YouTube problems), see below |

A malformed body or a bad enum value (e.g. `sourceType`) is `VALIDATION_FAILED`; `fields` may name the path with the message `Invalid value`.

## Limits

| field | rule |
|---|---|
| name (user) | 1-60 |
| email | valid, max 254, unique ignoring case |
| password | 6-72 characters (and at most 72 bytes). Not trimmed |
| recipe name | 1-120 |
| ingredients | 0-100 items. `item` 1-120, `quantity` 0-40 (missing = `""`) |
| steps | 0-100 items, each 1-1000 |
| stepDetails | optional. One entry for each step, see [Step details](#step-details) |
| sourceUrl | optional, max 2048; blank becomes `null` |
| thumbnailUrl | optional, max 2048, must start with `https://`; blank becomes `null`. Only the address is stored, never the image |
| category name | 1-30, unique per user ignoring case, cannot equal a starter name |

## Auth

### POST /auth/signup (open) -> 201
```json
{"name":"Ravi","email":"ravi@example.com","password":"secret123"}
```
```json
{"token":"RWjMxtPT84yulMhBSHRvdWoQL53Ie_yvzAE5JK6wXEI","user":{"id":"f686df4d-f8e8-4c59-be28-6f3168790326","name":"Ravi","email":"ravi@example.com"}}
```
Errors: 400 `VALIDATION_FAILED`, 409 `EMAIL_TAKEN`.
The token is shown only here and on login. If the server has `tindi.seed-sample-recipes=true`, the new account already has 4 sample recipes.

### POST /auth/login (open) -> 200
```json
{"email":"ravi@example.com","password":"secret123"}
```
Response is the same as signup (a new token). Errors: 400 (blank fields), 401 `INVALID_CREDENTIALS`.

### POST /auth/logout -> 204
No body. Deletes the token used for this call; it stops working at once. Other devices are unaffected.

### GET /me -> 200
```json
{"id":"f686df4d-f8e8-4c59-be28-6f3168790326","name":"Ravi","email":"ravi@example.com","recipeCount":1,"categoryCount":1}
```
`categoryCount` counts the user's own categories only (not the 6 starters).

### GET /health (open) -> 200
```json
{"status":"UP"}
```

## Categories

Starter categories (Breakfast, Lunch, Snacks, Dinner, Healthy, Protein) have fixed ids:
`00000000-0000-4000-8000-00000000000N` where N = 1 Breakfast, 2 Lunch, 3 Snacks, 4 Dinner, 5 Healthy, 6 Protein.
"Uncategorised" is not a category: it is a recipe with an empty `categoryIds`.

### GET /categories -> 200
Starters first (fixed order), then the user's own by name (case-insensitive).
```json
[{"id":"00000000-0000-4000-8000-000000000001","name":"Breakfast","starter":true},
 {"id":"e734db4c-c242-441f-811e-140ee5fb4064","name":"Sweets","starter":false}]
```

### POST /categories -> 201
```json
{"name":"Sweets"}
```
Response: `{"id":"e734db4c-c242-441f-811e-140ee5fb4064","name":"Sweets","starter":false}`
Errors: 400, 409 `CATEGORY_NAME_TAKEN`.

### PATCH /categories/{id} -> 200
Body `{"name":"Desserts"}`, response is the category. Errors: 400, 403 `STARTER_READ_ONLY`, 404, 409 `CATEGORY_NAME_TAKEN`.

### DELETE /categories/{id} -> 204
Recipes are kept; they just lose the link (and become uncategorised if it was their last). Errors: 403 `STARTER_READ_ONLY`, 404.

## Recipes

Recipe object:
```json
{"id":"f25efdc2-9098-4eb7-9a2d-c9d0a1247334","name":"Halwa",
 "ingredients":[{"quantity":"250 g","item":"Paneer"}],
 "steps":["Blend tomato and onion"],
 "stepDetails":[{"name":"Blend","ingredientIndexes":[0],"illustration":"BLEND"}],
 "categoryIds":["00000000-0000-4000-8000-000000000004","e734db4c-c242-441f-811e-140ee5fb4064"],
 "sourceType":"PASTED","sourceUrl":null,"thumbnailUrl":null,
 "createdAt":"2026-11-25T15:36:20.137879Z","updatedAt":"2026-11-25T15:36:20.137879Z"}
```
`sourceType` is `YOUTUBE` or `PASTED`. `categoryIds` has no guaranteed order; match by id. `stepDetails` is always present and is either a list or `null`, see [Step details](#step-details).

### GET /recipes -> 200
Array of every recipe of the user with all fields, newest first. No paging, no server search.

### GET /recipes/{id} -> 200
One recipe object, or 404.

### POST /recipes -> 201
```json
{"name":"Halwa","ingredients":[{"quantity":"250 g","item":"Paneer"}],"steps":["Blend tomato and onion"],
 "stepDetails":[{"name":"Blend","ingredientIndexes":[0],"illustration":"BLEND"}],
 "categoryIds":["00000000-0000-4000-8000-000000000004"],"sourceType":"PASTED","sourceUrl":null,"thumbnailUrl":null}
```
Required: `name`, `ingredients`, `steps`, `sourceType`. `stepDetails` is optional (omitted or `null` saves none). `categoryIds` may be omitted or `[]`. `sourceUrl` and `thumbnailUrl` are optional (a YouTube fetch returns both, send them back as they came). `PUT` replaces the whole recipe, so an omitted `thumbnailUrl` clears it.
`categoryIds` may hold starter ids and the user's own ids only; anything else is `400 VALIDATION_FAILED` with `fields.categoryIds`.
Response: the recipe object.

### PUT /recipes/{id} -> 200
Full replace, same body as POST, with one exception: **leaving out `stepDetails` does not always clear it**. `createdAt` is kept, `updatedAt` changes. Errors: 400, 404.

| `stepDetails` in the PUT body | result |
|---|---|
| a list | validated against the `ingredients` and `steps` in this body, then replaces the stored one |
| omitted or `null`, `steps` unchanged and every ingredient `item` unchanged and in the same position | the stored value is kept (changing the recipe name, a `quantity`, categories and so on does not affect it) |
| omitted or `null`, any step changed, added, removed or moved | becomes `null` |
| omitted or `null`, any ingredient `item` changed, added, removed or moved | becomes `null` (the stored indexes could point at the wrong ingredients) |

Steps and items are compared after trimming. There is no separate "clear" request.

### DELETE /recipes/{id} -> 204
Errors: 404.

### Step details

Optional labels for showing each step: a short name, the ingredients it uses and which illustration to show. `stepDetails` is a list **parallel to `steps`**: same length, same order, `stepDetails[i]` describes `steps[i]`.

```json
"ingredients":[{"quantity":"2","item":"tomatoes"},{"quantity":"1 tbsp","item":"butter"}],
"steps":["Blend the tomatoes.","Melt the butter and simmer the puree.","Serve hot."],
"stepDetails":[
  {"name":"Blend","ingredientIndexes":[0],"illustration":"BLEND"},
  {"name":"Simmer","ingredientIndexes":[1,0],"illustration":"SIMMER"},
  {"name":"Serve","ingredientIndexes":[],"illustration":"SERVE"}]
```

| field | in responses | rule on POST and PUT |
|---|---|---|
| `name` | string, may be `""`. A short label, normally 1-2 words in the language of the step | at most 14 characters after trimming (UTF-16 units, as `String.length` in Java and Kotlin). Omitted or `null` becomes `""`. Word count and language are not checked |
| `ingredientIndexes` | list of 0-based positions in `ingredients`, may be `[]`. No guaranteed order | each from 0 to `ingredients` length - 1. Omitted or `null` becomes `[]`. At most 100 entries. Repeats are not rejected |
| `illustration` | one of the values below | required, exactly as spelled below (upper case) |

Illustrations (fixed set): `PREP`, `SOAK`, `BLEND`, `MIX`, `SAUTE`, `SIMMER`, `BOIL`, `FRY`, `BAKE`, `STEAM`, `REST`, `SERVE`, `GENERIC`. Clients should show `GENERIC` for a value they do not know, so the set can grow later.

Validation failures are `400 VALIDATION_FAILED` and nothing is saved:

| `fields` key | problem |
|---|---|
| `stepDetails` | length differs from `steps` (or more than 100 entries) |
| `stepDetails[1]` | the entry is `null` |
| `stepDetails[1].name` | too long |
| `stepDetails[1].illustration` | missing or not in the set |
| `stepDetails[1].ingredientIndexes[0]` | out of range, `null` or not a number |

Not every problem is reported at once: length, range and illustration problems may only show after basic field problems (such as a long name) are fixed.

**`null` is normal.** It is returned for recipes saved before this field existed, recipes saved without it (including seeded samples and suggestions made by the rules splitter) and after a PUT that cleared it. The client must then show the plain steps.

The column is `recipes.step_details` (nullable JSON), added by the Flyway migration `V3__recipe_step_details.sql` when the server starts.

## Source fetch and structuring

Both endpoints need `Authorization: Bearer <token>`. Nothing is saved: the app shows the result in the review screen, and the user then saves with `POST /recipes`.

`/source/fetch` uses the official YouTube Data API v3 on the server, with the key from `tindi.youtube-api-key`. The `suggested` recipe in both endpoints comes from Google Gemini (`tindi.gemini-api-key`), with a rule based splitter as fallback. The phone never sees either key.

### POST /source/fetch -> 200
```json
{"url":"https://youtu.be/dQw4w9WgXcQ"}
```
Accepted links: `youtube.com/watch?v=ID` (also `www.` and `m.`), `youtu.be/ID`, `youtube.com/shorts/ID`. The scheme may be left out. Extra query parameters are ignored. Anything else (Instagram, other sites, channel or playlist pages) is rejected before YouTube is called.

```json
{
  "title": "Paneer Butter Masala",
  "channelName": "Tindi Test Kitchen",
  "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
  "sourceUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "sourceType": "YOUTUBE",
  "description": "Creamy paneer butter masala.\n\nIngredients:\n- 250 g paneer ...",
  "comments": [
    {"author": "Tindi Test Kitchen", "text": "Pinned: use kasuri methi", "likeCount": 120, "isPinnedLikely": true},
    {"author": "A Fan", "text": "Made this last night!", "likeCount": 7, "isPinnedLikely": false}
  ],
  "suggested": {
    "ingredients": ["250 g paneer", "2 tbsp butter"],
    "steps": ["Melt the butter.", "Add the puree and simmer."],
    "stepDetails": [
      {"name": "Melt", "ingredientIndexes": [1], "illustration": "SAUTE"},
      {"name": "Simmer", "ingredientIndexes": [], "illustration": "SIMMER"}
    ],
    "confidence": "HIGH",
    "foundIn": "DESCRIPTION",
    "method": "LLM",
    "warnings": []
  }
}
```
- `sourceUrl` is the canonical `https://www.youtube.com/watch?v=ID`. Send it back as `sourceUrl` when creating the recipe, with `sourceType` `YOUTUBE`.
- `thumbnailUrl` is the largest thumbnail YouTube offers, or `null`.
- `comments` holds up to 5 top comments by relevance (plain text). It is `[]` when comments are disabled or YouTube fails to return them; the request still succeeds. `isPinnedLikely` is a guess: the YouTube API does not say which comment is pinned, so it is `true` when the comment was written by the video's own channel.
- `description` and `comments` are always returned raw, whatever `suggested` says.

See [The `suggested` object](#the-suggested-object) for what it holds. A Gemini problem never fails the request: the rules splitter answers instead.

Errors (same error format as everywhere):

| HTTP | code | when |
|---|---|---|
| 400 | `VALIDATION_FAILED` | blank or missing `url`, or not a supported YouTube link. `fields.url` says: "Only YouTube links can be fetched. Paste Instagram and other links as text instead." |
| 401 | `UNAUTHORIZED` | missing or revoked token |
| 404 | `NOT_FOUND` | video does not exist or is private |
| 429 | `SOURCE_QUOTA_EXCEEDED` | the server's daily YouTube quota is used up. Offer to paste text instead |
| 502 | `SOURCE_UNAVAILABLE` | YouTube failed, timed out or answered something unexpected (5 s timeout, no retries) |
| 503 | `SOURCE_NOT_CONFIGURED` | no YouTube key on the server. The rest of the API works normally |

A bad link is checked first, so it returns `VALIDATION_FAILED` even when the key is missing.

### POST /source/structure -> 200
For pasted text. No YouTube call and no YouTube key needed.
```json
{"text":"Ingredients:\n- 2 eggs\n- salt\nMethod:\n- Beat the eggs.\n- Fry them."}
```
```json
{
  "suggested": {
    "ingredients": ["2 eggs", "salt"],
    "steps": ["Beat the eggs.", "Fry them."],
    "stepDetails": [
      {"name": "Beat", "ingredientIndexes": [0], "illustration": "MIX"},
      {"name": "Fry", "ingredientIndexes": [0], "illustration": "FRY"}
    ],
    "confidence": "HIGH",
    "foundIn": "PASTED",
    "method": "LLM",
    "warnings": []
  }
}
```
`text` is required, at most 20000 characters.

Errors: 400 `VALIDATION_FAILED` (`fields.text`), 401 `UNAUTHORIZED`. Gemini problems are not errors.

### The `suggested` object

| field | values |
|---|---|
| `ingredients` | strings, at most 100, each at most 120 characters |
| `steps` | strings in order, at most 100, each at most 1000 characters |
| `stepDetails` | [step details](#step-details) parallel to `steps`, or `null`. Indexes point into `suggested.ingredients` |
| `method` | `LLM` (Gemini, checked against the source) or `RULES` (fallback splitter) |
| `confidence` | `HIGH`, `LOW` or `NONE`, see below |
| `foundIn` | `DESCRIPTION`, `COMMENT` (fetch), `PASTED` (structure) or `NONE` |
| `warnings` | short English sentences to show the user, often `[]`. Always `[]` for `RULES` |

| `confidence` | meaning |
|---|---|
| `HIGH` | `method` is `LLM`, every item passed the check against the source, and there is no warning |
| `LOW` | something found, but the user should check it. Every non-empty `RULES` result is `LOW` |
| `NONE` | nothing found. `ingredients` and `steps` are `[]` and `foundIn` is `NONE` |

`stepDetails` is `null` when `method` is `RULES`, when no steps were found, and when Gemini sent no labels. If only some steps came without labels, those entries are `{"name":"","ingredientIndexes":[],"illustration":"GENERIC"}`. The labels are written by the model and are **not** checked against the source, so `confidence`, `warnings` and `method` say nothing about them.

Saving a suggestion: send `stepDetails` back with `POST /recipes`. Each suggested ingredient string must become one `ingredients` entry **in the same position** (how it is split into `quantity` and `item` does not matter). If the review screen lets the user add, remove or reorder ingredients or steps, the client must update `stepDetails` to match (drop the entry of a deleted step, renumber `ingredientIndexes`, use `{"illustration":"GENERIC"}` for a new step) or leave `stepDetails` out. The server only checks lengths and ranges; it cannot tell that an index in range points at the wrong ingredient.

Treat the suggestion as a prefill, not as truth: always let the user edit before saving. Item numbers in warnings are 1-based positions in the returned lists. A note that starts with "The recipe may be partial:" is written by the model and is not checked against the source.

### How `suggested` is made

1. **Gemini** (`generateContent`, temperature 0, JSON response schema, one attempt, 20 s deadline). It gets the video title, the description and the top comments as separately labelled sources, or the pasted text as one source. The same single request asks for the step labels. Thinking is set to the lowest level the model accepts: `thinkingLevel` `MINIMAL` for Gemini 3 Flash and Flash-Lite (which cannot switch it off completely), `thinkingBudget` 0 for Gemini 2.5 Flash; other models keep their default. Comment author names are not sent. It is told to reorganise only what is there, keep the language and the quantities as written, and ignore instructions inside the text.
2. **Grounding check** on the server. Text is compared after lowercasing and removing punctuation and emoji.
   - An item whose words are mostly not in the source (fewer than half, in any 3 neighbouring lines or sentences) is removed.
   - An item with some words missing or spelled differently is kept, with a warning.
   - An item with a number, fraction, number word or unit that is not in the source, or that sits next to something else there, is kept, with a warning.
   - If removed items plus items with a bad quantity are more than half, the whole Gemini answer is discarded.

   The step labels are not part of this check. They are only cleaned: a name over 14 characters is cut to its leading whole words (or emptied if the first word alone is too long), an unknown illustration becomes `GENERIC`, an index that points at no ingredient is dropped, and when ingredients or steps are removed the labels and indexes move with the ones that remain.

   The check compares words, not meaning. It cannot catch a dropped "not", or text that really is in the source but is not part of the recipe.
3. **Rules fallback**, used when Gemini is not configured, fails, times out, is over quota, blocks the request, answers with invalid JSON, or fails the grounding check. An answer with two empty lists is not a failure: it means "no recipe" (`method` `LLM`, `confidence` `NONE`).

The rules splitter looks for an "ingredients" section ("Ingredients", "What you need", "You will need", "Samagri", "सामग्री") and a method section ("Method", "Steps", "Directions", "Instructions", "Preparation", "Vidhi", "विधि") in the description first, then in each comment (or in the pasted text), and keeps the one with the most lines. Bullets, numbering and emoji are removed.

A request can take about 20 s longer than before when Gemini is slow, so clients should use a read timeout of at least 30 s on both endpoints.

### Logging
For each suggestion the server logs the video id (or `pasted`), the method, item counts and the fallback reason. Source text and API keys are not logged.

### Server setup
In `backend/config/local.properties` (gitignored) add these and restart the server:

| property | meaning |
|---|---|
| `tindi.youtube-api-key` | needed for `/source/fetch` |
| `tindi.gemini-api-key` | optional. Without it every suggestion uses the rules splitter |
| `tindi.gemini-model` | optional, default `gemini-3.5-flash-lite` |
| `tindi.gemini-timeout` | optional, default `20s` |

Keys are sent to Google in request headers and are never logged or returned. A value that starts with `PASTE` (the placeholders in `local.properties.example`) counts as not set.

With a Gemini key, the text above is sent to Google. On the free tier Google may use it to improve its products.
