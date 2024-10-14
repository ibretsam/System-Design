using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

public class User {
    public string Name { get; set; }
    public string Gender { get; set; }
    public int Age { get; set; }
    public int[] Location { get; set; }

    public User(string name, string gender, int age, int[] location = null)
    {
        Name = name;
        Gender = gender;
        Age = age;
        Location = location ?? new int[] { 0, 0 };
    }

    public void UpdateUserDetails(Dictionary<string, object> updatedDetails)
    {
        foreach (var detail in updatedDetails)
        {
            switch (detail.Key)
            {
                case "Name":
                    Name = detail.Value as string;
                    break;
                case "Gender":
                    Gender = detail.Value as string;
                    break;
                case "Age":
                    Age = Convert.ToInt32(detail.Value);
                    break;
                case "Location":
                    Location = detail.Value as int[];
                    break;
            }
        }
    }

    public void UpdateUserLocation(int[] location)
    {
        Location = location;
    }
}

public class Driver
{
    public string Name { get; set; }
    public string Gender { get; set; }
    public int Age { get; set; }
    public string Vehicle { get; set; }
    public string VehicleNumber { get; set; }
    public int[] Location { get; set; }
    public bool Status { get; set; }
    public int Earning { get; private set; }
    private readonly object lockObj = new object();

    public Driver(string name, string gender, int age, string vehicle, string vehicleNumber, int[] location, bool status = true)
    {
        Name = name;
        Gender = gender;
        Age = age;
        Vehicle = vehicle;
        VehicleNumber = vehicleNumber;
        Location = location;
        Status = status;
        Earning = 0;
    }

    public void UpdateDriverLocation(int[] location)
    {
        lock (lockObj)
        {
            Location = location;
        }
    }

    public void ChangeDriverStatus(bool status)
    {
        lock (lockObj)
        {
            Status = status;
        }
    }

    public void AddEarning(int amount)
    {
        lock (lockObj)
        {
            Earning += amount;
        }
    }
}

public class CabBookingApplication
{
    private readonly Dictionary<string, User> users = new Dictionary<string, User>();
    private readonly Dictionary<string, Driver> drivers = new Dictionary<string, Driver>();
    private readonly object userLock = new object();
    private readonly object driverLock = new object();
    private readonly Queue<(string, int[], int[], string)> rideRequestQueue = new Queue<(string, int[], int[], string)>();
    private bool running = true;
    public AutoResetEvent rideRequestEvent = new AutoResetEvent(false);

    public void AddUser(string userDetail)
    {
        var details = userDetail.Split(", ");
        var user = new User(details[0], details[1], int.Parse(details[2]));
        lock (userLock)
        {
            users[user.Name] = user;
        }
        Console.WriteLine($"User {user.Name} added");
    }

    public void UpdateUser(string username, Dictionary<string, object> updatedDetails)
    {
        lock (userLock)
        {
            if (users.ContainsKey(username))
            {
                users[username].UpdateUserDetails(updatedDetails);
                Console.WriteLine($"User {username} updated");
            }
        }
    }

    public void UpdateUserLocation(string username, int[] location)
    {
        lock (userLock)
        {
            if (users.ContainsKey(username))
            {
                users[username].UpdateUserLocation(location);
                Console.WriteLine($"User {username} location updated to {location[0]}, {location[1]}");
            }
        }
    }

    public void AddDriver(string driverDetails, string vehicleDetails, int[] currentLocation)
    {
        var driverInfo = driverDetails.Split(", ");
        var vehicleInfo = vehicleDetails.Split(", ");
        var driver = new Driver(driverInfo[0], driverInfo[1], int.Parse(driverInfo[2]), vehicleInfo[0], vehicleInfo[1], currentLocation);
        lock (driverLock)
        {
            drivers[driver.Name] = driver;
        }
        Console.WriteLine($"Driver {driver.Name} added");
    }

