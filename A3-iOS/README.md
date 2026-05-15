# KIT305 Assignment 3 — iOS Interior Design Quoting App

## Marker test setup
- **Recommended simulator:** iPhone 16 (iOS 18) or iPhone 15 Pro
- **Minimum iOS version:** iOS 15.0
- **Orientation:** Portrait (landscape is also supported)
- **Firebase:** Uses the same Firestore database as Assignment 2 — data is shared

## Setup instructions
1. Open a terminal and navigate to the `A3-iOS/` folder.
2. Run `pod install` to install Firebase dependencies.
3. Open `InteriorDesignQuote.xcworkspace` (not `.xcodeproj`) in Xcode.
4. Replace `InteriorDesignQuote/GoogleService-Info.plist` with your real Firebase `GoogleService-Info.plist` file (download from [Firebase Console](https://console.firebase.google.com)).
5. Select an iPhone simulator (iPhone 16 recommended).
6. Press Run (⌘R).

> **Note:** The placeholder `GoogleService-Info.plist` included in the repository contains dummy values. You must replace it with your own before the app will connect to Firestore.

## Application summary
This iOS app is an interior design measurement and quoting tool for professional salespeople. It is a native Swift + UIKit + Storyboard implementation of the Assignment 2 Android app, built for Assignment 3.

It supports:
- Creating, editing, searching, and deleting houses (customer name + address)
- Creating, editing, searching, and deleting rooms within a house
- Adding, editing, and deleting windows and floor spaces inside rooms
- Attaching products from the KIT305 product API (window and floor categories)
- Enforcing product compatibility rules for window products (height/width/multi-panel)
- Selecting photos from the photo library for rooms, windows, and floor spaces
- Generating an itemised quote with include/exclude toggles per item and room
- Applying a custom percentage discount to the quote total
- Sharing the quote as formatted text and a CSV file via the iOS share sheet

## Custom feature
- **Quote discount tool** — A percentage discount can be entered and applied on the quote screen. The discounted total is shown immediately and is included in the exported CSV.

## View Controllers and how they interrelate

### `HousesViewController`
- Entry/root screen embedded in a `UINavigationController`.
- Shows all houses in a `UITableView` (loaded via Firestore real-time listener).
- Search bar filters houses by customer name or address.
- Add button (+) opens an alert to enter customer name and address.
- Tap a row → navigates to `HouseDetailViewController` for that house.
- Long-press a row → action sheet with **Edit**, **Delete**, and **View Quote** options.
- Delete cascades: removes all rooms, windows, and floor spaces for that house.
- View Quote → navigates to `QuoteViewController` for that house.

### `HouseDetailViewController`
- Shows the list of rooms belonging to a selected house.
- Title shows the house customer name.
- Search bar filters rooms by name.
- Add button opens an alert to enter a room name.
- Tap a room → navigates to `RoomDetailViewController`.
- Long-press a row → **Edit Name** or **Delete** (cascades to windows/floor spaces).
- "Quote" bar button → navigates to `QuoteViewController` for the house.

### `RoomDetailViewController`
- Shows details and measurements for a selected room.
- Room name field with a Save button (updates Firestore).
- Room photo: tap the image or "Pick Room Photo" button to choose from the photo library.
- **Windows** section: count label, search bar, Show more/less toggle (shows 2 rows by default), Add Window button.
  - Long-press a window row → **Edit**, **Delete**, **Select Product**, **Pick Photo**, **Remove Photo**.
  - Select Product → navigates to `ProductListViewController` with `category = "window"`.
- **Floor Spaces** section: same layout as windows with `category = "floor"` for product selection.
- Photos are stored as Base64 strings in Firestore.

### `ProductListViewController`
- Shows products for a given category (`window` or `floor`).
- Products are loaded asynchronously from `https://utasbot.dev/kit305_2026/product?category=<category>`.
- Compatible products are sorted first and shown in normal text; incompatible products are greyed out and cannot be selected.
- Compatibility is checked using `CompatibilityChecker` (height range, width/panel count logic).
- Tap a compatible product → if it has variants, shows a variant picker action sheet; otherwise returns immediately.
- Returns the selected product + variant to `RoomDetailViewController` via a callback closure.

### `QuoteViewController`
- Shows the itemised quote for a house.
- Each room is shown with a UISwitch to include/exclude it.
- Each window and floor space is shown with its cost ($) and a UISwitch.
- Totals section: items subtotal, labour ($200/room), discount amount, and final total.
- Discount field: enter a percentage and tap Apply.
- Share button (top-right) → `UIActivityViewController` presenting formatted text and a `quote.csv` file.
- Product prices are fetched from the product API at load time and cached for cost calculation.

## Storyboard structure
```
UINavigationController (initial)
  └── HousesViewController (root, UITableViewController)
        ├── → HouseDetailViewController (show segue: "showHouseDetail")
        │     ├── → RoomDetailViewController (show segue: "showRoomDetail")
        │     │     └── → ProductListViewController (show segue: "showProductList")
        │     └── → QuoteViewController (show segue: "showQuote")
        └── → QuoteViewController (show segue: "showQuoteFromHouses")
```

## References
- KIT305 iOS tutorial base code used as a reference for Storyboard/UIKit patterns.
- KIT305 Assignment Theme and Assignment 3 specification (MyLO).
- KIT305 product API:
  - `https://utasbot.dev/kit305_2026/product`
  - `https://utasbot.dev/kit305_2026/product?category=window`
  - `https://utasbot.dev/kit305_2026/product?category=floor`
- Firebase iOS Firestore documentation:
  - https://firebase.google.com/docs/firestore/quickstart?platform=ios
- Apple Developer documentation:
  - UITableViewController: https://developer.apple.com/documentation/uikit/uitableviewcontroller
  - UISearchController: https://developer.apple.com/documentation/uikit/uisearchcontroller
  - UIImagePickerController: https://developer.apple.com/documentation/uikit/uiimagepickercontroller
  - UIActivityViewController: https://developer.apple.com/documentation/uikit/uiactivityviewcontroller
  - DispatchGroup: https://developer.apple.com/documentation/dispatch/dispatchgroup
- CocoaPods: https://cocoapods.org/

## Generative AI use
- **Tool used:** GitHub Copilot (Coding Agent)
- **How AI was used:**
  - Generating the full iOS project structure, Xcode project file (`project.pbxproj`), and Main Storyboard XML.
  - Implementing all five View Controllers (HousesViewController, HouseDetailViewController, RoomDetailViewController, ProductListViewController, QuoteViewController) with complete Firebase Firestore CRUD operations.
  - Implementing photo library selection (UIImagePickerController) and Base64 encode/decode helper.
  - Implementing product API loading with URLSession and JSONDecoder.
  - Implementing the compatibility checker (height range + multi-panel width logic).
  - Implementing the quote screen with per-item include/exclude switches, cost calculation, discount logic, and share/CSV export.
  - Implementing cascade delete for houses and rooms.
  - Writing this README.
- All generated code was reviewed and refined before being committed.
