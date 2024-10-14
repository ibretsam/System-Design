import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.logging.*;

class User {
    private String name;
    private String gender;
    private int age;
    private int[] location;

    public User(String name, String gender, int age, int[] location) {
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.location = location;
    }

    public void updateUserDetails(Map<String, Object> updatedDetails) {
        updatedDetails.forEach((key, value) -> {
            switch (key) {
                case "name":
                    this.name = (String) value;
                    break;
                case "gender":
                    this.gender = (String) value;
                    break;
                case "age":
                    this.age = (int) value;
                    break;
                case "location":
                    this.location = (int[]) value;
                    break;
            }
        });
    }

    public void updateUserLocation(int[] location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public int[] getLocation() {
        return location;
    }
}

class Driver {
    private String name;
    private String gender;
    private int age;
    private String vehicle;
    private String vehicleNumber;
    private int[] location;
    private boolean status;
    private int earning;
    final Lock lock;

    public Driver(String name, String gender, int age, String vehicle, String vehicleNumber, int[] location) {
        this.name = name;
        this.gender = gender;
        this.age = age;
        this.vehicle = vehicle;
        this.vehicleNumber = vehicleNumber;
        this.location = location;
        this.status = true;
        this.earning = 0;
        this.lock = new ReentrantLock();
    }

    public void updateDriverLocation(int[] location) {
        lock.lock();
        try {
            this.location = location;
        } finally {
            lock.unlock();
        }
    }

    public void changeDriverStatus(boolean status) {
        lock.lock();
        try {
            this.status = status;
        } finally {
            lock.unlock();
        }
    }

    public void addEarning(int amount) {
        lock.lock();
        try {
            this.earning += amount;
        } finally {
            lock.unlock();
        }
    }

    public String getName() {
        return name;
    }

    public int[] getLocation() {
        return location;
    }

    public boolean isAvailable() {
        return status;
    }

    public int getEarning() {
        return earning;
    }
}

class CabBookingApplication {
    private final Map<String, User> users;
    private final Map<String, Driver> drivers;
    private final Lock userLock;
    private final ReentrantReadWriteLock driverLock;
    private final BlockingQueue<RideRequest> rideRequestQueue;
    private volatile boolean running;
    private final CountDownLatch event;
    private static final Logger logger = Logger.getLogger(CabBookingApplication.class.getName());

    public CabBookingApplication() {
        this.users = new ConcurrentHashMap<>();
        this.drivers = new ConcurrentHashMap<>();
        this.userLock = new ReentrantLock();
        this.driverLock = new ReentrantReadWriteLock();
        this.rideRequestQueue = new LinkedBlockingQueue<>();
        this.running = true;
        this.event = new CountDownLatch(1);
    }

    public void addUser(String userDetail) {
        String[] details = userDetail.split(", ");
        String name = details[0];
        String gender = details[1];
        int age = Integer.parseInt(details[2]);
        User user = new User(name, gender, age, new int[]{0, 0});
        userLock.lock();
        try {
            users.put(name, user);
        } finally {
            userLock.unlock();
        }
        logger.info("User " + name + " added");
    }

    public void updateUserLocation(String username, int[] location) {
        userLock.lock();
        try {
            User user = users.get(username);
            if (user != null) {
                user.updateUserLocation(location);
                logger.info("User " + username + " location updated to " + Arrays.toString(location));
            }
        } finally {
            userLock.unlock();
        }
    }

    public void addDriver(String driverDetails, String vehicleDetails, int[] currentLocation) {
        String[] driverInfo = driverDetails.split(", ");
        String name = driverInfo[0];
        String gender = driverInfo[1];
        int age = Integer.parseInt(driverInfo[2]);
        String[] vehicleInfo = vehicleDetails.split(", ");
        String vehicle = vehicleInfo[0];
        String vehicleNumber = vehicleInfo[1];
        Driver driver = new Driver(name, gender, age, vehicle, vehicleNumber, currentLocation);
        driverLock.writeLock().lock();
        try {
            drivers.put(name, driver);
        } finally {
            driverLock.writeLock().unlock();
        }
        logger.info("Driver " + name + " added");
    }

