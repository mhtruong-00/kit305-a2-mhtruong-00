import UIKit
import FirebaseFirestore

// MARK: - Quote Data Models

private struct QuoteWindow {
    let item: WindowItem
    var included: Bool
}

private struct QuoteFloor {
    let item: FloorSpace
    var included: Bool
}

private struct QuoteRoom {
    let room: Room
    var included: Bool
    var windows: [QuoteWindow]
    var floors: [QuoteFloor]
}

private enum ToggleTarget {
    case room(Int)
    case window(roomIndex: Int, itemIndex: Int)
    case floor(roomIndex: Int, itemIndex: Int)
}

class QuoteViewController: UIViewController {

    var house: House!

    private let db = Firestore.firestore()
    private var quoteRooms: [QuoteRoom] = []
    private var discountPercent: Double = 0.0
    private let labourPerRoom: Double = 200.0
    private let defaultWindowRate: Double = 50.0
    private let defaultFloorRate: Double = 100.0

    // MARK: - UI

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let totalsStack = UIStackView()
    private let subtotalLabel = UILabel()
    private let labourLabel = UILabel()
    private let discountLabel = UILabel()
    private let totalLabel = UILabel()
    private let discountField = UITextField()
    private var itemsStack = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = "Quote"

        navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .action,
            target: self,
            action: #selector(shareTapped)
        )

        setupUI()
        loadData()
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
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)
        NSLayoutConstraint.activate([
            contentStack.topAnchor.constraint(equalTo: scrollView.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor, constant: -16),
            contentStack.widthAnchor.constraint(equalTo: scrollView.widthAnchor, constant: -32)
        ])

        let houseLabel = UILabel()
        houseLabel.font = .boldSystemFont(ofSize: 20)
        houseLabel.text = house.customerName
        houseLabel.numberOfLines = 0
        contentStack.addArrangedSubview(houseLabel)

        let addressLabel = UILabel()
        addressLabel.font = .systemFont(ofSize: 15)
        addressLabel.textColor = .secondaryLabel
        addressLabel.text = house.address
        addressLabel.numberOfLines = 0
        contentStack.addArrangedSubview(addressLabel)

        let sep1 = makeSeparator()
        contentStack.addArrangedSubview(sep1)

        itemsStack.axis = .vertical
        itemsStack.spacing = 8
        contentStack.addArrangedSubview(itemsStack)

        let sep2 = makeSeparator()
        contentStack.addArrangedSubview(sep2)

        // Discount
        let discountRow = UIStackView()
        discountRow.axis = .horizontal
        discountRow.spacing = 8

        let discountTitleLabel = UILabel()
        discountTitleLabel.text = "Discount %:"
        discountRow.addArrangedSubview(discountTitleLabel)

        discountField.borderStyle = .roundedRect
        discountField.keyboardType = .decimalPad
        discountField.placeholder = "0"
        discountField.text = "0"
        discountField.widthAnchor.constraint(equalToConstant: 80).isActive = true
        discountRow.addArrangedSubview(discountField)

        let applyButton = UIButton(type: .system)
        applyButton.setTitle("Apply", for: .normal)
        applyButton.addTarget(self, action: #selector(applyDiscount), for: .touchUpInside)
        discountRow.addArrangedSubview(applyButton)

        let spacer = UIView()
        spacer.setContentHuggingPriority(.defaultLow, for: .horizontal)
        discountRow.addArrangedSubview(spacer)

        contentStack.addArrangedSubview(discountRow)

        let sep3 = makeSeparator()
        contentStack.addArrangedSubview(sep3)

        // Totals
        totalsStack.axis = .vertical
        totalsStack.spacing = 6
        contentStack.addArrangedSubview(totalsStack)

        [subtotalLabel, labourLabel, discountLabel].forEach { label in
            label.font = .systemFont(ofSize: 15)
            totalsStack.addArrangedSubview(label)
        }

        totalLabel.font = .boldSystemFont(ofSize: 18)
        totalsStack.addArrangedSubview(totalLabel)
    }

    private func makeSeparator() -> UIView {
        let sep = UIView()
        sep.backgroundColor = .separator
        sep.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return sep
    }

    // MARK: - Data Loading

    private var productCache: [String: Double] = [:]
    private var nextToggleTag = 1
    private var toggleTargets: [Int: ToggleTarget] = [:]

    private func loadData() {
        let outerGroup = DispatchGroup()

        // Fetch product prices from API (both categories in parallel)
        for category in ["window", "floor"] {
            outerGroup.enter()
            guard let url = APIConfig.productURL(category: category) else { outerGroup.leave(); continue }
            URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
                if let data = data,
                   let products = try? Product.decodeList(from: data) {
                    DispatchQueue.main.async {
                        for p in products { self?.productCache[p.id] = p.pricePerSqm }
                        outerGroup.leave()
                    }
                } else {
                    outerGroup.leave()
                }
            }.resume()
        }

        // Fetch rooms + their windows/floors from Firestore
        outerGroup.enter()
        var fetchedQuoteRooms: [QuoteRoom] = []

        db.collection("rooms").whereField("houseId", isEqualTo: house.id).getDocuments { [weak self] snap, error in
            guard let self = self else { outerGroup.leave(); return }
            if let error = error {
                self.showAlert(title: "Error", message: error.localizedDescription)
                outerGroup.leave()
                return
            }
            let rooms = (snap?.documents.compactMap { Room(id: $0.documentID, data: $0.data()) } ?? [])
                .sorted { $0.name < $1.name }

            guard !rooms.isEmpty else { outerGroup.leave(); return }

            let roomGroup = DispatchGroup()
            for room in rooms {
                roomGroup.enter()
                var rawWindows: [WindowItem] = []
                var rawFloors: [FloorSpace] = []
                let innerGroup = DispatchGroup()

                innerGroup.enter()
                self.db.collection("windows").whereField("roomId", isEqualTo: room.id).getDocuments { wSnap, _ in
                    rawWindows = wSnap?.documents.compactMap { WindowItem(id: $0.documentID, data: $0.data()) } ?? []
                    innerGroup.leave()
                }
                innerGroup.enter()
                self.db.collection("floorspaces").whereField("roomId", isEqualTo: room.id).getDocuments { fSnap, _ in
                    rawFloors = fSnap?.documents.compactMap { FloorSpace(id: $0.documentID, data: $0.data()) } ?? []
                    innerGroup.leave()
                }
                innerGroup.notify(queue: .main) {
                    fetchedQuoteRooms.append(QuoteRoom(
                        room: room,
                        included: true,
                        windows: rawWindows.map { QuoteWindow(item: $0, included: true) },
                        floors: rawFloors.map { QuoteFloor(item: $0, included: true) }
                    ))
                    roomGroup.leave()
                }
            }
            roomGroup.notify(queue: .main) {
                self.quoteRooms = fetchedQuoteRooms.sorted { $0.room.name < $1.room.name }
                outerGroup.leave()
            }
        }

        // After all API + Firestore calls complete, costs are computed on-demand using the cache
        outerGroup.notify(queue: .main) { [weak self] in
            guard let self = self else { return }
            self.buildItemsUI()
            self.recalculate()
        }
    }

    // MARK: - Cost Calculation (uses productCache populated by loadData)

    private func windowCost(_ w: WindowItem) -> Double {
        guard let areaSqm = areaSqm(widthMm: w.widthMm, heightMm: w.heightMm) else { return 0 }
        let pricePerSqm = resolveRate(productId: w.selectedProductId, defaultRate: defaultWindowRate)
        return areaSqm * pricePerSqm * Double(max(1, w.panelCount))
    }

    private func floorCost(_ f: FloorSpace) -> Double {
        guard let areaSqm = areaSqm(widthMm: f.widthMm, heightMm: f.depthMm) else { return 0 }
        let pricePerSqm = resolveRate(productId: f.selectedProductId, defaultRate: defaultFloorRate)
        return areaSqm * pricePerSqm
    }

    private func resolveRate(productId: String?, defaultRate: Double) -> Double {
        guard let productId, !productId.isEmpty else { return defaultRate }
        return productCache[productId] ?? defaultRate
    }

    private func areaSqm(widthMm: Double?, heightMm: Double?) -> Double? {
        guard let widthMm, let heightMm, widthMm > 0, heightMm > 0 else { return nil }
        return (widthMm * heightMm) / 1_000_000.0
    }

    private func roomLabour(for room: QuoteRoom) -> Double {
        let hasMeasuredIncludedItem =
            room.windows.contains { $0.included && areaSqm(widthMm: $0.item.widthMm, heightMm: $0.item.heightMm) != nil } ||
            room.floors.contains { $0.included && areaSqm(widthMm: $0.item.widthMm, heightMm: $0.item.depthMm) != nil }
        return hasMeasuredIncludedItem ? labourPerRoom : 0
    }

    private func formatMoney(_ value: Double) -> String {
        String(format: "%.2f", value)
    }

    private func productDisplayName(name: String?, fallback: String, variant: String?) -> String {
        let baseName = (name?.isEmpty == false ? name! : fallback)
        guard let variant, !variant.isEmpty else { return baseName }
        return "\(baseName) – \(variant)"
    }

    // MARK: - UI Building

    private func buildItemsUI() {
        itemsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        toggleTargets.removeAll()
        nextToggleTag = 1

        for (roomIndex, qr) in quoteRooms.enumerated() {
            let roomTag = makeToggleTag()
            toggleTargets[roomTag] = .room(roomIndex)
            let roomRow = makeToggleRow(
                text: qr.room.name,
                isBold: true,
                isOn: qr.included,
                tag: roomTag,
                action: #selector(roomToggled(_:))
            )
            itemsStack.addArrangedSubview(roomRow)

            for (winIdx, qw) in qr.windows.enumerated() {
                let cost = windowCost(qw.item)
                let windowTag = makeToggleTag()
                toggleTargets[windowTag] = .window(roomIndex: roomIndex, itemIndex: winIdx)
                let row = makeToggleRow(
                    text: "  🪟 \(qw.item.name) ($\(String(format: "%.2f", cost)))",
                    isBold: false,
                    isOn: qw.included,
                    tag: windowTag,
                    action: #selector(windowItemToggled(_:))
                )
                itemsStack.addArrangedSubview(row)
            }

            for (floorIdx, qf) in qr.floors.enumerated() {
                let cost = floorCost(qf.item)
                let floorTag = makeToggleTag()
                toggleTargets[floorTag] = .floor(roomIndex: roomIndex, itemIndex: floorIdx)
                let row = makeToggleRow(
                    text: "  🏠 \(qf.item.name) ($\(String(format: "%.2f", cost)))",
                    isBold: false,
                    isOn: qf.included,
                    tag: floorTag,
                    action: #selector(floorItemToggled(_:))
                )
                itemsStack.addArrangedSubview(row)
            }
        }
    }

    private func makeToggleTag() -> Int {
        defer { nextToggleTag += 1 }
        return nextToggleTag
    }

    private func makeToggleRow(text: String, isBold: Bool, isOn: Bool, tag: Int, action: Selector) -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 8

        let label = UILabel()
        label.text = text
        label.font = isBold ? .boldSystemFont(ofSize: 15) : .systemFont(ofSize: 14)
        label.numberOfLines = 0
        label.setContentHuggingPriority(.defaultLow, for: .horizontal)
        row.addArrangedSubview(label)

        let toggle = UISwitch()
        toggle.isOn = isOn
        toggle.tag = tag
        toggle.addTarget(self, action: action, for: .valueChanged)
        row.addArrangedSubview(toggle)

        return row
    }

    // MARK: - Toggle Actions

    @objc private func roomToggled(_ sender: UISwitch) {
        guard case let .room(roomIndex)? = toggleTargets[sender.tag] else { return }
        guard roomIndex < quoteRooms.count else { return }
        quoteRooms[roomIndex].included = sender.isOn
        recalculate()
    }

    @objc private func windowItemToggled(_ sender: UISwitch) {
        guard case let .window(roomIndex, itemIndex)? = toggleTargets[sender.tag] else { return }
        guard roomIndex < quoteRooms.count,
               itemIndex >= 0, itemIndex < quoteRooms[roomIndex].windows.count else { return }
        quoteRooms[roomIndex].windows[itemIndex].included = sender.isOn
        recalculate()
    }

    @objc private func floorItemToggled(_ sender: UISwitch) {
        guard case let .floor(roomIndex, itemIndex)? = toggleTargets[sender.tag] else { return }
        guard roomIndex < quoteRooms.count,
               itemIndex >= 0, itemIndex < quoteRooms[roomIndex].floors.count else { return }
        quoteRooms[roomIndex].floors[itemIndex].included = sender.isOn
        recalculate()
    }

    @objc private func applyDiscount() {
        discountPercent = Double(discountField.text ?? "") ?? 0.0
        discountField.resignFirstResponder()
        recalculate()
    }

    // MARK: - Recalculate

    private func recalculate() {
        var itemSubtotal: Double = 0
        var labourTotal: Double = 0

        for qr in quoteRooms where qr.included {
            labourTotal += roomLabour(for: qr)
            for qw in qr.windows where qw.included {
                itemSubtotal += windowCost(qw.item)
            }
            for qf in qr.floors where qf.included {
                itemSubtotal += floorCost(qf.item)
            }
        }

        let subtotal = itemSubtotal + labourTotal
        let discountAmt = subtotal * discountPercent / 100.0
        let finalTotal = subtotal - discountAmt

        subtotalLabel.text = String(format: "Items Subtotal: $%.2f", itemSubtotal)
        labourLabel.text = String(format: "Labour: $%.2f", labourTotal)
        discountLabel.text = String(format: "Discount (%.1f%%): -$%.2f", discountPercent, discountAmt)
        totalLabel.text = String(format: "Total: $%.2f", finalTotal)
    }

    // MARK: - Share

    @objc private func shareTapped() {
        let text = buildShareText()
        let csv = buildCSV()

        let csvData = csv.data(using: .utf8) ?? Data()
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("quote.csv")
        try? csvData.write(to: tempURL)

        let activityVC = UIActivityViewController(
            activityItems: [text, tempURL],
            applicationActivities: nil
        )
        if let popover = activityVC.popoverPresentationController {
            popover.barButtonItem = navigationItem.rightBarButtonItem
        }
        present(activityVC, animated: true)
    }

    private func buildShareText() -> String {
        var lines: [String] = [
            "INTERIOR DESIGN QUOTE",
            "Customer: \(house.customerName)",
            "Address: \(house.address)",
            ""
        ]

        var itemSubtotal: Double = 0
        var labourTotal: Double = 0

        for qr in quoteRooms where qr.included {
            lines.append("Room: \(qr.room.name)")
            labourTotal += roomLabour(for: qr)
            for qw in qr.windows where qw.included {
                let cost = windowCost(qw.item)
                lines.append("  Window - \(qw.item.name): $\(formatMoney(cost))")
                itemSubtotal += cost
            }
            for qf in qr.floors where qf.included {
                let cost = floorCost(qf.item)
                lines.append("  Floor - \(qf.item.name): $\(formatMoney(cost))")
                itemSubtotal += cost
            }
            lines.append("  Labour: $\(formatMoney(roomLabour(for: qr)))")
            lines.append("")
        }

        let subtotal = itemSubtotal + labourTotal
        let discountAmt = subtotal * discountPercent / 100.0
        let finalTotal = subtotal - discountAmt

        lines.append(String(format: "Items Subtotal: $%.2f", itemSubtotal))
        lines.append(String(format: "Labour: $%.2f", labourTotal))
        lines.append(String(format: "Discount (%.1f%%): -$%.2f", discountPercent, discountAmt))
        lines.append(String(format: "TOTAL: $%.2f", finalTotal))

        return lines.joined(separator: "\n")
    }

    private func buildCSV() -> String {
        var rows: [String] = ["Room,Type,Item,Product,RatePerSqm,AreaSqm,Cost,Included"]
        var itemSubtotal: Double = 0
        var labourTotal: Double = 0

        for qr in quoteRooms where qr.included {
            for qw in qr.windows where qw.included {
                let cost = windowCost(qw.item)
                let rate = resolveRate(productId: qw.item.selectedProductId, defaultRate: defaultWindowRate)
                let area = areaSqm(widthMm: qw.item.widthMm, heightMm: qw.item.heightMm) ?? 0
                rows.append("\"\(qr.room.name)\",Window,\"\(qw.item.name)\",\"\(productDisplayName(name: qw.item.selectedProductName, fallback: "Basic Window", variant: qw.item.selectedProductVariant))\",\(formatMoney(rate)),\(formatMoney(area)),\(formatMoney(cost)),true")
                itemSubtotal += cost
            }
            for qf in qr.floors where qf.included {
                let cost = floorCost(qf.item)
                let rate = resolveRate(productId: qf.item.selectedProductId, defaultRate: defaultFloorRate)
                let area = areaSqm(widthMm: qf.item.widthMm, heightMm: qf.item.depthMm) ?? 0
                rows.append("\"\(qr.room.name)\",Floor,\"\(qf.item.name)\",\"\(productDisplayName(name: qf.item.selectedProductName, fallback: "Basic Floor", variant: qf.item.selectedProductVariant))\",\(formatMoney(rate)),\(formatMoney(area)),\(formatMoney(cost)),true")
                itemSubtotal += cost
            }
            labourTotal += roomLabour(for: qr)
        }
        let subtotal = itemSubtotal + labourTotal
        let discountAmount = subtotal * discountPercent / 100.0
        let finalTotal = subtotal - discountAmount
        rows.append("Summary,Items Subtotal,,, ,,\(formatMoney(itemSubtotal)),")
        rows.append("Summary,Labour,,, ,,\(formatMoney(labourTotal)),")
        rows.append("Summary,Discount \(String(format: "%.1f", discountPercent))%,,, ,,\(formatMoney(discountAmount)),")
        rows.append("Summary,Final Total,,, ,,\(formatMoney(finalTotal)),")
        return rows.joined(separator: "\n")
    }

    // MARK: - Helper

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
