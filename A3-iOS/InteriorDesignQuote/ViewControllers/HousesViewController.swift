import UIKit
import FirebaseFirestore

class HousesViewController: UITableViewController {

    private let db = Firestore.firestore()
    private var houses: [House] = []
    private var filteredHouses: [House] = []
    private var listener: ListenerRegistration?
    private let searchController = UISearchController(searchResultsController: nil)

    private var isFiltering: Bool {
        return searchController.isActive && !(searchController.searchBar.text?.isEmpty ?? true)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Houses"
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "HouseCell")

        navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .add,
            target: self,
            action: #selector(addHouseTapped)
        )

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search houses"
        navigationItem.searchController = searchController
        definesPresentationContext = true

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        tableView.addGestureRecognizer(longPress)

        loadHouses()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
    }

    deinit {
        listener?.remove()
    }

    private func loadHouses() {
        listener = db.collection("houses").addSnapshotListener { [weak self] snapshot, error in
            guard let self = self else { return }
            if let error = error {
                self.showAlert(title: "Error", message: error.localizedDescription)
                return
            }
            self.houses = snapshot?.documents.compactMap {
                House(id: $0.documentID, data: $0.data())
            } ?? []
            self.houses.sort { $0.customerName < $1.customerName }
            self.tableView.reloadData()
        }
    }

    @objc private func addHouseTapped() {
        showHouseAlert(house: nil)
    }

    private func showHouseAlert(house: House?) {
        let isEdit = house != nil
        let alert = UIAlertController(
            title: isEdit ? "Edit House" : "Add House",
            message: nil,
            preferredStyle: .alert
        )
        alert.addTextField { tf in
            tf.placeholder = "Customer Name"
            tf.text = house?.customerName
        }
        alert.addTextField { tf in
            tf.placeholder = "Address"
            tf.text = house?.address
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: isEdit ? "Save" : "Add", style: .default) { [weak self, weak alert] _ in
            guard let self = self,
                  let name = alert?.textFields?[0].text?.trimmingCharacters(in: .whitespaces), !name.isEmpty,
                  let address = alert?.textFields?[1].text?.trimmingCharacters(in: .whitespaces), !address.isEmpty
            else { return }

            if let existing = house {
                self.db.collection("houses").document(existing.id).updateData([
                    "customerName": name,
                    "address": address
                ]) { error in
                    if let error = error {
                        self.showAlert(title: "Error", message: error.localizedDescription)
                    }
                }
            } else {
                self.db.collection("houses").addDocument(data: [
                    "customerName": name,
                    "address": address
                ]) { error in
                    if let error = error {
                        self.showAlert(title: "Error", message: error.localizedDescription)
                    }
                }
            }
        })
        present(alert, animated: true)
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        let point = gesture.location(in: tableView)
        guard let indexPath = tableView.indexPathForRow(at: point) else { return }
        let house = isFiltering ? filteredHouses[indexPath.row] : houses[indexPath.row]

        let sheet = UIAlertController(title: house.customerName, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Edit", style: .default) { [weak self] _ in
            self?.showHouseAlert(house: house)
        })
        sheet.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.confirmDeleteHouse(house)
        })
        sheet.addAction(UIAlertAction(title: "View Quote", style: .default) { [weak self] _ in
            self?.performSegue(withIdentifier: "showQuoteFromHouses", sender: house)
        })
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        if let popover = sheet.popoverPresentationController {
            if let cell = tableView.cellForRow(at: indexPath) {
                popover.sourceView = cell
                popover.sourceRect = cell.bounds
            }
        }
        present(sheet, animated: true)
    }

    private func confirmDeleteHouse(_ house: House) {
        let alert = UIAlertController(
            title: "Delete House",
            message: "Delete \"\(house.customerName)\"? This will also delete all rooms, windows, and floor spaces.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.cascadeDeleteHouse(house)
        })
        present(alert, animated: true)
    }

    private func cascadeDeleteHouse(_ house: House) {
        db.collection("rooms").whereField("houseId", isEqualTo: house.id).getDocuments { [weak self] snapshot, _ in
            guard let self = self else { return }
            let roomIds = snapshot?.documents.map { $0.documentID } ?? []

            let group = DispatchGroup()
            for roomId in roomIds {
                group.enter()
                self.db.collection("windows").whereField("roomId", isEqualTo: roomId).getDocuments { snap, _ in
                    snap?.documents.forEach { self.db.collection("windows").document($0.documentID).delete() }
                    group.leave()
                }
                group.enter()
                self.db.collection("floorspaces").whereField("roomId", isEqualTo: roomId).getDocuments { snap, _ in
                    snap?.documents.forEach { self.db.collection("floorspaces").document($0.documentID).delete() }
                    group.leave()
                }
                group.enter()
                self.db.collection("rooms").document(roomId).delete { _ in group.leave() }
            }
            group.notify(queue: .main) {
                self.db.collection("houses").document(house.id).delete { error in
                    if let error = error {
                        self.showAlert(title: "Error", message: error.localizedDescription)
                    }
                }
            }
        }
    }

    // MARK: - UITableViewDataSource

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return isFiltering ? filteredHouses.count : houses.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "HouseCell", for: indexPath)
        let house = isFiltering ? filteredHouses[indexPath.row] : houses[indexPath.row]
        var config = cell.defaultContentConfiguration()
        config.text = house.customerName
        config.secondaryText = house.address
        cell.contentConfiguration = config
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    // MARK: - UITableViewDelegate

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let house = isFiltering ? filteredHouses[indexPath.row] : houses[indexPath.row]
        performSegue(withIdentifier: "showHouseDetail", sender: house)
    }

    // MARK: - Navigation

    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        if segue.identifier == "showHouseDetail",
           let vc = segue.destination as? HouseDetailViewController,
           let house = sender as? House {
            vc.house = house
        } else if segue.identifier == "showQuoteFromHouses",
                  let vc = segue.destination as? QuoteViewController,
                  let house = sender as? House {
            vc.house = house
        }
    }

    // MARK: - Helper

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

// MARK: - UISearchResultsUpdating

extension HousesViewController: UISearchResultsUpdating {
    func updateSearchResults(for searchController: UISearchController) {
        guard let query = searchController.searchBar.text?.lowercased() else { return }
        filteredHouses = houses.filter {
            $0.customerName.lowercased().contains(query) || $0.address.lowercased().contains(query)
        }
        tableView.reloadData()
    }
}
