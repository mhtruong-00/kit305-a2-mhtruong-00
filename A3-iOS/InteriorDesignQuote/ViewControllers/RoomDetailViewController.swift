import UIKit
import FirebaseFirestore

// MARK: - Product Selection Delegate

protocol ProductSelectionDelegate: AnyObject {
    func didSelectProduct(_ product: Product, variant: String?, for itemType: ItemType, itemId: String)
}

enum ItemType {
    case window
    case floor
}

class RoomDetailViewController: UIViewController {

    var room: Room!
    weak var productDelegate: ProductSelectionDelegate?

    private let db = Firestore.firestore()
    private var windows: [WindowItem] = []
    private var floorSpaces: [FloorSpace] = []
    private var windowsFiltered: [WindowItem] = []
    private var floorsFiltered: [FloorSpace] = []
    private var windowListener: ListenerRegistration?
    private var floorListener: ListenerRegistration?

    private var showAllWindows = false
    private var showAllFloors = false
    private var windowSearchText = ""
    private var floorSearchText = ""

    private var pendingProductItemId: String?
    private var pendingProductItemType: ItemType?

    // MARK: - UI Elements

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    // Room header
    private let roomNameField = UITextField()
    private let saveNameButton = UIButton(type: .system)
    private let roomPhotoImageView = UIImageView()
    private let pickPhotoButton = UIButton(type: .system)

    // Windows section
    private let windowHeaderStack = UIStackView()
    private let windowSearchBar = UISearchBar()
    private let windowCountLabel = UILabel()
    private let windowToggleButton = UIButton(type: .system)
    private let addWindowButton = UIButton(type: .system)
    private let windowTableView = UITableView()
    private var windowTableHeightConstraint: NSLayoutConstraint!

    // Floors section
    private let floorHeaderStack = UIStackView()
    private let floorSearchBar = UISearchBar()
    private let floorCountLabel = UILabel()
    private let floorToggleButton = UIButton(type: .system)
    private let addFloorButton = UIButton(type: .system)
    private let floorTableView = UITableView()
    private var floorTableHeightConstraint: NSLayoutConstraint!

