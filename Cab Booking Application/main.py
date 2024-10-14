import threading
from queue import Queue, Empty
import logging

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(threadName)s - %(levelname)s - %(message)s')

class User:
    def __init__(self, name, gender, age, location=(0, 0)) -> None:
        self.name = name
        self.gender = gender
        self.age = age
        self.location = location

    def update_userDetails(self, updated_details):
        for key, value in updated_details.items():
            setattr(self, key, value)

    def update_userLocation(self, location):
        self.location = location

class Driver:
    def __init__(self, name, gender, age, vehicle, vehicle_number, location, status=True) -> None:
        self.name = name
        self.gender = gender
        self.age = age
        self.vehicle = vehicle
        self.vehicle_number = vehicle_number
        self.location = location
        self.status = status
        self.lock = threading.Lock()
        self.earning = 0

    def update_driverLocation(self, location):
        with self.lock:
            self.location = location

    def change_driver_status(self, status):
        with self.lock:
            self.status = status
    
    def add_earning(self, amount):
        with self.lock:
            self.earning += amount

class CabBookingApplication:
    def __init__(self):
        self.users = {}
        self.drivers = {}
        self.user_lock = threading.Lock()
        self.driver_lock = threading.RLock()  # Changed to RLock
        self.ride_request_queue = Queue()
        self.running = True
        self.event = threading.Event()

    def add_user(self, user_detail):
        name, gender, age = user_detail.split(', ')
        user = User(name, gender, age)
        with self.user_lock:
            self.users[name] = user
        print(f"User {name} added")

    def update_user(self, username, updated_details):
        with self.user_lock:
            self.users[username].update_userDetails(updated_details)
        print(f"User {username} updated")

    def update_userLocation(self, username, location):
        with self.user_lock:
            self.users[username].update_userLocation(location)
        print(f"User {username} location updated to {location}")

    def add_driver(self, driver_details, vehicle_details, current_location):
        print(f"Adding driver {driver_details}")
        name, gender, age = driver_details.split(', ')
        vehicle, vehicle_number = vehicle_details.split(', ')
        driver = Driver(name, gender, age, vehicle, vehicle_number, current_location)
        with self.driver_lock:
            self.drivers[name] = driver
        print(f"Driver {name} added")

    def update_driverLocation(self, driver_name, location):
        logging.debug(f"Updating driver location: {driver_name} to {location}")
        lock_acquired = self.driver_lock.acquire(timeout=5)  # Wait up to 5 seconds to acquire the lock
        if lock_acquired:
            try:
                self.drivers[driver_name].update_driverLocation(location)
                logging.info(f"Driver {driver_name} location updated to {location}")
            finally:
                self.driver_lock.release()
        else:
            logging.error(f"Failed to acquire lock to update driver {driver_name} location")

    def change_driver_status(self, driver_name, status):
        logging.debug(f"Changing driver status: {driver_name} to {status}")
        lock_acquired = self.driver_lock.acquire(timeout=5)  # Wait up to 5 seconds to acquire the lock
        if lock_acquired:
            try:
                self.drivers[driver_name].change_driver_status(status)
                logging.info(f"Driver {driver_name} status changed to {status}")
            finally:
                self.driver_lock.release()
        else:
            logging.error(f"Failed to acquire lock to change driver {driver_name} status")

    def find_ride(self, username, source, destination):
        logging.debug(f"Finding ride for user {username} from {source} to {destination}")
        if username not in self.users:
            return "User not found"
        user = self.users[username]
        available_rides = []

        with self.driver_lock:
            for driver in self.drivers.values():
                if driver.status and self.calculate_distance(driver.location, user.location) <= 5:
                    available_rides.append(driver)
        
        if available_rides:
            print(f"Available rides: {[driver.name for driver in available_rides]}")
            return available_rides
        else:
            print("No ride found")
            return "No ride found"

    @staticmethod
    def calculate_distance(source, destination):
        return abs(source[0] - destination[0]) + abs(source[1] - destination[1])

    def choose_ride(self, username, drive_name):
        if drive_name in self.drivers:
            driver = self.drivers[drive_name]
            with self.driver_lock:
                if not driver.status:
                    print("Driver is not available")
                    return "Driver is not available"
                else:
                    print(f"Ride started with driver {drive_name}")
                    return "Ride started"

    def calculateBill(self, username, source, destination):
        print(f"Calculating bill for user {username}")
        distance = self.calculate_distance(source, destination)
        total_amount = distance * 10
        print(f"Ride ended bill amount Rs {total_amount}")
        return total_amount

    def find_total_earning(self):
        logging.debug("Calculating total earnings")
        with self.driver_lock:
            for driver in self.drivers.values():
                logging.info(f"{driver.name} earned Rs {driver.earning}")

    def process_ride_request(self, username, source, destination, driver_name):
        try:
            logging.info(f"Processing ride request for {username} with driver {driver_name}")
            ride_result = self.choose_ride(username, driver_name)
            if ride_result == "Ride started":
                bill = self.calculateBill(username, source, destination)
                
                # Acquire the lock for each operation separately
                with self.driver_lock:
                    self.drivers[driver_name].add_earning(bill)
                
                self.update_driverLocation(driver_name, destination)
                self.change_driver_status(driver_name, False)
                
            logging.info(f"Ride request for {username} processed")
        except Exception as e:
            logging.error(f"Error processing ride request: {str(e)}")
        finally:
            self.event.set() 

    def ride_request_worker(self):
        logging.info("Worker thread started")
        while self.running:
            try:
                request = self.ride_request_queue.get(timeout=1)
                logging.info(f"Worker processing request: {request}")
                self.process_ride_request(*request)
                self.ride_request_queue.task_done()
            except Empty:
                continue
            except Exception as e:
                logging.error(f"Error in worker thread: {str(e)}")
        logging.info("Worker thread stopped")

    def stop_worker(self):
        logging.info("Stopping worker thread")
        self.running = False

    def request_ride(self, username, source, destination):
        logging.info(f"User {username} requested a ride from {source} to {destination}")
        available_rides = self.find_ride(username, source, destination)
        if available_rides != "No ride found":
            driver = available_rides[0]
            self.ride_request_queue.put((username, source, destination, driver.name))
            logging.info(f"Ride request for {username} added to queue")
        else:
            logging.info("No ride found")


