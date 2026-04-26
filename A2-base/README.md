# KIT305 Assignment 2 — Interior Design Quoting App

## Marker test setup
- **Recommended device/emulator:** Medium Phone emulator
- **Android version target:** Android 14 / API 34
- **Orientation:** Portrait

## Repository link
- GitHub repository: https://github.com/mhtruong-00/kit305-a2-mhtruong-00

## Application summary
This application is an Android interior design measurement and quoting app for professional salespeople.

This assignment was developed using the KIT305 Week 5 Android tutorial base code as a starting point, then extended for the Assignment 2 requirements.

It supports:
- creating, editing, searching, and deleting houses
- creating, editing, searching, and deleting rooms
- adding, editing, and deleting windows and floor spaces inside rooms
- attaching products from the provided KIT305 product API
- enforcing product compatibility rules for window products
- taking photos or selecting photos from gallery for rooms, windows, and floor spaces
- generating an itemised quote with include/exclude controls
- sharing the quote as CSV text

## Custom feature
- **Custom feature used:** Quote discount tool
- A percentage discount can be applied to the final quote total from the quote screen.
- The discount updates the final total immediately and is also included in the shared CSV output.

## Activities and how they interrelate
- **`MainActivity`**
  - Entry screen.
  - Shows the list of houses.
  - Lets the user add, edit, delete, search, open rooms, or open the quote for a house.

- **`HouseDetails`**
  - Opened from `MainActivity` for a selected house.
  - Shows the list of rooms in that house.
  - Lets the user add, edit/open, delete, search rooms, or open the quote screen for that house.

- **`RoomDetails`**
  - Opened from `HouseDetails` for a selected room.
  - Lets the user edit the room name.
  - Shows windows and floor spaces for the room.
  - Supports product selection, dimensions, photos, search, and show more/show less behaviour.

- **`ProductListActivity`**
  - Opened from `RoomDetails` when selecting a product for a window or floor space.
  - Loads products from the provided API.
  - Returns the selected product and variant back to `RoomDetails`.

- **`QuoteActivity`**
  - Opened from `MainActivity` or `HouseDetails`.
  - Shows the itemised quote for a house.
  - Supports include/exclude toggles, totals, discount application, and quote sharing.

- **`MovieDetails`**
  - Leftover tutorial activity from the base code.
  - Not part of the main Assignment 2 app flow.

## References
- KIT305 Week 5 Android tutorial base code used as the starting point for this assignment.
- KIT305 Assignment Theme and Assignment 2 specification provided on MyLO.
- KIT305 product API:
  - https://utasbot.dev/kit305_2026/product
  - https://utasbot.dev/kit305_2026/product?category=window
  - https://utasbot.dev/kit305_2026/product?category=floor
- Firebase Firestore Android documentation:
  - https://firebase.google.com/docs/firestore
- Android Developers documentation:
  - Activity Result APIs: https://developer.android.com/training/basics/intents/result
  - FileProvider: https://developer.android.com/reference/androidx/core/content/FileProvider
  - Sharesheet / send intent: https://developer.android.com/training/sharing/send
- Glide documentation (used for product image loading):
  - https://github.com/bumptech/glide

## Generative AI use
- **Tool used:** GitHub Copilot / Copilot Chat
- **How AI was used:**
  - debugging RecyclerView show more/show less behaviour
  - improving Firestore CRUD flows for houses, rooms, windows, and floor spaces
  - helping with camera/gallery image handling and base64 persistence
  - helping implement quote calculations, CSV sharing, and discount behaviour
  - helping polish input validation and UI text/resources
- All generated suggestions were reviewed, tested, and edited before being kept in the project.
