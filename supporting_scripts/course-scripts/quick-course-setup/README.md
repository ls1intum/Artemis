# Artemis Course Setup Scripts

This project contains Python scripts that automate the setup and management of courses and users in an Artemis instance. The scripts help you create a course, generate users, enroll them into courses, and manage programming exercises, including participations and commits. The project is useful for setting up test environments or quickly initializing a course with a large number of students.

# Setup

## Prerequisites

- Python 3.13 or higher
- pip

## 1. Optionally, create and activate a virtual environment
It is recommended to use a virtual environment to manage dependencies in isolation from the global Python environment. This approach can prevent version conflicts and keep your system environment clean.

1. Install `virtualenv` if it's not already installed:
   - If you are using [brew](https://brew.sh/) on macOS, you can install `virtualenv` using:
      ```shell
      brew install virtualenv
      ```
   - Otherwise, you can use pip to install `virtualenv`:
      ```shell
      python3 -m pip install virtualenv
      ```

2. Create a virtual environment in your project folder:
    ```shell
    python3 -m venv venv
    ```

3. Activate the virtual environment:
   - On **macOS/Linux**:
     ```shell
     source venv/bin/activate
     ```
   - On **Windows**:
     ```shell
     venv\Scripts\activate
     ```

Once the virtual environment is activated, you will see the `(venv)` prefix in your terminal prompt. All dependencies will now be installed locally to this environment.

## 2. Install the Required Packages
```shell
pip install -r requirements.txt
```

## 3. Configure the Environment
1. Start your local Artemis instance.
2. Configure the [config.ini](./config.ini) file according to your local or test server setup.

# Usage

## Create Local Course and Users

These scripts help you configure and set up your first Artemis course quickly.

1. Start your local Artemis instance.
2. Configure the values in [config.ini](./config.ini) according to your setup.
3. Run the scripts of your choice _(see the shell commands provided below)_

**Note:**
1. Ensure that the [config.ini](./config.ini) file is correctly configured before running any scripts.
2. **Always test the scripts on a local setup before running them on a production or test server! ⚠️**

### Create Users
Creates users 1-20 (students, tutors, editors, instructors - 5 for each group).

```shell
python3 create_users.py
```

### Authenticate Users
If the users have already been created, they still need to be logged in order to be added to a course (without a first login, Artemis does not know that the users exist).

```shell  
python3 authenticate_all_users.py
```

### Creating a Course with Standard User Groups
Creates a course for which the users are registered according to their user groups (students, tutors, editors, instructors).

```shell
python3 create_course.py
```

### Test Servers
#### Create a Course with Default Users

1.	Adjust `server_url` and `client_url` according to the test server.
2.  Update `admin_user` and `admin_password` to valid values for the test server.
3.  Set `is_local_course` in [config.ini](./config.ini) to `False`.
4. Create a Course and Assign the Default Users

    | User Range | Role        |
    |------------|-------------|
    | 1-5        | Students    |
    | 6-10       | Tutors      |
    | 11-15      | Instructors |
    | 16-20      | Editors     |

5. Define the name of your course in the [config.ini](./config.ini) as `course_name`.
6. Run the script to create a new course with the default users:
    ```shell  
    python3 create_course.py
    ```

#### Add Users to Existing Course
1. Define the `course_id` in the [config.ini](./config.ini).
2. Run the script to add users to the existing course:
    ```shell  
    python3 add_users_to_course.py
    ```

## Artemis Large Course with Submissions on Programming Exercise Automation

This section details how to use the large_course_main script that orchestrates the entire process, from user creation to course setup, and finally user participation in programming exercises.

### Running the large_course_main Script

The large_course_main script performs all necessary steps to set up a large course with multiple students and submissions.

### Steps to Run:

1. Open the project in IntelliJ. 
2. Locate the large_course_main.py file in the project directory.
3. Update the [config.ini](./config.ini) to your needs
 
   | Variable           | Description                                                                              |
   |--------------------|------------------------------------------------------------------------------------------|
   | `students`         | Number of students to be created                                                         |
   | `commits`          | Number of commits each student should perform in the example exercise                    |
   | `exercises`        | Number of programming exercises to be created                                            |
   | `exercise_name`    | Name of the programming exercises to be created                                          |
   | `create_course`    | Set to `False` to use an existing course and provide a valid `course_id`.                |
   | `create_exercises` | Set to `False` to use an existing programming exercise and provide valid `exercise_Ids`. |
4. Update the shared [config.ini](../config.ini) to your needs
   
   You will need to update values here if you want to run the script against a test server.

   **IMPORTANT:** Do not create users with weak passwords on production or test servers. Ensure that the `student_password_base` follows the password policies of the server.

   | Variable                | Description                                                                    |
   |-------------------------|--------------------------------------------------------------------------------|
   | `admin_user`            |                                                                                |
   | `admin_password`        |                                                                                |
   | `student_password_base` | According to the passwords used on the system                                  |
   | `student_group_name`    | Student group name of the course for which the script is run                   |
   | `server_url`            | Of the server against which the script shall be run                            |
   | `client_url`            | Of the client against the script shall be run (usually the same as server url) |
5. You can use the play button within IntelliJ (if Python is configured properly) to run the script.
```shell
python3 large_course_main.py
```

The script will automatically perform all the necessary steps:

1. Authenticate as admin. 
2. Create students.
3. Create a course or use an existing one.
4. Add students to the course. 
5. Create a programming exercise or use an existing one. 
6. Add participation and commit for each student.

### Optional: Generating Different Results For All Created Students (Should only be done Locally!!)

If you want to generate different results for all the students created by the script:

1. Run the following Script which will navigate to the [testFiles](../../../src/main/resources/templates/java/test/testFiles) folder and copy the [RandomizedTestCases](./testFiles-template/randomized/RandomizedTestCases.java) file into it.
   It will delete the existing folders (behavior and structural) from the programming exercise’s test case template. The new test cases will randomly pass or fail, causing different results for each student.
    ```shell
    python3 randomize_results_before.py
    ```
2. Rebuild Artemis to apply the changes.
3. Run the main method in large_course_main.py. Now, all created students should have varying results in the programming exercise.
    ```shell
    python3 large_course_main.py
    ```
4. Make sure to revert these changes after running the script. The following script copies the original test case files from the [default](./testFiles-template/default) folder back into the [testFiles](../../../src/main/resources/templates/java/test/testFiles) folder and deletes the [RandomizedTestCases](./testFiles-template/randomized/RandomizedTestCases.java) file that was copied to [testFiles](../../../src/main/resources/templates/java/test/testFiles) in Step 1.
   If you don't run this script after running the script in Step 1, you risk breaking the real template of the programming exercise if these changes are pushed and merged.
    ```shell
    python3 randomize_results_after.py
    ```

### Optional: Using an Existing Programming Exercise (Can also be done on Test Server)
Alternatively, you can use an existing programming exercise and push the [RandomizedTestCases](./testFiles-template/randomized/RandomizedTestCases.java) file to the test repository of the programming exercise. 
Make sure to adjust the [config.ini](./config.ini) file to use the existing programming exercise with the corresponding exercise ID, allowing the script to push with the created students to this existing programming exercise.

### Optional: Deleting All Created Students

If you want to delete all the students created by the script:
```shell
python3 delete_students.py
```

### Dependency management

Find outdated dependencies using the following command:
```shell
pip list --outdated
```

Find unused dependencies using the following command:
```shell
pip install deptry
deptry .
```
