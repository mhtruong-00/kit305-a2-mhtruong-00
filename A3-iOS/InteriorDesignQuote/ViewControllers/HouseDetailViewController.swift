import UIKit
import FirebaseFirestore

class HouseDetailViewController: UITableViewController {

    var house: House!

    private let db = Firestore.firestore()
    private var rooms: [Room] = []
    private var filteredRooms: [Room] = []
    private var listener: ListenerRegistration?
    private let searchController = UISearchController(searchResultsController: nil)

    private var isFiltering: Bool {
        return searchController.isActive && !(searchController.searchBar.text?.isEmpty ?? true)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "\(house.customerName)"
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "RoomCell")

        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "Quote", style: .plain, target: self, action: #selector(quoteTapped)),
            UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(addRoomTapped))
        ]

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search rooms"
        navigationItem.searchController = searchController
        definesPresentationContext = true

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        tableView.addGestureRecognizer(longPress)

        loadRooms()
    }

    deinit {
        listener?.remove()
    }

    private func loadRooms() {
        listener = db.collection("rooms")
            .whereField("houseId", isEqualTo: house.id)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self = self else { return }
                if let error = error {
                    self.showAlert(title: "Error", message: error.localizedDescription)
                    return
                }
                self.rooms = snapshot?.documents.compactMap {
                    Room(id: $0.documentID, data: $0.data())
                } ?? []
                self.rooms.sort { $0.name < $1.name }
                self.tableView.reloadData()
            }
    }

    @objc private func addRoomTapped() {
        showRoomAlert(room: nil)
    }

    @objc private func quoteTapped() {
        performSegue(withIdentifier: "showQuote", sender: house)
    }

    private func showRoomAlert(room: Room?) {
        let isEdit = room != nil
        let alert = UIAlertController(
            title: isEdit ? "Edit Room" : "Add Room",
            message: nil,
            preferredStyle: .alert
        )
        alert.addTextField { tf in
            tf.placeholder = "Room Name"
            tf.text = room?.name
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: isEdit ? "Save" : "Add", style: .default) { [weak self, weak alert] _ in
            guard let self = self,
                  let name = alert?.textFields?[0].text?.trimmingCharacters(in: .whitespaces), !name.isEmpty
            else { return }

            if let existing = room {
                self.db.collection("rooms").document(existing.id).updateData(["name": name]) { error in
                    if let error = error { self.showAlert(title: "Error", message: error.localizedDescription) }
                }
            } else {
                self.db.collection("rooms").addDocument(data: [
                    "name": name,
                    "houseId": self.house.id
                ]) { error in
                    if let error = error { self.showAlert(title: "Error", message: error.localizedDescription) }
                }
            }
        })
        present(alert, animated: true)
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        let point = gesture.location(in: tableView)
        guard let indexPath = tableView.indexPathForRow(at: point) else { return }
        let room = isFiltering ? filteredRooms[indexPath.row] : rooms[indexPath.row]

        let sheet = UIAlertController(title: room.name, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Edit Name", style: .default) { [weak self] _ in
            self?.showRoomAlert(room: room)
        })
        sheet.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.confirmDeleteRoom(room)
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

    private func confirmDeleteRoom(_ room: Room) {
        let alert = UIAlertController(
            title: "Delete Room",
            message: "Delete \"\(room.name)\"? This will also delete all windows and floor spaces.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.cascadeDeleteRoom(room)
        })
        present(alert, animated: true)
    }

    private func cascadeDeleteRoom(_ room: Room) {
        let group = DispatchGroup()

        group.enter()
        db.collection("windows").whereField("roomId", isEqualTo: room.id).getDocuments { [weak self] snap, _ in
            snap?.documents.forEach { self?.db.collection("windows").document($0.documentID).delete() }
            group.leave()
        }

        group.enter()
        db.collection("floorspaces").whereField("roomId", isEqualTo: room.id).getDocuments { [weak self] snap, _ in
            snap?.documents.forEach { self?.db.collection("floorspaces").document($0.documentID).delete() }
            group.leave()
        }

        group.notify(queue: .main) { [weak self] in
            guard let self = self else { return }
            self.db.collection("rooms").document(room.id).delete { error in
                if let error = error {
                    self.showAlert(title: "Error", message: error.localizedDescription)
                }
            }
        }
    }

    // MARK: - UITableViewDataSource

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return isFiltering ? filteredRooms.count : rooms.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "RoomCell", for: indexPath)
        let room = isFiltering ? filteredRooms[indexPath.row] : rooms[indexPath.row]
        var config = cell.defaultContentConfiguration()
        config.text = room.name
        cell.contentConfiguration = config
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    // MARK: - UITableViewDelegate

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let room = isFiltering ? filteredRooms[indexPath.row] : rooms[indexPath.row]
        performSegue(withIdentifier: "showRoomDetail", sender: room)
    }

    // MARK: - Navigation

    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        if segue.identifier == "showRoomDetail",
           let vc = segue.destination as? RoomDetailViewController,
           let room = sender as? Room {
            vc.room = room
        } else if segue.identifier == "showQuote",
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

extension HouseDetailViewController: UISearchResultsUpdating {
    func updateSearchResults(for searchController: UISearchController) {
        guard let query = searchController.searchBar.text?.lowercased() else { return }
        filteredRooms = rooms.filter { $0.name.lowercased().contains(query) }
        tableView.reloadData()
    }
}
