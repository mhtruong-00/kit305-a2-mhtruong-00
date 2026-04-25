# KIT305 Assignment 2/3/4 — Progress Notes
**Last updated:** 2026-04-25 (Room photo display + Share feature complete)  
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
| `rooms`       | houseId, name, photoBase64, photoUrl          |
| `windows`     | roomId, name, widthMm, heightMm, photoBase64  |
| `floorspaces` | roomId, name, widthMm, depthMm, photoBase64   |

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

### Feature 6 — Camera / Gallery photos (room + window + floor) (10 pts) ✅ DONE 2026-04-14
- `RoomDetails.kt` now supports `PhotoTarget.ROOM`, `PhotoTarget.WINDOW`, and `PhotoTarget.FLOOR_SPACE`
- Photo actions implemented for room and measurement items:
  - `Take Photo` (`ActivityResultContracts.TakePicture`)
  - `From Gallery` (`ActivityResultContracts.GetContent`)
  - `Remove Photo` (clears stored value and updates UI)
- Runtime camera permission flow is implemented
- Persistence uses **Firestore base64** fields (no Firebase Storage required):
  - room photo saved to `rooms/{roomId}.photoBase64` (with `photoUrl` kept empty)
  - window photo saved to `windows/{windowId}.photoBase64`
  - floor photo saved to `floorspaces/{floorId}.photoBase64`
- Compression/downscale is applied before encoding to stay under Firestore document limits
- UI polish completed:
  - Room photo buttons are consistent (`Take Photo`, `From Gallery`, `Remove Photo`)
  - Measurement item photo buttons are aligned in one consistent row
- `AndroidManifest.xml` includes a `FileProvider` entry
- New file: `res/xml/file_paths.xml` for camera temp file URI support

### Feature 7 — Quote Display (10 pts) ✅ DONE 2026-04-19
- `QuoteActivity.kt` added and opened from `HouseDetails.kt`
- Quote screen loads house, rooms, windows, and floor spaces from Firestore
- Product pricing is fetched from the assignment API endpoint: `https://utasbot.dev/kit305_2026/product`
- Quote calculations now show:
  - item area
  - product rate per square metre
  - item cost
  - room subtotal
  - room labour ($200)
  - room total
  - final house total
- Fallback rates are used if API pricing cannot be loaded:
  - windows = $50 / m²
  - floor spaces = $100 / m²
- HD-level filtering behavior is implemented on the quote page:
  - include/exclude room checkbox
  - include/exclude window checkbox
  - include/exclude floor space checkbox
  - totals update instantly when toggles change

### UI Polish — Room Photo Display ✅ DONE 2026-04-25
- Room photo display improved with rounded card styling
- Added "Room Photo" section label above the image for clarity
- New drawable: `rounded_card_background.xml` (light gray with rounded corners and border)
- Image view height increased to 220dp for better visibility
- Photo buttons (Take Photo, From Gallery, Remove Photo) positioned directly below the image
- Image displays with `centerCrop` scale type for optimal framing

---

## ❌ TODO (Remaining Features)

### Feature 8 — Sharing (5 pts) ✅ DONE
- Share button functional in `QuoteActivity`
- Quote text built with customer name, address, itemized breakdown
- Discount amount included in shared text
- Launches Android share sheet via `Intent.ACTION_SEND`
- Users can send quote via email, messaging, etc.

### Feature 9 — Custom Feature (10 pts)
**Status:** Not yet started
**Options to consider:**
- Map view showing house address (Google Maps) - requires API key
- PDF export of the quote - would provide professional output
- Dark/light theme toggle - UI enhancement
- Search/filter on house list - improves navigation
- House copy functionality - allow duplication with pre-filled rooms

---

### Bonus / Polish
- Search/filter on house list (for HD+)
- App icon (for HD+) — Currently in progress with monochrome launcher icon
- Consistent Material Design theme across all screens
- Replace `txtYear` label repurposing with proper layout fields

---

## Key Files
| File | Purpose |
|------|---------|
| `MainActivity.kt` | House list, add/delete house, edit house details |
| `HouseDetails.kt` | Room list for selected house, add/delete room |
| `RoomDetails.kt` | Room edit, window list, floor space list, add/edit/delete |
| `QuoteActivity.kt` | House quote screen, totals, include/exclude toggles |
| `House.kt` | Data class |
| `Room.kt` | Data class |
| `Window.kt` | Data class (widthMm, heightMm) |
| `FloorSpace.kt` | Data class (widthMm, depthMm) |
| `res/layout/activity_room_details.xml` | Layout for RoomDetails |
| `res/layout/activity_quote.xml` | Layout for QuoteActivity |
| `res/layout/measurement_list_item.xml` | Measurement row (edit/delete/product + photo actions) |
| `res/values/strings.xml` | String resources |

---

## How to Resume
When you return, say **"continue from progress notes"** and I will:
1. Read this file
2. Start **Feature 8 — Sharing**
3. Implement steps:
   - Add a Share button to `QuoteActivity`
   - Build quote summary text from the current included rooms/items
   - Launch Android share sheet with plain text quote output
4. Continue with simple incremental commits and push after each part

**Current stack policy state: Firebase + assignment API only (no Retrofit/OkHttp/Gson/Glide).**
