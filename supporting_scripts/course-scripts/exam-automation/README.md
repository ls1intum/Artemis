# Artemis Exam Automation Scripts

This project contains Python scripts that automate the creation and management of programming exams in an Artemis instance. The scripts help you create exams, register students, set up programming exercises, and simulate student submissions for testing purposes.

## Overview

The exam automation scripts provide a complete workflow for setting up and running programming exams in Artemis. They are particularly useful for:
- Testing exam functionality in development environments
- Creating demo exams for presentations
- Automating exam setup for testing scenarios
- Simulating student behavior during exams

## Setup

### Prerequisites

- Python 3.8 or higher
- pip
- Running Artemis instance (local)
- Admin access to the Artemis instance

### 1. Create and activate a virtual environment (recommended)

```shell
# Create virtual environment
python3 -m venv venv

# Activate virtual environment
# On macOS/Linux:
source venv/bin/activate
# On Windows:
venv\Scripts\activate
```

### 2. Install required packages

```shell
pip install -r requirements.txt
```

### 3. Configure the environment

1. Start your local Artemis instance
2. Configure the `config.ini` file according to your setup (see Configuration section below)

## Configuration

The `config.ini` file contains all necessary configuration settings:

### Server Settings
```ini
[Settings]
admin_user = artemis_admin
admin_password = artemis_admin
server_url = http://localhost:8080/api
client_url = http://localhost:9000/api
```

### Exam Settings
```ini
[ExamSettings]
course_id = 1
exam_title = Local Programming Exam
programming_exercise_name = Exam Programming Exercise
number_of_correction_rounds = 1
```

**Important Notes:**
- Ensure the course ID exists and is accessible
- The course should have students enrolled (preferably created using the quick-course-setup scripts)

## Scripts Overview

**Note:** The exam automation scripts use shared utility functions from the `quick-course-setup` directory for user authentication and management.

### 1. `create_exam.py`
Creates a complete exam setup with programming exercises.

**What it does:**
- Creates an exam in the specified course
- Generates exam dates in the future
- Registers all course students for the exam
- Creates an exercise group
- Sets up a programming exercise within the exam
- Configures exam settings (working time, grace period, etc. wrt. now())

**What it cannot do:**
- Create students (registers existing course users to exam)
- Handle complex exam configurations beyond basic settings
- Create non-programming exercises

### 2. `exam_workflow.py`
Runs a complete exam simulation workflow.

**What it does:**
- Creates an exam using `create_exam.py`
- Generates student exams
- Prepares exercise start
- Starts the exam (sets start to 1 minute before current time)
- Simulates student submissions (creates participations and commits)
- Ends the exam (by setting the end time) and publishes results
- Uses threading for concurrent student submissions

**What it cannot do:**
- Provide real student interaction
- Handle complex or individual student submission scenarios
- Handle grading and plagiarism cases
- Manage exam security features

## Usage

### Basic Exam Creation

1. Ensure you have a course with students enrolled
2. Configure `config.ini` with your settings
3. Run the exam creation script:

```shell
python3 create_exam.py
```

This will create an exam and return the exam ID for further use.

### Complete Exam Workflow

To run the complete exam simulation:

```shell
python3 exam_workflow.py
```

This will:
1. Create an exam
2. Set up all necessary components
3. Simulate student submissions
4. End the exam and publish results

### Expected Output

The scripts provide detailed logging output showing:
- Exam creation progress
- Student registration status
- Exercise setup completion
- Submission simulation results
- Exam completion status


## Troubleshooting

### Common Issues

1. **Authentication failures**: Verify admin credentials in `config.ini`
2. **Course not found**: Ensure the course ID exists and is accessible
3. **No students enrolled**: Use quick-course-setup scripts to create students first
4. **Exam timing issues**: Check system time and timezone settings
5. **Submission failures**: Verify student credentials and exercise setup

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Ensure your Artemis instance is properly configured and able to conduct an exam manually 
3. Review Artemis documentation
4. Check the Artemis project issues
 