    public void updateDriverLocation(String driverName, int[] location) {
        logger.fine("Updating driver location: " + driverName + " to " + Arrays.toString(location));
        driverLock.writeLock().lock();
        try {
            Driver driver = drivers.get(driverName);
            if (driver != null) {
                driver.updateDriverLocation(location);
                logger.info("Driver " + driverName + " location updated to " + Arrays.toString(location));
            }
        } finally {
            driverLock.writeLock().unlock();
        }
    }

    public void changeDriverStatus(String driverName, boolean status) {
        logger.fine("Changing driver status: " + driverName + " to " + status);
        driverLock.writeLock().lock();
        try {
            Driver driver = drivers.get(driverName);
            if (driver != null) {
                driver.changeDriverStatus(status);
                logger.info("Driver " + driverName + " status changed to " + status);
            }
        } finally {
            driverLock.writeLock().unlock();
        }
    }

    public List<Driver> findRide(String username, int[] source, int[] destination) {
        logger.fine("Finding ride for user " + username + " from " + Arrays.toString(source) + " to " + Arrays.toString(destination));
        userLock.lock();
        User user;
        try {
            user = users.get(username);
        } finally {
            userLock.unlock();
        }
        if (user == null) {
            logger.warning("User not found");
            return Collections.emptyList();
        }

        List<Driver> availableRides = new ArrayList<>();
        driverLock.readLock().lock();
        try {
            for (Driver driver : drivers.values()) {
                if (driver.isAvailable() && calculateDistance(driver.getLocation(), user.getLocation()) <= 5) {
                    availableRides.add(driver);
                }
            }
        } finally {
            driverLock.readLock().unlock();
        }

        if (!availableRides.isEmpty()) {
            logger.info("Available rides: " + availableRides);
            return availableRides;
        } else {
            logger.info("No ride found");
            return Collections.emptyList();
        }
    }

    public static double calculateDistance(int[] source, int[] destination) {
        return Math.sqrt(Math.pow(destination[0] - source[0], 2) + Math.pow(destination[1] - source[1], 2));
    }

    public String chooseRide(String username, String driverName) {
        driverLock.readLock().lock();
        Driver driver;
        try {
            driver = drivers.get(driverName);
        } finally {
            driverLock.readLock().unlock();
        }
        if (driver == null) {
            return "Driver not found";
        }

        driver.lock.lock();
        try {
            if (!driver.isAvailable()) {
                return "Driver is not available";
            }
            return "Ride started";
        } finally {
            driver.lock.unlock();
        }
    }

    public int calculateBill(String username, int[] source, int[] destination) {
        logger.info("Calculating bill for user " + username);
        double distance = calculateDistance(source, destination);
        int totalAmount = (int) (distance * 10);
        logger.info("Ride ended bill amount $" + totalAmount);
        return totalAmount;
    }

    public void findTotalEarning() {
        logger.fine("Calculating total earnings");
        driverLock.readLock().lock();
        try {
            for (Driver driver : drivers.values()) {
                driver.lock.lock();
                try {
                    logger.info(driver.getName() + " earned $" + driver.getEarning());
                } finally {
                    driver.lock.unlock();
                }
            }
        } finally {
            driverLock.readLock().unlock();
        }
    }

    public void processRideRequest(RideRequest request) {
        try {
            logger.info("Processing ride request for " + request.getUsername() + " with driver " + request.getDriverName());
            String rideResult = chooseRide(request.getUsername(), request.getDriverName());
            if ("Ride started".equals(rideResult)) {
                int bill = calculateBill(request.getUsername(), request.getSource(), request.getDestination());
                driverLock.writeLock().lock();
                try {
                    Driver driver = drivers.get(request.getDriverName());
                    if (driver != null) {
                        driver.addEarning(bill);
                    }
                } finally {
                    driverLock.writeLock().unlock();
                }
                updateDriverLocation(request.getDriverName(), request.getDestination());
                changeDriverStatus(request.getDriverName(), false);
            }
            logger.info("Ride request for " + request.getUsername() + " processed");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing ride request", e);
        } finally {
            event.countDown();
        }
    }

