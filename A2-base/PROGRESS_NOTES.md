# KIT305 Assignment 2/3/4 — Progress Notes
**Last updated:** 2026-04-14 (session end)  
**Project:** Android app (Kotlin + Firebase Firestore) — Window/Floor covering quote app

---

## Architecture Overview
- **Package:** `au.edu.utas.kit305.tutorial05`
- **Firebase:** Firestore (collections: `houses`, `rooms`, `windows`, `floorspaces`)
- **Data classes:** `House`, `Room`, `Window`, `FloorSpace`
- **Activities:** `MainActivity`, `HouseDetails`, `RoomDetails`, `MovieDetails` (leftover, ignore)

### Firestore Collections
| Collection    | Fields                                         |
|---------------|------------------------------------------------|
| `houses`      | customerName, address                          |
| `rooms`       | houseId, name                                  |
| `windows`     | roomId, name, widthMm, heightMm               |
| `floorspaces` | roomId, name, widthMm, depthMm                |

---

## ✅ COMPLETED

### Feature 1 — House & Room Lists + Edit (10 pts)
- `MainActivity.kt` loads all houses from Firestore, shows in RecyclerView
- Tap house → options dialog (**Edit House** or **Open Rooms**)
- House edit (customer name + address) is handled in `MainActivity.kt`
- `HouseDetails.kt` is now room-focused: loads rooms for the selected house
- `RoomDetails.kt` opens from room tap (edit room name)
- Count labels update after every operation
- Long-press on house → confirm delete dialog
- Long-press on room → confirm delete dialog
- `onResume()` refreshes lists when returning from child screen

### Feature 2 — Add / Delete Houses & Rooms (5 pts)
- Add house button in `MainActivity` creates placeholder in Firestore, prepends to list
- Deleting a house also batch-deletes all its rooms (`MainActivity.deleteHouse`)
- Add room button in `HouseDetails` creates placeholder in Firestore
- Deleting a room removes it from Firestore

### Feature 3 — Floor & Window Space Measurements (15 pts)
- `RoomDetails.kt` shows two separate RecyclerViews: one for Windows, one for Floor Spaces
- Add buttons create a placeholder record immediately, then open an edit dialog
- Edit dialog has name + two dimension fields (widthMm + heightMm for windows; widthMm + depthMm for floor spaces)
- Delete via long-press on any item (confirm dialog)
- Data persists in Firestore under `windows` / `floorspaces` collections
- Generic `MeasurementAdapter<T>` used for both lists

---

## ✅ COMPLETED (continued)

### Feature 4 — Products: Selection (10 pts) ✅ DONE 2026-04-11
### Feature 5 — Products: Constraints (10 pts) ✅ DONE 2026-04-12
- **API URL:** `https://utasbot.dev/kit305_2026/product?category=window|floor`
- Product list is loaded from the assignment API (not from Firestore product documents)
- `ProductListActivity.kt` fetches API data using built-in `HttpURLConnection` + `org.json` parsing
- Supports API payload formats:
  - `{ "data": [ ... ] }`
  - `[ ... ]`
- `Product.kt` uses Firebase-friendly fields and string product IDs (e.g., `win-001`)
- `Window.kt` / `FloorSpace.kt` store selected product id + name, and window panel count
- `measurement_list_item.xml` supports Edit / Delete / Select Product actions
- Product selection returns to `RoomDetails` and saves selected product to Firestore
- `INTERNET` permission is enabled in `AndroidManifest.xml`
- UX fixes completed:
  - Tap window/floor row opens edit dialog
  - Add window/floor creates record only on **Save** (Cancel creates nothing)
  - Room list refreshes after room-name edits on return from `RoomDetails`
- Dependency policy update:
  - Removed non-Firebase third-party libs: Retrofit / OkHttp / Gson converter / Glide
  - Current external stack is Firebase + assignment API only

---

## ✅ COMPLETED (new)

### Feature 6 — Camera / Gallery room photo (10 pts) ✅ DONE 2026-04-14
- `Room.kt` already includes `photoUrl` for Firestore persistence
- `RoomDetails.kt` now supports:
  - Camera capture (`ActivityResultContracts.TakePicture`)
  - Gallery selection (`ActivityResultContracts.GetContent`)
  - Runtime camera permission request
  - Firebase Storage upload for selected image
  - Saving image download URL back to Firestore `rooms/{roomId}.photoUrl`
  - Loading and displaying existing room photo URL on screen open (no Glide)
- `AndroidManifest.xml` now includes a `FileProvider` entry
- New file: `res/xml/file_paths.xml` for camera temp file URI support

---

## ❌ TODO (Remaining Features)

### Feature 7 — Quote Display (10 pts)
**← START HERE NEXT SESSION**
**What to build:**
- A `QuoteActivity` that takes a `houseId`
- Shows an itemized list: House → Rooms → Spaces with selected product, measurement, panel count, cost
- Calculates and shows:
  - Cost per space = product price × panels (or area for floor)
  - Cost per room = sum of space costs
  - Total cost for house = sum of room costs
- HD+: checkboxes to filter out individual rooms; totals update instantly

---

### Feature 8 — Sharing (5 pts)
**What to build:**
- In `QuoteActivity`, add a Share button
- Builds a text (or CSV for HD+) summary of the quote
- Launches `Intent.ACTION_SEND` with `type = "text/plain"`

---

### Feature 9 — Custom Feature (10 pts)
**Ideas (pick one):**
- Map view showing house address (Google Maps)
- Augmented reality preview of a product on a wall/floor
- PDF export of the quote
- Dark/light theme toggle
- Push notifications when a quote is ready

---

### Bonus / Polish
- Search/filter on house list (for HD+)
- App icon (for HD+)
- Consistent Material Design theme across all screens
- Replace `txtYear` label repurposing with proper layout fields

---

## Key Files
| File | Purpose |
|------|---------|
| `MainActivity.kt` | House list, add/delete house, edit house details |
| `HouseDetails.kt` | Room list for selected house, add/delete room |
| `RoomDetails.kt` | Room edit, window list, floor space list, add/edit/delete |
| `House.kt` | Data class |
| `Room.kt` | Data class |
| `Window.kt` | Data class (widthMm, heightMm) |
| `FloorSpace.kt` | Data class (widthMm, depthMm) |
| `res/layout/activity_room_details.xml` | Layout for RoomDetails |
| `res/values/strings.xml` | String resources |

---

## How to Resume
When you return, say **"continue from progress notes"** and I will:
1. Read this file
2. Start **Feature 7 — Quote Display**
3. Implement steps:
   - Add `QuoteActivity` and house-level quote screen
   - Compute room totals + house total
   - Include selected product names and measurements in itemized output
   - Add exclude toggles (HD+ behavior)
4. Continue with simple incremental commits and push after each part

**Current stack policy state: Firebase + assignment API only (no Retrofit/OkHttp/Gson/Glide).**
