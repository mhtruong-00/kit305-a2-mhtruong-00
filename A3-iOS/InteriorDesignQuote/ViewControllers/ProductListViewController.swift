import UIKit

class ProductListViewController: UITableViewController {

    var category: String = "window"
    var windowWidth: Double?
    var windowHeight: Double?
    var selectionCallback: ((Product, String?) -> Void)?

    private var products: [Product] = []
    private var compatibilityResults: [String: CompatibilityResult] = [:]
    private let activityIndicator = UIActivityIndicatorView(style: .medium)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = category == "window" ? "Window Products" : "Floor Products"
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "ProductCell")
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 70

        navigationItem.rightBarButtonItem = UIBarButtonItem(customView: activityIndicator)
        activityIndicator.startAnimating()

        loadProducts()
    }

    private func loadProducts() {
        let urlString = "https://utasbot.dev/kit305_2026/product?category=\(category)"
        guard let url = URL(string: urlString) else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.activityIndicator.stopAnimating()

                if let error = error {
                    self.showAlert(title: "Error", message: error.localizedDescription)
                    return
                }
                guard let data = data else { return }

                do {
                    self.products = try JSONDecoder().decode([Product].self, from: data)
                    self.computeCompatibility()
                    self.tableView.reloadData()
                } catch {
                    self.showAlert(title: "Parse Error", message: error.localizedDescription)
                }
            }
        }.resume()
    }

    private func computeCompatibility() {
        for product in products {
            let result = CompatibilityChecker.check(product: product,
                                                     windowWidth: windowWidth,
                                                     windowHeight: windowHeight)
            compatibilityResults[product.id] = result
        }
        // Sort: compatible first
        products.sort { a, b in
            let ra = compatibilityResults[a.id]?.compatible ?? false
            let rb = compatibilityResults[b.id]?.compatible ?? false
            if ra != rb { return ra && !rb }
            return a.name < b.name
        }
    }

    // MARK: - UITableViewDataSource

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return products.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "ProductCell", for: indexPath)
        let product = products[indexPath.row]
        let result = compatibilityResults[product.id]

        var config = cell.defaultContentConfiguration()
        config.text = product.name
        let price = String(format: "$%.2f/sqm", product.pricePerSqm)
        let compatMsg = result?.message ?? "Unknown"
        config.secondaryText = "\(price) | \(compatMsg)"

        if result?.compatible == true {
            config.textProperties.color = .label
            cell.accessoryType = .disclosureIndicator
        } else {
            config.textProperties.color = .secondaryLabel
            cell.accessoryType = .none
        }
        cell.contentConfiguration = config
        return cell
    }

    // MARK: - UITableViewDelegate

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let product = products[indexPath.row]
        guard compatibilityResults[product.id]?.compatible == true else { return }

        if !product.variants.isEmpty {
            showVariantPicker(for: product)
        } else {
            returnProduct(product, variant: nil)
        }
    }

    private func showVariantPicker(for product: Product) {
        let alert = UIAlertController(title: "Select Variant",
                                      message: "Choose a variant for \(product.name)",
                                      preferredStyle: .actionSheet)
        for variant in product.variants {
            alert.addAction(UIAlertAction(title: variant, style: .default) { [weak self] _ in
                self?.returnProduct(product, variant: variant)
            })
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = tableView
            popover.sourceRect = tableView.bounds
        }
        present(alert, animated: true)
    }

    private func returnProduct(_ product: Product, variant: String?) {
        selectionCallback?(product, variant)
        navigationController?.popViewController(animated: true)
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
