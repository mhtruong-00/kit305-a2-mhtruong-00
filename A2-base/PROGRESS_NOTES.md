# KIT305 Assignment 2/3/4 — Progress Notes
**Last updated:** 2026-04-11 (end of day)  
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
- **API URL:** `https://utasbot.dev/kit305_2026/product?category=window|floor`
- `Product.kt` data class with `@SerializedName` field mappings
- `ProductApiService.kt` Retrofit interface (`GET /product`, `GET /product?category=`, `GET /product/:id`)
- `RetrofitClient.kt` singleton (OkHttp + Gson + logging interceptor)
- `Window.kt` updated → added `selectedProductId`, `selectedProductName`, `panelCount`
- `FloorSpace.kt` updated → added `selectedProductId`, `selectedProductName`
- `product_list_item.xml` — image (Glide), name, description, price, constraint info
- `activity_product_list.xml` — title, space info banner, loading spinner, product list
- `measurement_list_item.xml` — name, dims, current product label, Edit / Delete / **Select Product** buttons
- `ProductListActivity.kt` — fetches API by category, shows list, returns selected product via `ActivityResultLauncher`
- `RoomDetails.kt` rewritten — two `ActivityResultLauncher`s (window + floor), saves product ID/name/panelCount to Firestore
- `INTERNET` permission added to `AndroidManifest.xml`
- New dependencies: Retrofit 2.9, OkHttp 4.12, Gson converter, Glide 4.16, Firebase Storage
- API parsing fixed to match real response format: `{ "data": [...] }`
- Product IDs handled as strings (e.g., `win-001`) across `Product`, `Window`, `FloorSpace`, and selection result flow
- Product load error `Expected BEGIN_...` resolved
- UX fixes completed:
  - Tap window/floor row now opens edit dialog
  - Add window/floor now creates records only on **Save** (Cancel does not create)
  - Room list refreshes after room-name edits when returning from `RoomDetails`

---

## ❌ TODO (Remaining Features)

### Feature 5 — Products: Constraints (10 pts)  **← START HERE NEXT SESSION**
**Constraints to implement for WINDOW spaces only:**
- `minHeight` / `maxHeight` — window height must be within range
- `minWidth` / `maxWidth` — window width must be within range
- **Panel Splitting:** if the window is wider than `maxWidth`, it can be split into panels (each panel width = window width ÷ panels), and each panel must still satisfy `minWidth` / `maxWidth`
- `maxPanelCount` — number of panels cannot exceed this
- Show **clear error messages** explaining WHY a product is incompatible

---

### Feature 6 — Camera (10 pts)
**What to build:**
- Add an image field to `Room` (or `Window`/`FloorSpace`) — store as a URL in Firestore (upload to Firebase Storage)
- On `RoomDetails` (and optionally space detail screen), show an `ImageView`
- Button to pick image from **Gallery** OR **Camera** (both for HD+)
- After picking, upload to **Firebase Storage** → get download URL → save to Firestore
- Display the image whenever the screen is shown

**Implementation steps:**
1. Add Firebase Storage dependency to `app/build.gradle.kts`
2. Request `CAMERA` permission at runtime
3. Use `ActivityResultContracts.TakePicture` and `ActivityResultContracts.GetContent`
4. Upload bitmap/uri to Storage, save download URL string

---

### Feature 7 — Quote Display (10 pts)
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
2. Start **Feature 5 — Products: Constraints**
3. Implement: min/max width/height checks, panel split logic, max panel count, and clear incompatibility messages
4. Continue with simple incremental commits and push after each part

**Commit count target: ~150 (current progress is on track)**
