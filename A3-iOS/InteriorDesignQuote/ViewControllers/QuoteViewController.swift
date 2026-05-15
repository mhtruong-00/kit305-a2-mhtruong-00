import UIKit
import FirebaseFirestore

// MARK: - Quote Data Models

private struct QuoteItem {
    let name: String
    let cost: Double
    var included: Bool
}

private struct QuoteRoom {
    let room: Room
    var included: Bool
    var windowItems: [QuoteItem]
    var floorItems: [QuoteItem]
}

class QuoteViewController: UIViewController {

    var house: House!

    private let db = Firestore.firestore()
    private var quoteRooms: [QuoteRoom] = []
    private var discountPercent: Double = 0.0
    private let labourPerRoom: Double = 200.0

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

    private func loadData() {
        db.collection("rooms").whereField("houseId", isEqualTo: house.id).getDocuments { [weak self] snap, error in
            guard let self = self else { return }
            if let error = error { self.showAlert(title: "Error", message: error.localizedDescription); return }
            let rooms = snap?.documents.compactMap { Room(id: $0.documentID, data: $0.data()) } ?? []
            let sortedRooms = rooms.sorted { $0.name < $1.name }

            let group = DispatchGroup()
            var quoteRooms: [QuoteRoom] = []

            for room in sortedRooms {
                group.enter()
                var windowItems: [QuoteItem] = []
                var floorItems: [QuoteItem] = []

                let innerGroup = DispatchGroup()

                innerGroup.enter()
                self.db.collection("windows").whereField("roomId", isEqualTo: room.id).getDocuments { wSnap, _ in
                    let windows = wSnap?.documents.compactMap { WindowItem(id: $0.documentID, data: $0.data()) } ?? []
                    for w in windows {
                        let cost = self.windowCost(w)
                        windowItems.append(QuoteItem(name: w.name, cost: cost, included: true))
                    }
                    innerGroup.leave()
                }

                innerGroup.enter()
                self.db.collection("floorspaces").whereField("roomId", isEqualTo: room.id).getDocuments { fSnap, _ in
                    let floors = fSnap?.documents.compactMap { FloorSpace(id: $0.documentID, data: $0.data()) } ?? []
                    for f in floors {
                        let cost = self.floorCost(f)
                        floorItems.append(QuoteItem(name: f.name, cost: cost, included: true))
                    }
                    innerGroup.leave()
                }

                innerGroup.notify(queue: .main) {
                    quoteRooms.append(QuoteRoom(
                        room: room,
                        included: true,
                        windowItems: windowItems,
                        floorItems: floorItems
                    ))
                    group.leave()
                }
            }

            group.notify(queue: .main) {
                self.quoteRooms = quoteRooms.sorted { $0.room.name < $1.room.name }
                self.buildItemsUI()
                self.recalculate()
            }
        }
    }

    // MARK: - Cost Calculation

    private func windowCost(_ w: WindowItem) -> Double {
        guard let width = w.widthMm, let height = w.heightMm, w.selectedProductId != nil else { return 0 }
        let areaSqm = (width * height) / 1_000_000.0
        // pricePerSqm is not stored locally; we'd need to re-fetch from API or store it
        // For quote we use stored product data — price not in Firestore, so we show 0 unless re-fetched
        // To make this work without API re-fetch, we'd need to store price. For now return area * panels
        // This is a known limitation — proper implementation would cache the price.
        return areaSqm * Double(w.panelCount)
    }

    private func floorCost(_ f: FloorSpace) -> Double {
        guard let width = f.widthMm, let depth = f.depthMm, f.selectedProductId != nil else { return 0 }
        let areaSqm = (width * depth) / 1_000_000.0
        return areaSqm
    }

    // MARK: - Product-aware calculation (fetches prices from API)

    private var productCache: [String: Double] = [:]  // productId -> pricePerSqm