    private let rowHeight: CGFloat = 60
    private let maxVisibleRows = 2

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = room.name
        setupUI()
        loadData()
    }

    deinit {
        windowListener?.remove()
        floorListener?.remove()
    }

    // MARK: - UI Setup

    private func setupUI() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        contentStack.axis = .vertical
        contentStack.spacing = 16
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)
        NSLayoutConstraint.activate([
            contentStack.topAnchor.constraint(equalTo: scrollView.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor, constant: -16),
            contentStack.widthAnchor.constraint(equalTo: scrollView.widthAnchor, constant: -32)
        ])

        setupRoomHeader()
        setupWindowSection()
        setupFloorSection()
    }

    private func setupRoomHeader() {
        let nameRow = UIStackView()
        nameRow.axis = .horizontal
        nameRow.spacing = 8

        roomNameField.borderStyle = .roundedRect
        roomNameField.text = room.name
        roomNameField.placeholder = "Room name"
        nameRow.addArrangedSubview(roomNameField)

        saveNameButton.setTitle("Save", for: .normal)
        saveNameButton.addTarget(self, action: #selector(saveRoomName), for: .touchUpInside)
        saveNameButton.widthAnchor.constraint(equalToConstant: 60).isActive = true
        nameRow.addArrangedSubview(saveNameButton)

        contentStack.addArrangedSubview(nameRow)

        roomPhotoImageView.contentMode = .scaleAspectFit
        roomPhotoImageView.backgroundColor = .secondarySystemBackground
        roomPhotoImageView.layer.cornerRadius = 8
        roomPhotoImageView.clipsToBounds = true
        roomPhotoImageView.heightAnchor.constraint(equalToConstant: 200).isActive = true
        roomPhotoImageView.isUserInteractionEnabled = true
        let tap = UITapGestureRecognizer(target: self, action: #selector(pickRoomPhoto))
        roomPhotoImageView.addGestureRecognizer(tap)

        if let base64 = room.photoBase64, let img = PhotoHelper.base64ToImage(base64) {
            roomPhotoImageView.image = img
        } else {
            roomPhotoImageView.image = UIImage(systemName: "photo")
            roomPhotoImageView.tintColor = .tertiaryLabel
        }
        contentStack.addArrangedSubview(roomPhotoImageView)

        pickPhotoButton.setTitle("Pick Room Photo", for: .normal)
        pickPhotoButton.addTarget(self, action: #selector(pickRoomPhoto), for: .touchUpInside)
        contentStack.addArrangedSubview(pickPhotoButton)

        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        contentStack.addArrangedSubview(separator)
    }

    private func setupWindowSection() {
        let titleLabel = UILabel()
        titleLabel.text = "Windows"
        titleLabel.font = .boldSystemFont(ofSize: 18)
        contentStack.addArrangedSubview(titleLabel)

        let windowControls = UIStackView()
        windowControls.axis = .horizontal
        windowControls.spacing = 8

        windowCountLabel.text = "0 windows"
        windowCountLabel.font = .systemFont(ofSize: 14)
        windowCountLabel.textColor = .secondaryLabel
        windowControls.addArrangedSubview(windowCountLabel)

        let spacer1 = UIView()
        spacer1.setContentHuggingPriority(.defaultLow, for: .horizontal)
        windowControls.addArrangedSubview(spacer1)

        windowToggleButton.setTitle("Show more", for: .normal)
        windowToggleButton.titleLabel?.font = .systemFont(ofSize: 13)
        windowToggleButton.addTarget(self, action: #selector(toggleWindows), for: .touchUpInside)
        windowControls.addArrangedSubview(windowToggleButton)

        addWindowButton.setTitle("+ Add Window", for: .normal)
        addWindowButton.addTarget(self, action: #selector(addWindowTapped), for: .touchUpInside)
        windowControls.addArrangedSubview(addWindowButton)

        contentStack.addArrangedSubview(windowControls)

        windowSearchBar.placeholder = "Search windows"
        windowSearchBar.delegate = self
        windowSearchBar.searchBarStyle = .minimal
        contentStack.addArrangedSubview(windowSearchBar)

        windowTableView.delegate = self
        windowTableView.dataSource = self
        windowTableView.register(UITableViewCell.self, forCellReuseIdentifier: "WindowCell")
        windowTableView.isScrollEnabled = false
        windowTableView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.addArrangedSubview(windowTableView)

        windowTableHeightConstraint = windowTableView.heightAnchor.constraint(equalToConstant: rowHeight * CGFloat(maxVisibleRows))
        windowTableHeightConstraint.isActive = true

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(windowLongPress(_:)))
        windowTableView.addGestureRecognizer(longPress)

        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        contentStack.addArrangedSubview(separator)
    }

    private func setupFloorSection() {
        let titleLabel = UILabel()
        titleLabel.text = "Floor Spaces"
        titleLabel.font = .boldSystemFont(ofSize: 18)
        contentStack.addArrangedSubview(titleLabel)

        let floorControls = UIStackView()
        floorControls.axis = .horizontal
        floorControls.spacing = 8

        floorCountLabel.text = "0 floor spaces"
        floorCountLabel.font = .systemFont(ofSize: 14)
        floorCountLabel.textColor = .secondaryLabel
        floorControls.addArrangedSubview(floorCountLabel)

        let spacer2 = UIView()
        spacer2.setContentHuggingPriority(.defaultLow, for: .horizontal)
        floorControls.addArrangedSubview(spacer2)

        floorToggleButton.setTitle("Show more", for: .normal)
        floorToggleButton.titleLabel?.font = .systemFont(ofSize: 13)
        floorToggleButton.addTarget(self, action: #selector(toggleFloors), for: .touchUpInside)
        floorControls.addArrangedSubview(floorToggleButton)

        addFloorButton.setTitle("+ Add Floor Space", for: .normal)
        addFloorButton.addTarget(self, action: #selector(addFloorTapped), for: .touchUpInside)
        floorControls.addArrangedSubview(addFloorButton)

        contentStack.addArrangedSubview(floorControls)

        floorSearchBar.placeholder = "Search floor spaces"
        floorSearchBar.delegate = self
        floorSearchBar.searchBarStyle = .minimal
        contentStack.addArrangedSubview(floorSearchBar)

        floorTableView.delegate = self
        floorTableView.dataSource = self
        floorTableView.register(UITableViewCell.self, forCellReuseIdentifier: "FloorCell")
        floorTableView.isScrollEnabled = false
        floorTableView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.addArrangedSubview(floorTableView)

        floorTableHeightConstraint = floorTableView.heightAnchor.constraint(equalToConstant: rowHeight * CGFloat(maxVisibleRows))
        floorTableHeightConstraint.isActive = true

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(floorLongPress(_:)))
        floorTableView.addGestureRecognizer(longPress)
    }

    // MARK: - Data Loading

    private func loadData() {
        windowListener = db.collection("windows")
            .whereField("roomId", isEqualTo: room.id)
            .addSnapshotListener { [weak self] snap, error in
                guard let self = self else { return }
                if let error = error { self.showAlert(title: "Error", message: error.localizedDescription); return }
                self.windows = snap?.documents.compactMap { WindowItem(id: $0.documentID, data: $0.data()) } ?? []
                self.windows.sort { $0.name < $1.name }
                self.applyWindowFilter()
            }

        floorListener = db.collection("floorspaces")
            .whereField("roomId", isEqualTo: room.id)
            .addSnapshotListener { [weak self] snap, error in
                guard let self = self else { return }
                if let error = error { self.showAlert(title: "Error", message: error.localizedDescription); return }
                self.floorSpaces = snap?.documents.compactMap { FloorSpace(id: $0.documentID, data: $0.data()) } ?? []
                self.floorSpaces.sort { $0.name < $1.name }
                self.applyFloorFilter()
            }
    }

    private func applyWindowFilter() {
        if windowSearchText.isEmpty {
            windowsFiltered = windows
        } else {
            windowsFiltered = windows.filter { $0.name.lowercased().contains(windowSearchText.lowercased()) }
        }
        updateWindowTable()
    }

    private func applyFloorFilter() {
        if floorSearchText.isEmpty {
            floorsFiltered = floorSpaces
        } else {
            floorsFiltered = floorSpaces.filter { $0.name.lowercased().contains(floorSearchText.lowercased()) }
        }
        updateFloorTable()
    }

    private func updateWindowTable() {
        let count = windowsFiltered.count
        windowCountLabel.text = "\(count) window\(count == 1 ? "" : "s")"
        let visibleRows = showAllWindows ? count : min(count, maxVisibleRows)
        windowTableHeightConstraint.constant = rowHeight * CGFloat(max(visibleRows, 0))
        windowToggleButton.isHidden = count <= maxVisibleRows
        windowToggleButton.setTitle(showAllWindows ? "Show less" : "Show more", for: .normal)
        windowTableView.reloadData()
        view.layoutIfNeeded()
    }

    private func updateFloorTable() {
        let count = floorsFiltered.count
        floorCountLabel.text = "\(count) floor space\(count == 1 ? "" : "s")"
        let visibleRows = showAllFloors ? count : min(count, maxVisibleRows)
        floorTableHeightConstraint.constant = rowHeight * CGFloat(max(visibleRows, 0))
        floorToggleButton.isHidden = count <= maxVisibleRows
        floorToggleButton.setTitle(showAllFloors ? "Show less" : "Show more", for: .normal)
        floorTableView.reloadData()
        view.layoutIfNeeded()
    }

    // MARK: - Actions

    @objc private func saveRoomName() {
        guard let name = roomNameField.text?.trimmingCharacters(in: .whitespaces), !name.isEmpty else { return }
        db.collection("rooms").document(room.id).updateData(["name": name]) { [weak self] error in
            if let error = error {
                self?.showAlert(title: "Error", message: error.localizedDescription)
            } else {
                self?.room.name = name
                self?.title = name
            }
        }
    }

    @objc private func pickRoomPhoto() {
        currentPhotoTarget = .room
        let picker = UIImagePickerController()
        picker.sourceType = .photoLibrary
        picker.delegate = self
        present(picker, animated: true)
    }

    @objc private func toggleWindows() {
        showAllWindows.toggle()
        updateWindowTable()
    }

    @objc private func toggleFloors() {
        showAllFloors.toggle()
        updateFloorTable()
    }

    @objc private func addWindowTapped() {
        showAddWindowAlert(window: nil)
    }

    @objc private func addFloorTapped() {
        showAddFloorAlert(floor: nil)
    }

    // MARK: - Add/Edit Alerts

    private func showAddWindowAlert(window: WindowItem?) {
        let isEdit = window != nil
        let alert = UIAlertController(title: isEdit ? "Edit Window" : "Add Window", message: nil, preferredStyle: .alert)
        alert.addTextField { tf in tf.placeholder = "Window Name"; tf.text = window?.name }
        alert.addTextField { tf in
            tf.placeholder = "Width (mm)"
            tf.keyboardType = .decimalPad
            if let w = window?.widthMm { tf.text = String(format: "%.0f", w) }
        }
        alert.addTextField { tf in
            tf.placeholder = "Height (mm)"
            tf.keyboardType = .decimalPad
            if let h = window?.heightMm { tf.text = String(format: "%.0f", h) }
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: isEdit ? "Save" : "Add", style: .default) { [weak self, weak alert] _ in
            guard let self = self,
                  let name = alert?.textFields?[0].text?.trimmingCharacters(in: .whitespaces), !name.isEmpty
            else { return }
            let width = Double(alert?.textFields?[1].text ?? "")
            let height = Double(alert?.textFields?[2].text ?? "")
            if let existing = window {
                var updated = existing
                updated.name = name
                updated.widthMm = width
                updated.heightMm = height
                self.db.collection("windows").document(existing.id).setData(updated.toFirestore()) { error in
                    if let error = error { self.showAlert(title: "Error", message: error.localizedDescription) }
                }
            } else {
                let item = WindowItem(name: name, roomId: self.room.id, widthMm: width, heightMm: height)
                self.db.collection("windows").addDocument(data: item.toFirestore()) { error in
                    if let error = error { self.showAlert(title: "Error", message: error.localizedDescription) }
                }
            }
        })
        present(alert, animated: true)
    }

    private func showAddFloorAlert(floor: FloorSpace?) {
        let isEdit = floor != nil
        let alert = UIAlertController(title: isEdit ? "Edit Floor Space" : "Add Floor Space", message: nil, preferredStyle: .alert)
        alert.addTextField { tf in tf.placeholder = "Floor Space Name"; tf.text = floor?.name }
        alert.addTextField { tf in
            tf.placeholder = "Width (mm)"
            tf.keyboardType = .decimalPad
            if let w = floor?.widthMm { tf.text = String(format: "%.0f", w) }
        }
        alert.addTextField { tf in
            tf.placeholder = "Depth (mm)"
            tf.keyboardType = .decimalPad
            if let d = floor?.depthMm { tf.text = String(format: "%.0f", d) }
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: isEdit ? "Save" : "Add", style: .default) { [weak self, weak alert] _ in
            guard let self = self,
                  let name = alert?.textFields?[0].text?.trimmingCharacters(in: .whitespaces), !name.isEmpty
            else { return }
            let width = Double(alert?.textFields?[1].text ?? "")
            let depth = Double(alert?.textFields?[2].text ?? "")
            if let existing = floor {
                var updated = existing
                updated.name = name
                updated.widthMm = width
                updated.depthMm = depth
                self.db.collection("floorspaces").document(existing.id).setData(updated.toFirestore()) { error in
                    if let error = error { self.showAlert(title: "Error", message: error.localizedDescription) }
                }
            } else {
                let item = FloorSpace(name: name, roomId: self.room.id, widthMm: width, depthMm: depth)
                self.db.collection("floorspaces").addDocument(data: item.toFirestore()) { error in
                    if let error = error { self.showAlert(title: "Error", message: error.localizedDescription) }
                }
            }
        })
        present(alert, animated: true)
    }

    // MARK: - Long Press Handlers

    @objc private func windowLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        let point = gesture.location(in: windowTableView)
        guard let indexPath = windowTableView.indexPathForRow(at: point) else { return }
        let visibleCount = showAllWindows ? windowsFiltered.count : min(windowsFiltered.count, maxVisibleRows)
        guard indexPath.row < visibleCount else { return }
        let window = windowsFiltered[indexPath.row]
        showWindowActions(window: window, indexPath: indexPath)
    }

    @objc private func floorLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        let point = gesture.location(in: floorTableView)
        guard let indexPath = floorTableView.indexPathForRow(at: point) else { return }
        let visibleCount = showAllFloors ? floorsFiltered.count : min(floorsFiltered.count, maxVisibleRows)
        guard indexPath.row < visibleCount else { return }
        let floor = floorsFiltered[indexPath.row]
        showFloorActions(floor: floor, indexPath: indexPath)
    }

    private func showWindowActions(window: WindowItem, indexPath: IndexPath) {
        let sheet = UIAlertController(title: window.name, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Edit", style: .default) { [weak self] _ in
            self?.showAddWindowAlert(window: window)
        })
        sheet.addAction(UIAlertAction(title: "Select Product", style: .default) { [weak self] _ in
            self?.pendingProductItemId = window.id
            self?.pendingProductItemType = .window
            self?.performSegue(withIdentifier: "showProductList", sender: [
                "category": "window",
                "width": window.widthMm as Any,
                "height": window.heightMm as Any,
                "itemId": window.id
            ])
        })
        sheet.addAction(UIAlertAction(title: "Pick Photo", style: .default) { [weak self] _ in
            self?.pickPhoto(for: .window, itemId: window.id)
        })
        if window.photoBase64 != nil {
            sheet.addAction(UIAlertAction(title: "Remove Photo", style: .default) { [weak self] _ in
                self?.db.collection("windows").document(window.id).updateData(["photoBase64": FieldValue.delete()]) { _ in }
            })
        }
        sheet.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.confirmDeleteWindow(window)
        })
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        if let popover = sheet.popoverPresentationController,
           let cell = windowTableView.cellForRow(at: indexPath) {
            popover.sourceView = cell
            popover.sourceRect = cell.bounds
        }
        present(sheet, animated: true)
    }

    private func showFloorActions(floor: FloorSpace, indexPath: IndexPath) {
        let sheet = UIAlertController(title: floor.name, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Edit", style: .default) { [weak self] _ in
            self?.showAddFloorAlert(floor: floor)
        })
        sheet.addAction(UIAlertAction(title: "Select Product", style: .default) { [weak self] _ in
            self?.pendingProductItemId = floor.id
            self?.pendingProductItemType = .floor
            self?.performSegue(withIdentifier: "showProductList", sender: [
                "category": "floor",
                "width": floor.widthMm as Any,
                "depth": floor.depthMm as Any,
                "itemId": floor.id
            ])
        })
        sheet.addAction(UIAlertAction(title: "Pick Photo", style: .default) { [weak self] _ in
            self?.pickPhoto(for: .floor, itemId: floor.id)
        })
        if floor.photoBase64 != nil {
            sheet.addAction(UIAlertAction(title: "Remove Photo", style: .default) { [weak self] _ in
                self?.db.collection("floorspaces").document(floor.id).updateData(["photoBase64": FieldValue.delete()]) { _ in }
            })
        }
        sheet.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.confirmDeleteFloor(floor)
        })
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        if let popover = sheet.popoverPresentationController,
           let cell = floorTableView.cellForRow(at: indexPath) {
            popover.sourceView = cell
            popover.sourceRect = cell.bounds
        }
        present(sheet, animated: true)
    }

    // MARK: - Delete

    private func confirmDeleteWindow(_ window: WindowItem) {
        let alert = UIAlertController(title: "Delete Window",
                                      message: "Delete \"\(window.name)\"?",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.db.collection("windows").document(window.id).delete()
        })
        present(alert, animated: true)
    }

    private func confirmDeleteFloor(_ floor: FloorSpace) {
        let alert = UIAlertController(title: "Delete Floor Space",
                                      message: "Delete \"\(floor.name)\"?",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.db.collection("floorspaces").document(floor.id).delete()
        })
        present(alert, animated: true)
    }

    // MARK: - Photo Picker

    private enum PhotoTarget {
        case room
        case item(type: ItemType, id: String)
    }

    private var currentPhotoTarget: PhotoTarget?

    private func pickPhoto(for itemType: ItemType, itemId: String) {
        currentPhotoTarget = .item(type: itemType, id: itemId)
        let picker = UIImagePickerController()
        picker.sourceType = .photoLibrary
        picker.delegate = self
        present(picker, animated: true)
    }

    // MARK: - Navigation

    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        if segue.identifier == "showProductList",
           let vc = segue.destination as? ProductListViewController,
           let info = sender as? [String: Any] {
            vc.category = info["category"] as? String ?? "window"
            vc.windowWidth = info["width"] as? Double
            vc.windowHeight = info["height"] as? Double
            vc.selectionCallback = { [weak self] product, variant in
                guard let self = self, let itemId = info["itemId"] as? String else { return }
                let itemType: ItemType = (info["category"] as? String) == "floor" ? .floor : .window
                self.applyProductSelection(product: product, variant: variant, itemType: itemType, itemId: itemId)
            }
        }
    }

    private func applyProductSelection(product: Product, variant: String?, itemType: ItemType, itemId: String) {
        let result = CompatibilityChecker.check(
            product: product,
            windowWidth: itemType == .window ? windows.first(where: { $0.id == itemId })?.widthMm : nil,
            windowHeight: itemType == .window ? windows.first(where: { $0.id == itemId })?.heightMm : nil
        )
        if itemType == .window {
            db.collection("windows").document(itemId).updateData([
                "selectedProductId": product.id,
                "selectedProductName": product.name,
                "selectedProductVariant": variant ?? "",
                "panelCount": result.panelCount
            ])
        } else {
            db.collection("floorspaces").document(itemId).updateData([
                "selectedProductId": product.id,
                "selectedProductName": product.name,
                "selectedProductVariant": variant ?? ""
            ])
        }
    }

    // MARK: - Helper

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    private func dimensionString(width: Double?, height: Double?, depthLabel: String = "H") -> String {
        if let w = width, let h = height {
            return "\(Int(w))mm × \(Int(h))mm"
        } else if let w = width {
            return "W: \(Int(w))mm"
        } else if let h = height {
            return "\(depthLabel): \(Int(h))mm"
        }
        return "No dimensions"
    }
}