    public void UpdateDriverLocation(string driverName, int[] location)
    {
        lock (driverLock)
        {
            if (drivers.ContainsKey(driverName))
            {
                drivers[driverName].UpdateDriverLocation(location);
                Console.WriteLine($"Driver {driverName} location updated to {location[0]}, {location[1]}");
            }
        }
    }

    public void ChangeDriverStatus(string driverName, bool status)
    {
        lock (driverLock)
        {
            if (drivers.ContainsKey(driverName))
            {
                drivers[driverName].ChangeDriverStatus(status);
                Console.WriteLine($"Driver {driverName} status changed to {status}");
            }
        }
    }

    public List<Driver> FindRide(string username, int[] source, int[] destination)
    {
        Console.WriteLine($"Finding ride for user {username} from {source[0]}, {source[1]} to {destination[0]}, {destination[1]}");
        if (!users.ContainsKey(username))
        {
            Console.WriteLine("User not found");
            return null;
        }

        var user = users[username];
        var availableRides = new List<Driver>();

        lock (driverLock)
        {
            foreach (var driver in drivers.Values)
            {
                if (driver.Status && CalculateDistance(driver.Location, user.Location) <= 5)
                {
                    availableRides.Add(driver);
                }
            }
        }

        if (availableRides.Any())
        {
            Console.WriteLine($"Available rides: {string.Join(", ", availableRides.Select(d => d.Name))}");
            return availableRides;
        }
        else
        {
            Console.WriteLine("No ride found");
            return null;
        }
    }

    public string ChooseRide(string username, string driverName)
    {
        lock (driverLock)
        {
            if (drivers.ContainsKey(driverName))
            {
                var driver = drivers[driverName];
                if (!driver.Status)
                {
                    Console.WriteLine("Driver is not available");
                    return "Driver is not available";
                }
                else
                {
                    Console.WriteLine($"Ride started with driver {driverName}");
                    return "Ride started";
                }
            }
            else
            {
                Console.WriteLine("Driver not found");
                return "Driver not found";
            }
        }
    }

    public int CalculateBill(string username, int[] source, int[] destination)
    {
        Console.WriteLine($"Calculating bill for user {username}");
        var distance = CalculateDistance(source, destination);
        var totalAmount = distance * 10;
        Console.WriteLine($"Ride ended bill amount ${totalAmount}");
        return totalAmount;
    }

    public void FindTotalEarning()
    {
        Console.WriteLine("Calculating total earnings");
        lock (driverLock)
        {
            foreach (var driver in drivers.Values)
            {
                Console.WriteLine($"{driver.Name} earned ${driver.Earning}");
            }
        }
    }

    public void ProcessRideRequest(string username, int[] source, int[] destination, string driverName)
    {
        try
        {
            Console.WriteLine($"Processing ride request for {username} with driver {driverName}");
            var rideResult = ChooseRide(username, driverName);
            if (rideResult == "Ride started")
            {
                var bill = CalculateBill(username, source, destination);
                lock (driverLock)
                {
                    drivers[driverName].AddEarning(bill);
                }
                UpdateDriverLocation(driverName, destination);
                ChangeDriverStatus(driverName, false);
            }
            Console.WriteLine($"Ride request for {username} processed");
        }
        catch (Exception e)
        {
            Console.WriteLine($"Error processing ride request: {e.Message}");
        }
        finally
        {
            rideRequestEvent.Set();
        }
    }

    public void RideRequestWorker()
    {
        Console.WriteLine("Worker thread started");
        while (running)
        {
            (string username, int[] source, int[] destination, string driverName) request;
            lock (rideRequestQueue)
            {
                if (rideRequestQueue.Count == 0)
                {
                    Monitor.Wait(rideRequestQueue);
                }
                if (rideRequestQueue.Count > 0)
                {
                    request = rideRequestQueue.Dequeue();
                }
                else
                {
                    continue;
                }
            }
            ProcessRideRequest(request.username, request.source, request.destination, request.driverName);
        }
        Console.WriteLine("Worker thread stopped");
    }

