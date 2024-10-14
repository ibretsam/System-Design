# Cab Booking Application

## Description:
Implement a cab booking application. Below are the expected features from the system.

## Features:
- The application allows users to book rides on a route.
- Users can register themselves and make changes to their details.
- Driving partners can onboard the system with vehicle details.
- Users can search and select one from multiple available rides on a route with the same source and destination based on the nearest to the user.

## Requirements:

### 1. Application should allow user onboarding:
1. **add_user(user_detail):**
    - Add basic user details.

2. **update_user(username, updated_details):**
    - User should be able to update their contact details.

3. **update_userLocation(username, location):**
    - This will update the user location in X, Y coordinates to find the nearest in the future.

### 2. Application should allow Driver onboarding:
1. **add_driver(driver_details, vehicle_details, current_location):**
    - This will create an instance of the driver and will mark their current location on the map.

2. **update_driverLocation(driver_name, location):**
    - This will mark the current location of the driver.

3. **change_driver_status(driver_name, status):**
    - The driver can make themselves either available or unavailable via a boolean.

### 3. Application should allow the user to find a ride based on the criteria below:
1. **find_ride(username, source, destination):**
    - It will return a list of available rides.

2. **choose_ride(username, driver_name):**
    - It will choose the driver name from the list.
    - **Note:** Only the driver which is at a max distance of 5 units will be displayed to a user and the driver should be in an available state to confirm the booking.

3. **calculateBill(username):**
    - It will return the bill based on the distance between the source and destination and will display it.

4. **find_total_earning():**
    - Application should at the end calculate the earnings of all the drivers onboarded in the application.

## Other Notes:
1. Write a driver class for demo purposes. This will execute all the commands in one place in the code and have test cases.
2. Do not use any database or NoSQL store, use in-memory data-structure for now.
3. Do not create any UI for the application.
4. Please prioritize code compilation, execution, and completion.
5. Work on the expected output first and then add bonus features of your own.

## Expectations:
1. Make sure that you have a working and demo-able code.
2. Make sure that code is functionally correct.
3. Use of proper abstraction, entity modeling, separation of concerns is good to have.
4. Code should be modular, readable, and unit-testable.
5. Code should easily accommodate new requirements with minimal changes.
6. Proper exception handling is required.
7. **Concurrency Handling (BONUS)** - Optional

## Sample Test Cases:

### 1. Onboard 3 users:
- `add_user("Khanh L., M, 23"); update_userLocation("Khanh L.", (0, 0))`
- `add_user("Thu Tr., F, 22"); update_userLocation("Thu Tr.", (10, 0))`
- `add_user("Blue, M, 2"); update_userLocation("Blue", (15, 6))`

### 2. Onboard 3 drivers to the application:
- `add_driver("Driver1, M, 22", "Swift, KA-01-12345", (10, 1))`
- `add_driver("Driver2, M, 29", "Swift, KA-01-12345", (11, 10))`
- `add_driver("Driver3, M, 24", "Swift, KA-01-12345", (5, 3))`

### 3. User trying to get a ride:
- `find_ride("Khanh L.", (0, 0), (20, 1))`
  - **Output:** No ride found [Since all the drivers are more than 5 units away from the user]

- `find_ride("Thu Tr.", (10, 0), (15, 3))`
  - **Output:** Driver1 [Available]
  - `choose_ride("Thu Tr.", "Driver1")`
    - **Output:** Ride Started
  - `calculateBill("Thu Tr.")`
    - **Output:** Ride Ended bill amount Rs 60
  - Backend API Call:
    - `update_userLocation("Thu Tr.", (15, 3))`
    - `update_driverLocation("Driver1", (15, 3))`
    - `change_driver_status("Driver1", False)`

- `find_ride("Blue", (15, 6), (20, 4))`
  - **Output:** No ride found [Driver one is set to not available]

### 4. Total earning by drivers:
- `find_total_earning()`
  - **Output:**
    - Driver1 earned Rs 60
    - Driver2 earned Rs 0
    - Driver3 earned Rs 0