// MARK: - UITableViewDataSource & Delegate

extension RoomDetailViewController: UITableViewDataSource, UITableViewDelegate {

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if tableView == windowTableView {
            let count = windowsFiltered.count
            return showAllWindows ? count : min(count, maxVisibleRows)
        } else {
            let count = floorsFiltered.count
            return showAllFloors ? count : min(count, maxVisibleRows)
        }
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if tableView == windowTableView {
            let cell = tableView.dequeueReusableCell(withIdentifier: "WindowCell", for: indexPath)
            let item = windowsFiltered[indexPath.row]
            var config = cell.defaultContentConfiguration()
            config.text = item.name
            var details: [String] = [dimensionString(width: item.widthMm, height: item.heightMm)]
            if let pname = item.selectedProductName {
                var productInfo = pname
                if let variant = item.selectedProductVariant, !variant.isEmpty {
                    productInfo += " – \(variant)"
                }
                productInfo += " (\(item.panelCount) panel\(item.panelCount != 1 ? "s" : ""))"
                details.append(productInfo)
            } else {
                details.append("No product selected")
            }
            config.secondaryText = details.joined(separator: " | ")
            cell.contentConfiguration = config
            return cell
        } else {
            let cell = tableView.dequeueReusableCell(withIdentifier: "FloorCell", for: indexPath)
            let item = floorsFiltered[indexPath.row]
            var config = cell.defaultContentConfiguration()
            config.text = item.name
            var details: [String] = [dimensionString(width: item.widthMm, height: item.depthMm, depthLabel: "D")]
            if let pname = item.selectedProductName {
                var productInfo = pname
                if let variant = item.selectedProductVariant, !variant.isEmpty {
                    productInfo += " – \(variant)"
                }
                details.append(productInfo)
            } else {
                details.append("No product selected")
            }
            config.secondaryText = details.joined(separator: " | ")
            cell.contentConfiguration = config
            return cell
        }
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        return rowHeight
    }
}