    public void rideRequestWorker() {
        logger.info("Worker thread started");
        while (running) {
            try {
                RideRequest request = rideRequestQueue.poll(1, TimeUnit.SECONDS);
                if (request != null) {
                    logger.info("Worker processing request: " + request);
                    processRideRequest(request);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.SEVERE, "Worker thread interrupted", e);
            }
        }
        logger.info("Worker thread stopped");
    }

    public void stopWorker() {
        logger.info("Stopping worker thread");
        running = false;
    }

    public void requestRide(String username, int[] source, int[] destination) {
        logger.info("User " + username + " requested a ride from " + Arrays.toString(source) + " to " + Arrays.toString(destination));
        List<Driver> availableRides = findRide(username, source, destination);
        if (!availableRides.isEmpty()) {
            Driver driver = availableRides.get(0);
            rideRequestQueue.offer(new RideRequest(username, source, destination, driver.getName()));
            logger.info("Ride request for " + username + " added to queue");
        } else {
            logger.info("No ride found");
        }
    }

    public static void main(String[] args) {
        CabBookingApplication app = new CabBookingApplication();

        // Onboard 3 users:
        app.addUser("Khanh L., M, 23");
        app.updateUserLocation("Khanh L.", new int[]{0, 0});

        app.addUser("Thu Tr., F, 22");
        app.updateUserLocation("Thu Tr.", new int[]{10, 0});

        app.addUser("Blue, M, 2");
        app.updateUserLocation("Blue", new int[]{15, 6});

        // Onboard 3 drivers:
        app.addDriver("Driver1, M, 22", "Swift, KA-01-12345", new int[]{10, 1});
        app.addDriver("Driver2, M, 29", "Swift, KA-01-12345", new int[]{11, 10});
        app.addDriver("Driver3, M, 24", "Swift, KA-01-12345", new int[]{5, 3});

        // Start ride request worker in a separate thread
        Thread rideWorkerThread = new Thread(app::rideRequestWorker, "RideWorker");
        rideWorkerThread.setDaemon(true);
        rideWorkerThread.start();

        try {
            // User tries to find a ride
            app.findRide("Khanh L.", new int[]{0, 0}, new int[]{20, 1});
            app.findRide("Thu Tr.", new int[]{10, 0}, new int[]{15, 3});

            // User chooses a ride
            app.requestRide("Thu Tr.", new int[]{10, 0}, new int[]{15, 3});

            // Wait for the ride request to complete
            logger.info("Waiting for ride request to complete");
            if (!app.event.await(5, TimeUnit.SECONDS)) {
                logger.warning("Ride request processing timed out");
            }

            // Update user and driver location and change driver status
            logger.info("Updating user location");
            app.updateUserLocation("Thu Tr.", new int[]{15, 3});

            logger.info("Updating driver location");
            app.updateDriverLocation("Driver1", new int[]{15, 3});

            logger.info("Changing driver status");
            app.changeDriverStatus("Driver1", false);

            // User tries to find a ride
            logger.info("Finding ride for Blue");
            app.findRide("Blue", new int[]{15, 6}, new int[]{20, 4});

            // Total earning by drivers
            logger.info("Calculating total earnings");
            app.findTotalEarning();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Main thread interrupted", e);
        } finally {
            // Shutdown procedure
            logger.info("Initiating shutdown");
            app.stopWorker();
            try {
                rideWorkerThread.join(5000);
                if (rideWorkerThread.isAlive()) {
                    logger.warning("Worker thread did not stop gracefully. Forcing exit.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.log(Level.SEVERE, "Error stopping worker thread", e);
            }
            logger.info("Main thread finished");
        }
    }
}

class RideRequest {
    private final String username;
    private final int[] source;
    private final int[] destination;
    private final String driverName;

    public RideRequest(String username, int[] source, int[] destination, String driverName) {
        this.username = username;
        this.source = source;
        this.destination = destination;
        this.driverName = driverName;
    }

    public String getUsername() {
        return username;
    }

    public int[] getSource() {
        return source;
    }

    public int[] getDestination() {
        return destination;
    }

    public String getDriverName() {
        return driverName;
    }

    @Override
    public String toString() {
        return "RideRequest{" +
                "username='" + username + '\'' +
                ", source=" + Arrays.toString(source) +
                ", destination=" + Arrays.toString(destination) +
                ", driverName='" + driverName + '\'' +
                '}';
    }
}