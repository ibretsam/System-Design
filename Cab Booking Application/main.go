package main

import (
	"fmt"
	"log"
	"math"
	"sync"
	"time"
)

type User struct {
	Name     string
	Gender   string
	Age      int
	Location [2]int
}

type Driver struct {
	Name          string
	Gender        string
	Age           int
	Vehicle       string
	VehicleNumber string
	Location      [2]int
	Status        bool
	Earning       int
	Lock          sync.Mutex
}

type CabBookingApplication struct {
	Users            map[string]*User
	Drivers          map[string]*Driver
	UserLock         sync.Mutex
	DriverLock       sync.RWMutex
	RideRequestQueue chan RideRequest
	Running          bool
	Event            chan bool
}

type RideRequest struct {
	Username    string
	Source      [2]int
	Destination [2]int
	DriverName  string
}

func NewCabBookingApplication() *CabBookingApplication {
	return &CabBookingApplication{
		Users:            make(map[string]*User),
		Drivers:          make(map[string]*Driver),
		RideRequestQueue: make(chan RideRequest, 10),
		Running:          true,
		Event:            make(chan bool),
	}
}

func (app *CabBookingApplication) AddUser(userDetail string) {
	var name, gender string
	var age int
	fmt.Sscanf(userDetail, "%s, %s, %d", &name, &gender, &age)
	user := &User{Name: name, Gender: gender, Age: age}
	app.UserLock.Lock()
	app.Users[name] = user
	app.UserLock.Unlock()
	log.Printf("User %s added", name)
}

func (app *CabBookingApplication) UpdateUserLocation(username string, location [2]int) {
	app.UserLock.Lock()
	if user, exists := app.Users[username]; exists {
		user.Location = location
		log.Printf("User %s location updated to %v", username, location)
	}
	app.UserLock.Unlock()
}

func (app *CabBookingApplication) AddDriver(driverDetails, vehicleDetails string, currentLocation [2]int) {
	var name, gender string
	var age int
	fmt.Sscanf(driverDetails, "%s, %s, %d", &name, &gender, &age)
	var vehicle, vehicleNumber string
	fmt.Sscanf(vehicleDetails, "%s, %s", &vehicle, &vehicleNumber)
	driver := &Driver{Name: name, Gender: gender, Age: age, Vehicle: vehicle, VehicleNumber: vehicleNumber, Location: currentLocation, Status: true}
	app.DriverLock.Lock()
	app.Drivers[name] = driver
	app.DriverLock.Unlock()
	log.Printf("Driver %s added", name)
}

func (app *CabBookingApplication) UpdateDriverLocation(driverName string, location [2]int) {
	app.DriverLock.Lock()
	if driver, exists := app.Drivers[driverName]; exists {
		driver.Lock.Lock()
		driver.Location = location
		driver.Lock.Unlock()
		log.Printf("Driver %s location updated to %v", driverName, location)
	}
	app.DriverLock.Unlock()
}

func (app *CabBookingApplication) ChangeDriverStatus(driverName string, status bool) {
	app.DriverLock.Lock()
	if driver, exists := app.Drivers[driverName]; exists {
		driver.Lock.Lock()
		driver.Status = status
		driver.Lock.Unlock()
		log.Printf("Driver %s status changed to %v", driverName, status)
	}
	app.DriverLock.Unlock()
}

func (app *CabBookingApplication) FindRide(username string, source, destination [2]int) []*Driver {
	log.Printf("Finding ride for user %s from %v to %v", username, source, destination)
	app.UserLock.Lock()
	user, exists := app.Users[username]
	app.UserLock.Unlock()
	if !exists {
		log.Println("User not found")
		return nil
	}

	var availableRides []*Driver
	app.DriverLock.RLock()
	for _, driver := range app.Drivers {
		driver.Lock.Lock()
		if driver.Status && calculateDistance(driver.Location, user.Location) <= 5 {
			availableRides = append(availableRides, driver)
		}
		driver.Lock.Unlock()
	}
	app.DriverLock.RUnlock()

	if len(availableRides) > 0 {
		log.Printf("Available rides: %v", availableRides)
		return availableRides
	} else {
		log.Println("No ride found")
		return nil
	}
}

func calculateDistance(source, destination [2]int) float64 {
	return math.Sqrt(math.Pow(float64(destination[0]-source[0]), 2) + math.Pow(float64(destination[1]-source[1]), 2))
}

func (app *CabBookingApplication) ChooseRide(username, driverName string) string {
	app.DriverLock.RLock()
	driver, exists := app.Drivers[driverName]
	app.DriverLock.RUnlock()
	if !exists {
		return "Driver not found"
	}

	driver.Lock.Lock()
	defer driver.Lock.Unlock()
	if !driver.Status {
		return "Driver is not available"
	}
	return "Ride started"
}