// MARK: - UISearchBarDelegate

extension RoomDetailViewController: UISearchBarDelegate {
    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        if searchBar == windowSearchBar {
            windowSearchText = searchText
            applyWindowFilter()
        } else {
            floorSearchText = searchText
            applyFloorFilter()
        }
    }

    func searchBarCancelButtonClicked(_ searchBar: UISearchBar) {
        searchBar.text = ""
        if searchBar == windowSearchBar {
            windowSearchText = ""
            applyWindowFilter()
        } else {
            floorSearchText = ""
            applyFloorFilter()
        }
        searchBar.resignFirstResponder()
    }
}

// MARK: - UIImagePickerControllerDelegate

extension RoomDetailViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage,
              let base64 = PhotoHelper.imageToBase64(image) else { return }

        switch currentPhotoTarget {
        case .room:
            roomPhotoImageView.image = image
            db.collection("rooms").document(room.id).updateData(["photoBase64": base64]) { [weak self] error in
                if let error = error { self?.showAlert(title: "Error", message: error.localizedDescription) }
            }
            room.photoBase64 = base64
        case .item(let itemType, let itemId):
            let collection = itemType == .window ? "windows" : "floorspaces"
            db.collection(collection).document(itemId).updateData(["photoBase64": base64]) { [weak self] error in
                if let error = error { self?.showAlert(title: "Error", message: error.localizedDescription) }
            }
        case .none:
            break
        }
        currentPhotoTarget = nil
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }
}