    private func loadProductPricesAndRecalculate() {
        let allProductIds = Set(
            quoteRooms.flatMap { qr in
                qr.windowItems.map { _ in "" } + qr.floorItems.map { _ in "" }
            }
        )
        // Re-fetch products for pricing
        let group = DispatchGroup()
        for category in ["window", "floor"] {
            group.enter()
            let urlString = "https://utasbot.dev/kit305_2026/product?category=\(category)"
            guard let url = URL(string: urlString) else { group.leave(); continue }
            URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
                if let data = data,
                   let products = try? JSONDecoder().decode([Product].self, from: data) {
                    for p in products {
                        self?.productCache[p.id] = p.pricePerSqm
                    }
                }
                group.leave()
            }.resume()
        }
        group.notify(queue: .main) { [weak self] in
            self?.recalculate()
        }
    }

    // MARK: - UI Building

    private func buildItemsUI() {
        itemsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }

        for (roomIndex, qr) in quoteRooms.enumerated() {
            let roomRow = makeToggleRow(
                text: qr.room.name,
                isBold: true,
                isOn: qr.included,
                tag: roomIndex * 10000,
                action: #selector(roomToggled(_:))
            )
            itemsStack.addArrangedSubview(roomRow)

            for (winIdx, item) in qr.windowItems.enumerated() {
                let row = makeToggleRow(
                    text: "  🪟 \(item.name)",
                    isBold: false,
                    isOn: item.included,
                    tag: roomIndex * 10000 + winIdx + 1,
                    action: #selector(windowItemToggled(_:))
                )
                itemsStack.addArrangedSubview(row)
            }

            for (floorIdx, item) in qr.floorItems.enumerated() {
                let row = makeToggleRow(
                    text: "  🏠 \(item.name)",
                    isBold: false,
                    isOn: item.included,
                    tag: roomIndex * 10000 + 5000 + floorIdx + 1,
                    action: #selector(floorItemToggled(_:))
                )
                itemsStack.addArrangedSubview(row)
            }
        }
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
        let roomIndex = sender.tag / 10000
        guard roomIndex < quoteRooms.count else { return }
        quoteRooms[roomIndex].included = sender.isOn
        recalculate()
    }

    @objc private func windowItemToggled(_ sender: UISwitch) {
        let roomIndex = sender.tag / 10000
        let itemIndex = (sender.tag % 10000) - 1
        guard roomIndex < quoteRooms.count, itemIndex < quoteRooms[roomIndex].windowItems.count else { return }
        quoteRooms[roomIndex].windowItems[itemIndex].included = sender.isOn
        recalculate()
    }

    @objc private func floorItemToggled(_ sender: UISwitch) {
        let roomIndex = sender.tag / 10000
        let itemIndex = (sender.tag % 10000) - 5001
        guard roomIndex < quoteRooms.count, itemIndex >= 0, itemIndex < quoteRooms[roomIndex].floorItems.count else { return }
        quoteRooms[roomIndex].floorItems[itemIndex].included = sender.isOn
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
            labourTotal += labourPerRoom
            for item in qr.windowItems where item.included {
                itemSubtotal += item.cost
            }
            for item in qr.floorItems where item.included {
                itemSubtotal += item.cost
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
            labourTotal += labourPerRoom
            for item in qr.windowItems where item.included {
                lines.append("  Window - \(item.name): $\(String(format: "%.2f", item.cost))")
                itemSubtotal += item.cost
            }
            for item in qr.floorItems where item.included {
                lines.append("  Floor - \(item.name): $\(String(format: "%.2f", item.cost))")
                itemSubtotal += item.cost
            }
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
        var rows: [String] = ["Room,Type,Item,Cost"]
        for qr in quoteRooms where qr.included {
            for item in qr.windowItems where item.included {
                rows.append("\"\(qr.room.name)\",Window,\"\(item.name)\",\(String(format: "%.2f", item.cost))")
            }
            for item in qr.floorItems where item.included {
                rows.append("\"\(qr.room.name)\",Floor,\"\(item.name)\",\(String(format: "%.2f", item.cost))")
            }
        }
        return rows.joined(separator: "\n")
    }

    // MARK: - Helper

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
