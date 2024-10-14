import Foundation

class User {
    var name: String
    var gender: String
    var age: Int
    var location: (Int, Int)
    
    init(name: String, gender: String, age: Int, location: (Int, Int) = (0, 0)) {
        self.name = name
        self.gender = gender
        self.age = age
        self.location = location
    }
    
    func updateUserDetails(updatedDetails: [String: Any]) {
        for (key, value) in updatedDetails {
            switch key {
            case "name":
                self.name = value as! String
            case "gender":
                self.gender = value as! String
            case "age":
                self.age = value as! Int
            case "location":
                self.location = value as! (Int, Int)
            default:
                break
            }
        }
    }
    
    func updateUserLocation(location: (Int, Int)) {
        self.location = location
    }
}

class Driver {
    var name: String
    var gender: String
    var age: Int
    var vehicle: String
    var vehicleNumber: String
    var location: (Int, Int)
    var status: Bool
    var earning: Int
    private let lock = NSLock()
    
    init(name: String, gender: String, age: Int, vehicle: String, vehicleNumber: String, location: (Int, Int), status: Bool = true) {
        self.name = name
        self.gender = gender
        self.age = age
        self.vehicle = vehicle
        self.vehicleNumber = vehicleNumber
        self.location = location
        self.status = status
        self.earning = 0
    }
    
    func updateDriverLocation(location: (Int, Int)) {
        lock.lock()
        defer { lock.unlock() }
        self.location = location
    }
    
    func changeDriverStatus(status: Bool) {
        lock.lock()
        defer { lock.unlock() }
        self.status = status
    }
    
    func addEarning(amount: Int) {
        lock.lock()
        defer { lock.unlock() }
        self.earning += amount
    }
}

class CabBookingApplication {
    private var users: [String: User]
    private var drivers: [String: Driver]
    private let userLock = NSLock()
    private let driverLock = NSRecursiveLock()
    private let rideRequestQueue = DispatchQueue(label: "rideRequestQueue", attributes: .concurrent)
    private var running = true
    internal let event = DispatchSemaphore(value: 0) // Changed from private to internal
    
    init() {
        self.users = [:]
        self.drivers = [:]
    }
    
    func addUser(userDetail: String) {
        let details = userDetail.split(separator: ", ").map { String($0) }
        let name = details[0]
        let gender = details[1]
        let age = Int(details[2])!
        let user = User(name: name, gender: gender, age: age)
        userLock.lock()
        defer { userLock.unlock() }
        users[name] = user
        print("User \(name) added")
    }
    
    func updateUserLocation(username: String, location: (Int, Int)) {
        userLock.lock()
        defer { userLock.unlock() }
        if let user = users[username] {
            user.updateUserLocation(location: location)
            print("User \(username) location updated to \(location)")
        }
    }
    
    func addDriver(driverDetails: String, vehicleDetails: String, currentLocation: (Int, Int)) {
        let driverInfo = driverDetails.split(separator: ", ").map { String($0) }
        let name = driverInfo[0]
        let gender = driverInfo[1]
        let age = Int(driverInfo[2])!
        let vehicleInfo = vehicleDetails.split(separator: ", ").map { String($0) }
        let vehicle = vehicleInfo[0]
        let vehicleNumber = vehicleInfo[1]
        let driver = Driver(name: name, gender: gender, age: age, vehicle: vehicle, vehicleNumber: vehicleNumber, location: currentLocation)
        driverLock.lock()
        defer { driverLock.unlock() }
        drivers[name] = driver
        print("Driver \(name) added")
    }
    
    func updateDriverLocation(driverName: String, location: (Int, Int)) {
        driverLock.lock()
        defer { driverLock.unlock() }
        if let driver = drivers[driverName] {
            driver.updateDriverLocation(location: location)
            print("Driver \(driverName) location updated to \(location)")
        }
    }
    
    func changeDriverStatus(driverName: String, status: Bool) {
        driverLock.lock()
        defer { driverLock.unlock() }
        if let driver = drivers[driverName] {
            driver.changeDriverStatus(status: status)
            print("Driver \(driverName) status changed to \(status)")
        }
    }
    
    func findRide(username: String, source: (Int, Int), destination: (Int, Int)) -> [Driver]? {
        print("Finding ride for user \(username) from \(source) to \(destination)")
        userLock.lock()
        defer { userLock.unlock() }
        guard let user = users[username] else {
            print("User not found")
            return nil
        }
        
        var availableRides: [Driver] = []
        driverLock.lock()
        defer { driverLock.unlock() }
        for driver in drivers.values {
            if driver.status && calculateDistance(source: driver.location, destination: user.location) <= 5 {
                availableRides.append(driver)
            }
        }
        
        if !availableRides.isEmpty {
            print("Available rides: \(availableRides.map { $0.name })")
            return availableRides
        } else {
            print("No ride found")
            return nil
        }
    }
    