func (app *CabBookingApplication) CalculateBill(username string, source, destination [2]int) int {
	log.Printf("Calculating bill for user %s", username)
	distance := calculateDistance(source, destination)
	totalAmount := int(distance * 10)
	log.Printf("Ride ended bill amount Rs %d", totalAmount)
	return totalAmount
}

func (app *CabBookingApplication) FindTotalEarning() {
	log.Println("Calculating total earnings")
	app.DriverLock.RLock()
	for _, driver := range app.Drivers {
		driver.Lock.Lock()
		log.Printf("%s earned Rs %d", driver.Name, driver.Earning)
		driver.Lock.Unlock()
	}
	app.DriverLock.RUnlock()
}

func (app *CabBookingApplication) ProcessRideRequest(request RideRequest) {
	defer func() { app.Event <- true }()
	log.Printf("Processing ride request for %s with driver %s", request.Username, request.DriverName)
	rideResult := app.ChooseRide(request.Username, request.DriverName)
	if rideResult == "Ride started" {
		bill := app.CalculateBill(request.Username, request.Source, request.Destination)
		app.DriverLock.Lock()
		driver := app.Drivers[request.DriverName]
		driver.Lock.Lock()
		driver.Earning += bill
		driver.Lock.Unlock()
		app.DriverLock.Unlock()
		app.UpdateDriverLocation(request.DriverName, request.Destination)
		app.ChangeDriverStatus(request.DriverName, false)
	}
	log.Printf("Ride request for %s processed", request.Username)
}

func (app *CabBookingApplication) RideRequestWorker() {
	log.Println("Worker thread started")
	for app.Running {
		select {
		case request := <-app.RideRequestQueue:
			log.Printf("Worker processing request: %v", request)
			app.ProcessRideRequest(request)
		case <-time.After(1 * time.Second):
		}
	}
	log.Println("Worker thread stopped")
}

func (app *CabBookingApplication) StopWorker() {
	log.Println("Stopping worker thread")
	app.Running = false
	close(app.RideRequestQueue)
}

func (app *CabBookingApplication) RequestRide(username string, source, destination [2]int) {
	log.Printf("User %s requested a ride from %v to %v", username, source, destination)
	availableRides := app.FindRide(username, source, destination)
	if availableRides != nil {
		driver := availableRides[0]
		request := RideRequest{Username: username, Source: source, Destination: destination, DriverName: driver.Name}
		app.RideRequestQueue <- request
		log.Printf("Ride request for %s added to queue", username)
	} else {
		log.Println("No ride found")
	}
}

func main() {
	app := NewCabBookingApplication()

	// Onboard 3 users:
	app.AddUser("Khanh L., M, 23")
	app.UpdateUserLocation("Khanh L.", [2]int{0, 0})

	app.AddUser("Thu Tr., F, 22")
	app.UpdateUserLocation("Thu Tr.", [2]int{10, 0})

	app.AddUser("Blue, M, 2")
	app.UpdateUserLocation("Blue", [2]int{15, 6})

	// Onboard 3 drivers:
	app.AddDriver("Driver1, M, 22", "Swift, KA-01-12345", [2]int{10, 1})
	app.AddDriver("Driver2, M, 29", "Swift, KA-01-12345", [2]int{11, 10})
	app.AddDriver("Driver3, M, 24", "Swift, KA-01-12345", [2]int{5, 3})

	// Start ride request worker in a separate goroutine
	go app.RideRequestWorker()

	// User tries to find a ride
	app.FindRide("Khanh L.", [2]int{0, 0}, [2]int{20, 1})
	app.FindRide("Thu Tr.", [2]int{10, 0}, [2]int{15, 3})

	// User chooses a ride
	app.RequestRide("Thu Tr.", [2]int{10, 0}, [2]int{15, 3})

	// Wait for the ride request to complete
	log.Println("Waiting for ride request to complete")
	select {
	case <-app.Event:
	case <-time.After(5 * time.Second):
		log.Println("Ride request processing timed out")
	}

	// Update user and driver location and change driver status
	log.Println("Updating user location")
	app.UpdateUserLocation("Thu Tr.", [2]int{15, 3})

	log.Println("Updating driver location")
	app.UpdateDriverLocation("Driver1", [2]int{15, 3})

	log.Println("Changing driver status")
	app.ChangeDriverStatus("Driver1", false)

	// User tries to find a ride
	log.Println("Finding ride for Blue")
	app.FindRide("Blue", [2]int{15, 6}, [2]int{20, 4})

	// Total earning by drivers
	log.Println("Calculating total earnings")
	app.FindTotalEarning()

	// Shutdown procedure
	log.Println("Initiating shutdown")
	app.StopWorker()
	log.Println("Main thread finished")
}
