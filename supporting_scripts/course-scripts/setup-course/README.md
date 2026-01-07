# Artemis Course Setup Script

This script creates a comprehensive test course with various content types for testing Artemis functionality.

## What It Creates

### Course Exercises
- **5 Programming Exercises**: Java/Gradle, Python, C/GCC, JavaScript, Java/Maven with different configurations
- **2 Modeling Exercises**: UML Class Diagram, Activity Diagram
- **2 Text Exercises**: Essay and Summary assignments
- **2 Quiz Exercises**: Multiple choice, short answer, and drag-and-drop questions
- **1 File Upload Exercise**: Document submission

### Exam
- **1 Real Exam** with:
  - 90-minute working time with 3-minute grace period
  - 2 exercise groups (mandatory)
  - **Programming Exercise**: Java Algorithms (50 points, automatic assessment)
  - **Quiz Exercise**: Java Knowledge Quiz with 3 multiple-choice questions (50 points)
  - All students registered and student exams generated
  - Exercise start prepared (participations created)

### Lectures
- 4 lectures with different content types:
  - Text units (markdown content)
  - Exercise units (linked exercises)
  - Online units (external resources)
  - Attachment units (PDF files)

### Competencies & Prerequisites
- 2 prerequisites (Basic Programming, Mathematics Fundamentals)
- 4 competencies linked to lectures and exercises

### Tutorial Groups
- Tutorial groups configuration with period settings
- 3 tutorial groups (2 in-person, 1 online)
- Recurring sessions for each group
- Students distributed among groups

### FAQs
- 8 comprehensive FAQs covering common student questions

### Communication
- Posts in course channels with various topics (welcome, study groups, resources, tips)
- Answer posts (replies) to existing posts
- Emoji reactions (thumbsup, heart, rocket, fire, etc.) on posts and answers
- Forwarded messages between posts
- Saved posts (bookmarks) for students

### Users & Participations
- Configurable number of students and tutors
- One editor and one instructor for testing privileged functionality
- Students participate in exercises and submit solutions
- Automatic assessment for programming and quiz exercises
- Manual assessment by tutors for modeling and text exercises (due dates updated automatically)

## Prerequisites

- Node.js 18+ (uses native fetch)
- Admin credentials for the Artemis server
- **Artemis server must be running** before executing this script

### Starting the Server

Before running the setup script, start the Artemis server in a separate terminal:

```bash
# Start the full Artemis application (includes Angular build)
./gradlew bootRun

# OR for faster development: start server only (without Angular)
./gradlew bootRun -x webapp

# Then in another terminal, start the Angular dev server:
npm start
```