if __name__ == "__main__":
    app = CabBookingApplication()

    # Onboard 3 users:
    app.add_user("Khanh L., M, 23")
    app.update_userLocation("Khanh L.", (0, 0))

    app.add_user("Thu Tr., F, 22")
    app.update_userLocation("Thu Tr.", (10, 0))

    app.add_user("Blue, M, 2")
    app.update_userLocation("Blue", (15, 6))

    # Onboard 3 drivers:
    app.add_driver("Driver1, M, 22", "Swift, KA-01-12345", (10, 1))
    app.add_driver("Driver2, M, 29", "Swift, KA-01-12345", (11, 10))
    app.add_driver("Driver3, M, 24", "Swift, KA-01-12345", (5, 3))

    # Start ride request worker in a separate thread
    ride_worker_thread = threading.Thread(target=app.ride_request_worker, name="RideWorker")
    ride_worker_thread.daemon = True
    ride_worker_thread.start()

    try:
        # User tries to find a ride
        app.find_ride("Khanh L.", (0, 0), (20, 1))
        app.find_ride("Thu Tr.", (10, 0), (15, 3))

        # User chooses a ride    
        app.request_ride("Thu Tr.", (10, 0), (15, 3))

        # Wait for the ride request to complete
        logging.info("Waiting for ride request to complete")
        app.event.wait(timeout=5)  # Wait up to 5 seconds for the ride to complete
        
        if not app.event.is_set():
            logging.warning("Ride request processing timed out")

        # Update user and driver location and change driver status
        logging.info("Updating user location")
        app.update_userLocation("Thu Tr.", (15, 3))
        
        logging.info("Updating driver location")
        app.update_driverLocation("Driver1", (15, 3))
        
        logging.info("Changing driver status")
        app.change_driver_status("Driver1", False)

        # User tries to find a ride
        logging.info("Finding ride for Blue")
        app.find_ride("Blue", (15, 6), (20, 4))

        # Total earning by drivers
        logging.info("Calculating total earnings")
        app.find_total_earning()

    except Exception as e:
        logging.error(f"Error in main thread: {str(e)}", exc_info=True)
    finally:
        # Shutdown procedure
        logging.info("Initiating shutdown")
        app.stop_worker()
        ride_worker_thread.join(timeout=5)
        if ride_worker_thread.is_alive():
            logging.warning("Worker thread did not stop gracefully. Forcing exit.")
        logging.info("Main thread finished")