    public void StopWorker()
    {
        Console.WriteLine("Stopping worker thread");
        running = false;
        lock (rideRequestQueue)
        {
            Monitor.PulseAll(rideRequestQueue);
        }
    }

    public void RequestRide(string username, int[] source, int[] destination)
    {
        Console.WriteLine($"User {username} requested a ride from {source[0]}, {source[1]} to {destination[0]}, {destination[1]}");
        var availableRides = FindRide(username, source, destination);
        if (availableRides != null && availableRides.Any())
        {
            var driver = availableRides[0];
            lock (rideRequestQueue)
            {
                rideRequestQueue.Enqueue((username, source, destination, driver.Name));
                Monitor.PulseAll(rideRequestQueue);
            }
            Console.WriteLine($"Ride request for {username} added to queue");
        }
        else
        {
            Console.WriteLine("No ride found");
        }
    }

    private static int CalculateDistance(int[] source, int[] destination)
    {
        return Math.Abs(source[0] - destination[0]) + Math.Abs(source[1] - destination[1]);
    }
}

public partial class Program
{
    public static void Main(string[] args)
    {
        var app = new CabBookingApplication();

        // Onboard 3 users:
        app.AddUser("Khanh L., M, 23");
        app.UpdateUserLocation("Khanh L.", new int[] { 0, 0 });

        app.AddUser("Thu Tr., F, 22");
        app.UpdateUserLocation("Thu Tr.", new int[] { 10, 0 });

        app.AddUser("Blue, M, 2");
        app.UpdateUserLocation("Blue", new int[] { 15, 6 });

        // Onboard 3 drivers:
        app.AddDriver("Driver1, M, 22", "Swift, KA-01-12345", new int[] { 10, 1 });
        app.AddDriver("Driver2, M, 29", "Swift, KA-01-12345", new int[] { 11, 10 });
        app.AddDriver("Driver3, M, 24", "Swift, KA-01-12345", new int[] { 5, 3 });

        // Start ride request worker in a separate thread
        var rideWorkerThread = new Thread(app.RideRequestWorker) { IsBackground = true };
        rideWorkerThread.Start();

        try
        {
            // User tries to find a ride
            app.FindRide("Khanh L.", new int[] { 0, 0 }, new int[] { 20, 1 });
            app.FindRide("Thu Tr.", new int[] { 10, 0 }, new int[] { 15, 3 });

            // User chooses a ride
            app.RequestRide("Thu Tr.", new int[] { 10, 0 }, new int[] { 15, 3 });

            // Wait for the ride request to complete
            Console.WriteLine("Waiting for ride request to complete");
            if (!app.rideRequestEvent.WaitOne(TimeSpan.FromSeconds(5)))
            {
                Console.WriteLine("Ride request processing timed out");
            }

            // Update user and driver location and change driver status
            Console.WriteLine("Updating user location");
            app.UpdateUserLocation("Thu Tr.", new int[] { 15, 3 });

            Console.WriteLine("Updating driver location");
            app.UpdateDriverLocation("Driver1", new int[] { 15, 3 });

            Console.WriteLine("Changing driver status");
            app.ChangeDriverStatus("Driver1", false);

            // User tries to find a ride
            Console.WriteLine("Finding ride for Blue");
            app.FindRide("Blue", new int[] { 15, 6 }, new int[] { 20, 4 });

            // Total earning by drivers
            Console.WriteLine("Calculating total earnings");
            app.FindTotalEarning();
        }
        catch (Exception e)
        {
            Console.WriteLine($"Error in main thread: {e.Message}");
        }
        finally
        {
            // Shutdown procedure
            Console.WriteLine("Initiating shutdown");
            app.StopWorker();
            rideWorkerThread.Join(TimeSpan.FromSeconds(5));
            if (rideWorkerThread.IsAlive)
            {
                Console.WriteLine("Worker thread did not stop gracefully. Forcing exit.");
            }
            Console.WriteLine("Main thread finished");
        }
    }
}