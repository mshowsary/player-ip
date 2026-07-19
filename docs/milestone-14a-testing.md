# Milestone 14A — parental control test plan

IBO-parity milestone: a 4-digit parental PIN plus lockable categories across
Live, Movies and Series. Locked categories keep their name visible (with a
padlock badge) but everything inside them disappears from browsing, search,
channel zapping and the Home rails until the PIN is entered. Unlocking lasts
for the app session; closing the app (or "Lock now" in Settings) locks again.

## What changed

- **Locking a category**: hold OK (long-press; touch long-press on phones) on
  any provider category in Live, Movies or Series — in the rail, the chip
  strip or the category picker dialog. The very first lock asks you to create
  the PIN in place; later changes ask for the PIN unless the session is
  already unlocked. Locked categories show a small padlock.
- **Opening a locked category** asks for the PIN. A correct PIN unlocks
  parental locks for the whole session.
- **Hidden everywhere while locked**: content of locked categories is excluded
  from the "All" lists, FTS search, channel up/down and digit zapping, the
  in-player channel panel, and the Home Recent/Bookmarks rails — enforced in
  the database queries, not the UI.
- **Settings → Parental controls**: PIN status, locked-category count,
  Set/Change PIN (change verifies the current PIN first) and "Lock now".
- PIN is stored as a salted hash (never plaintext); verification fails closed.
  Locks key on the provider's category id, so they survive re-syncs. No Room
  schema change — locks live in DataStore.
- New pure policy `ParentalPinPolicy` with JUnit tests.

## Test tour (TV box and phone)

1. **Create the PIN by locking**: Live → open the category picker → long-press
   a category (pick one with recognizable channels) → "Set parental PIN"
   appears → enter a 4-digit PIN → the category now wears a padlock.
2. **Hidden while locked**: force-stop and reopen the app (the session lock
   returns on every fresh start). Check, without entering the PIN:
   - "All" no longer contains that category's channels;
   - search cannot find them by name;
   - channel up/down and typing a channel number inside the player skip them;
   - Home Recent/Bookmarks rails do not show them.
3. **Open with PIN**: select the locked category → PIN prompt → wrong PIN is
   refused and clears the field; correct PIN opens it and the hidden content
   returns everywhere (session unlocked).
4. **Relock**: Settings → Parental controls → "Lock now" → the category's
   content vanishes again immediately.
5. **Unlock the lock itself**: long-press the locked category again (PIN asked
   if locked) → padlock disappears, content is back for good.
6. **Movies/Series**: repeat steps 1–3 in the Movies grid (chips or rail) to
   confirm the same behavior outside Live.
7. **Change PIN**: Settings → Parental controls → Change PIN → wrong current
   PIN refused; correct one lets you save a new PIN, which then gates locks.
8. **Sync survival**: run "Refresh now" in Settings, then confirm the locked
   category is still locked afterwards (locks key on provider ids, not local
   rows).

Install over the existing data as usual; no fresh install required.