Wait for the server to fully start (you'll see "Started ArtemisApp" in the logs) before running the setup script.

## Usage

```bash
# Basic usage with defaults
npm run setupCourse

# With custom student count
npm run setupCourse -- --student-count=10

# Using an existing course
npm run setupCourse -- --course-id=123

# Skip participations (faster, just creates content)
npm run setupCourse -- --skip-participations

# Custom server
npm run setupCourse -- --server-url=https://artemis.example.com

# Full example
npm run setupCourse -- \
  --server-url=http://localhost:8080 \
  --admin-user=artemis_admin \
  --admin-password=artemis_admin \
  --student-count=10
```

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `--server-url=<url>` | Artemis server URL | `http://localhost:8080` |
| `--admin-user=<user>` | Admin username | `artemis_admin` |
| `--admin-password=<pass>` | Admin password | `artemis_admin` |
| `--student-password=<pass>` | Password for created students/tutors | `Password123!` |
| `--student-count=<n>` | Number of students to create | `5` |
| `--course-id=<id>` | Use existing course instead of creating new | - |
| `--skip-participations` | Skip student participations and assessments | `false` |
| `--help` | Show help message | - |

## Environment Variables

You can also configure the script using environment variables:

```bash
export ARTEMIS_SERVER_URL=http://localhost:8080
export ARTEMIS_ADMIN_USER=artemis_admin
export ARTEMIS_ADMIN_PASSWORD=artemis_admin
export ARTEMIS_STUDENT_PASSWORD=Password123!
npm run setupCourse
```

## Output

The script provides progress output as it creates content:

```
============================================================
Artemis Course Setup Script
============================================================
Server URL: http://localhost:8080
Admin User: artemis_admin
Student Count: 5
Skip Participations: false
============================================================

[1/13] Authenticating as admin...
  Authenticated as artemis_admin
[2/13] Creating course...
  Created course: Test Course 2024-01-15 (ID: 42)
    Student group: artemis-test1234-students
    Tutor group: artemis-test1234-tutors
    Editor group: artemis-test1234-editors
    Instructor group: artemis-test1234-instructors
[3/13] Creating users...
  Created 5 students, 2 tutors, 1 editor, 1 instructor
[4/13] Creating programming exercises...
    Created: Java Sorting Algorithm (JAVA)
    Created: Python Data Analysis (PYTHON)
    ...
[5/13] Creating other exercises...
    Created: UML Class Diagram
    Created: Activity Diagram
    ...
[6/13] Creating exam...
  Creating exam...
    Created exam: Programming Exam 2024-01-15 (ID: 1)
  Creating exercise groups and exercises...
    Created exercise group: Programming Exercise
    Created programming exercise: Exam - Java Algorithms
    Created exercise group: Quiz Exercise
    Created quiz exercise: Exam - Java Knowledge Quiz
  Registering students...
    Registered 5 students for exam
  Generating student exams...
    Generated 5 student exams
  Preparing exercise start...
    Exercise start preparation initiated
[7/13] Creating lectures...
...
[8/13] Creating competencies and prerequisites...
...
[9/13] Creating tutorial groups...
...
[10/13] Creating FAQs...
...
[11/13] Creating communication data...
  Getting course channels...
  Using channel: announcement
  Creating posts...
    student_1: Created post "Welcome Discussion"
    student_2: Created post "Study Group Formation"
    ...
  Creating answer posts...
    student_1: Replied to "Welcome to the course..."
    ...
  Adding reactions...
    Added 15 reactions
  Creating forwarded messages...
    student_1: Forwarded message created
  Saving posts...
    student_1: Saved post
  Created 5 posts, 10 answers, 15 reactions
  Created 2 forwarded messages, 3 saved posts
[12/13] Creating student participations...
...
[13/13] Creating assessments...
    Updated due date for: UML Class Diagram
    Updated due date for: Activity Diagram
    tutor_1: Assessed 5 submissions for UML Class Diagram
    ...
============================================================
Course setup completed successfully!
============================================================
```

## Troubleshooting

### Cannot connect / ECONNREFUSED
- The Artemis server is not running
- Start it with `./gradlew bootRun` and wait for "Started ArtemisApp"

### HTTP 404: Authentication endpoint not found
- The server may still be starting up - wait and try again
- Verify the server URL is correct (default: `http://localhost:8080`)
- Check that the server is running with the full profile (not just a subset)

### Authentication Failed
- Verify admin credentials are correct
- Default credentials are `artemis_admin` / `artemis_admin`
- Ensure the server is running and accessible

### Exercise Creation Failed
- Check if the programming language is enabled on the server
- Verify CI/CD system (LocalCI) is properly configured

### Quiz Creation Failed
- Ensure the course has the quiz feature enabled

### Participation Failed
- Students need proper group membership
- Exercise must be released and not past due date

### Exam Creation Failed
- Ensure the user has instructor permissions on the course
- Check that exercise groups are created before adding exercises
- Verify programming language support for exam programming exercises

### Assessment Failed
- Due dates must be in the past for manual assessment
- The script automatically updates due dates before creating assessments
- Tutors must be properly assigned to the course

### Communication Failed
- Ensure the course has communication enabled (`courseInformationSharingConfiguration`)
- Check that there are channels available in the course
- Students must be enrolled in the course to post messages

## File Structure

```
setup-course/
├── setup-course.mjs      # Main entry point
├── README.md             # This file
└── lib/
    ├── auth.mjs          # Shared authentication utilities
    ├── cli.mjs           # CLI argument parsing
    ├── http-client.mjs   # HTTP client with cookie/CSRF handling
    ├── course-setup.mjs  # Main orchestration (13 steps)
    ├── exercises.mjs     # Course exercise creation
    ├── exams.mjs         # Exam creation with exercise groups
    ├── lectures.mjs      # Lecture creation
    ├── competencies.mjs  # Competencies and prerequisites
    ├── tutorial-groups.mjs # Tutorial groups
    ├── faqs.mjs          # FAQ creation
    ├── communication.mjs # Posts, answers, reactions, forwarding, saved posts
    ├── participations.mjs # Student participations and submissions
    └── assessments.mjs   # Manual assessment creation
```
