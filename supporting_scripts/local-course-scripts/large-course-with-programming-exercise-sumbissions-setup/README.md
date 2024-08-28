# Artemis Large Course with Sumbissions on Programming Exercise Automation

This project contains Python scripts that automate the setup and management of courses and users in an Artemis instance. The scripts allow you to create a course, generate users, enroll them into courses, and manage programming exercises, including participation and commits. The project is useful for setting up test environments or quickly initializing a course with a large number of students.

## Setup

### 1. Install Python and the Python Plugin for IntelliJ
- Ensure that Python (preferably version 3.7 or 3.9) is installed on your system.
- Install the [Python Plugin for IntelliJ](https://plugins.jetbrains.com/plugin/631-python).
- Enable Python support in IntelliJ:
    - Go to `File > Project Structure > Facets > Add - Python`.
    - Add a Python environment by configuring the Python interpreter.
    - Add a module in IntelliJ by navigating to `File > Project Structure > Modules > Add - Python`.

### 2. Configure the Environment
- Start your local Artemis instance.
- Configure the `config.ini` file according to your local or test server setup. This includes settings like `server_url`, `client_url`, `admin_user`, `admin_password`, and course-specific configurations.
- Install the necessary Python packages using the following command (replace `<packageName>` with the actual package name):
  ```shell
  python3.9 -m pip install <packageName>

## Usage

### Running the Main Script

The main script orchestrates the entire process, from user creation to course setup, and finally user participation in programming exercises. To run the script:

#### 1.Open the project in IntelliJ.

#### 2.Locate the main.py file in the project directory.

#### 3.Run main.py:

-  You can use the play button within IntelliJ (if Python is configured properly) to run the script.

-  If you want to change the amount of students created, you can modify the `students` variable in the config.ini file.

-  If you want to change the amount of commits each student should perform in the example exercise, you can modify the `comments` variable in the config.ini file.

-  This will automatically perform all the necessary steps:

1.  Authenticate as admin.

2.  Create users.

3.  Create a course.

4.  Add users to the course.

5.  Create a programming exercise.

6.  Add participation and commit for each user.

### Optional: Deleting All Created Students

If you want to delete all the students created by the script, uncomment the relevant section in main.py that calls the delete_all_created_students() function and comment out steps 2 to 6, then re-run the script.

### Notes

-  Ensure that the config.ini file is correctly configured before running any scripts.

-  Always test the scripts on a local setup before running them on a production or test server.
