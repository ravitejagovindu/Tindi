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
| 400 | `VALIDATION_FAILED` | bad body or field. `fields` maps field path to a short message, e.g. `name`, `ingredients[0].item`, `steps[2]`, `categoryIds` |
| 401 | `INVALID_CREDENTIALS` | login failed (does not say whether email or password) |
| 401 | `UNAUTHORIZED` | missing, unknown or revoked token. Clear the stored token and show login |
| 403 | `STARTER_READ_ONLY` | rename or delete of a starter category |
| 404 | `NOT_FOUND` | missing, or belongs to someone else |
| 409 | `EMAIL_TAKEN` | `fields.email` |
| 409 | `CATEGORY_NAME_TAKEN` | `fields.name` |
| 405 / 500 | `METHOD_NOT_ALLOWED` / `INTERNAL_ERROR` | unexpected |

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
| sourceUrl | optional, max 2048; blank becomes `null` |
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
 "categoryIds":["00000000-0000-4000-8000-000000000004","e734db4c-c242-441f-811e-140ee5fb4064"],
 "sourceType":"PASTED","sourceUrl":null,
 "createdAt":"2026-11-25T15:36:20.137879Z","updatedAt":"2026-11-25T15:36:20.137879Z"}
```
`sourceType` is `YOUTUBE` or `PASTED`. `categoryIds` has no guaranteed order; match by id.

### GET /recipes -> 200
Array of every recipe of the user with all fields, newest first. No paging, no server search.

### GET /recipes/{id} -> 200
One recipe object, or 404.

### POST /recipes -> 201
```json
{"name":"Halwa","ingredients":[{"quantity":"250 g","item":"Paneer"}],"steps":["Blend tomato and onion"],
 "categoryIds":["00000000-0000-4000-8000-000000000004"],"sourceType":"PASTED","sourceUrl":null}
```
Required: `name`, `ingredients`, `steps`, `sourceType`. `categoryIds` may be omitted or `[]`.
`categoryIds` may hold starter ids and the user's own ids only; anything else is `400 VALIDATION_FAILED` with `fields.categoryIds`.
Response: the recipe object.

### PUT /recipes/{id} -> 200
Full replace, same body as POST. `createdAt` is kept, `updatedAt` changes. Errors: 400, 404.

### DELETE /recipes/{id} -> 204
Errors: 404.