    func chooseRide(username: String, driverName: String) -> String {
        driverLock.lock()
        defer { driverLock.unlock() }
        guard let driver = drivers[driverName] else {
            return "Driver not found"
        }
        
        if !driver.status {
            return "Driver is not available"
        }
        return "Ride started"
    }
    
    func calculateBill(username: String, source: (Int, Int), destination: (Int, Int)) -> Int {
        print("Calculating bill for user \(username)")
        let distance = calculateDistance(source: source, destination: destination)
        let totalAmount = distance * 10
        print("Ride ended bill amount $\(totalAmount)")
        return totalAmount
    }
    
    func findTotalEarning() {
        print("Calculating total earnings")
        driverLock.lock()
        defer { driverLock.unlock() }
        for driver in drivers.values {
            print("\(driver.name) earned $\(driver.earning)")
        }
    }
    
    func processRideRequest(request: (String, (Int, Int), (Int, Int), String)) {
        let (username, source, destination, driverName) = request
        print("Processing ride request for \(username) with driver \(driverName)")
        let rideResult = chooseRide(username: username, driverName: driverName)
        if rideResult == "Ride started" {
            let bill = calculateBill(username: username, source: source, destination: destination)
            driverLock.lock()
            defer { driverLock.unlock() }
            if let driver = drivers[driverName] {
                driver.addEarning(amount: bill)
            }
            updateDriverLocation(driverName: driverName, location: destination)
            changeDriverStatus(driverName: driverName, status: false)
        }
        print("Ride request for \(username) processed")
        event.signal()
    }
    
    func rideRequestWorker() {
        print("Worker thread started")
        while running {
            rideRequestQueue.sync {
                // Simulate processing ride requests
                Thread.sleep(forTimeInterval: 1)
            }
        }
        print("Worker thread stopped")
    }
    
    func stopWorker() {
        print("Stopping worker thread")
        running = false
    }
    
    func requestRide(username: String, source: (Int, Int), destination: (Int, Int)) {
        print("User \(username) requested a ride from \(source) to \(destination)")
        if let availableRides = findRide(username: username, source: source, destination: destination) {
            let driver = availableRides[0]
            rideRequestQueue.async {
                self.processRideRequest(request: (username, source, destination, driver.name))
            }
            print("Ride request for \(username) added to queue")
        } else {
            print("No ride found")
        }
    }
    
    private func calculateDistance(source: (Int, Int), destination: (Int, Int)) -> Int {
        return abs(source.0 - destination.0) + abs(source.1 - destination.1)
    }
}

// Main function to demonstrate the functionality
func main() {
    let app = CabBookingApplication()
    
    // Onboard 3 users:
    app.addUser(userDetail: "Khanh L., M, 23")
    app.updateUserLocation(username: "Khanh L.", location: (0, 0))
    
    app.addUser(userDetail: "Thu Tr., F, 22")
    app.updateUserLocation(username: "Thu Tr.", location: (10, 0))
    
    app.addUser(userDetail: "Blue, M, 2")
    app.updateUserLocation(username: "Blue", location: (15, 6))
    
    // Onboard 3 drivers:
    app.addDriver(driverDetails: "Driver1, M, 22", vehicleDetails: "Swift, KA-01-12345", currentLocation: (10, 1))
    app.addDriver(driverDetails: "Driver2, M, 29", vehicleDetails: "Swift, KA-01-12345", currentLocation: (11, 10))
    app.addDriver(driverDetails: "Driver3, M, 24", vehicleDetails: "Swift, KA-01-12345", currentLocation: (5, 3))
    
    // Start ride request worker in a separate thread
    let rideWorkerThread = Thread {
        app.rideRequestWorker()
    }
    rideWorkerThread.start()
    
    // User tries to find a ride
    _ = app.findRide(username: "Khanh L.", source: (0, 0), destination: (20, 1)) // Ignoring the result
    _ = app.findRide(username: "Thu Tr.", source: (10, 0), destination: (15, 3)) // Ignoring the result
    
    // User chooses a ride
    app.requestRide(username: "Thu Tr.", source: (10, 0), destination: (15, 3))
    
    // Wait for the ride request to complete
    print("Waiting for ride request to complete")
    if app.event.wait(timeout: .now() + 5) == .timedOut {
        print("Ride request processing timed out")
    }
    
    // Update user and driver location and change driver status
    print("Updating user location")
    app.updateUserLocation(username: "Thu Tr.", location: (15, 3))
    
    print("Updating driver location")
    app.updateDriverLocation(driverName: "Driver1", location: (15, 3))
    
    print("Changing driver status")
    app.changeDriverStatus(driverName: "Driver1", status: false)
    
    // User tries to find a ride
    print("Finding ride for Blue")
    _ = app.findRide(username: "Blue", source: (15, 6), destination: (20, 4)) // Ignoring the result
    
    // Total earning by drivers
    print("Calculating total earnings")
    app.findTotalEarning()
    
    // Shutdown procedure
    print("Initiating shutdown")
    app.stopWorker()
    rideWorkerThread.cancel()
    print("Main thread finished")
}

main()