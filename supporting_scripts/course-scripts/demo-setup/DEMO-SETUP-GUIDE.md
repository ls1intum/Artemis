# Demo Data Setup Guide — Master Thesis Presentation

## Overview

This script sets up realistic demo data for the master thesis presentation on **"Enhancing Navigation in a Learning Management System through Passkey Authentication and Semantic Search"**.

It creates a fully populated **"Data Structures and Algorithms" (SS 2026)** course that matches the storytelling in the presentation, where:

- **Prof. Sofia** (admin/instructor) manages the course and demonstrates passkey authentication
- **Luca** (student) searches for "binary search trees" using global search
- **Tom** (instructor/admin) is the colleague who fell for a phishing email

## What Gets Created

### Course
| Field      | Value                          |
|------------|--------------------------------|
| Title      | Data Structures and Algorithms |
| Short Name | DSA2026                        |
| Semester   | SS2026                         |
| Duration   | 30 days ago → 90 days from now |

### Demo Characters

| Character   | Login        | Password       | Role               | Story Role                         |
|-------------|--------------|----------------|--------------------|------------------------------------|
| Prof. Sofia | `prof_sofia` | `Password123!` | Admin + Instructor | Registers passkey, manages course  |
| Luca        | `luca`       | `Password123!` | Student            | Searches for "binary search trees" |
| Tom         | `tom`        | `Password123!` | Admin + Instructor | Compromised colleague (phishing)   |

### Students & Tutors
- **50 students** with realistic German names (`dsa_student_1` through `dsa_student_50`)
- **2 tutors** (`dsa_tutor_1`, `dsa_tutor_2`)
- All students use password `Password123!`

### Exercises

| Type               | Title                                        | Key for Demo?                |
|--------------------|----------------------------------------------|------------------------------|
| Programming (Java) | **Binary Search Trees**                      | **Yes — search demo target** |
| Programming (Java) | Linked List Implementation                   | Background content           |
| Programming (Java) | Graph Traversal Algorithms                   | Background content           |
| Programming (Java) | Sorting Algorithms                           | Background content           |
| Modeling           | UML Class Diagram: Tree Structures           | Background content           |
| Modeling           | Activity Diagram: Search Algorithm Flow      | Background content           |
| Text               | Essay: Comparing Sorting Algorithms          | Background content           |
| Text               | Summary: Applications of Binary Search Trees | Background content           |
| Quiz               | Data Structures Fundamentals Quiz            | Background content           |
| Quiz               | Sorting Algorithms Quiz                      | Background content           |

### Lectures (with text units)

| Lecture                           | Text Units                                                                    | Key for Demo?                |
|-----------------------------------|-------------------------------------------------------------------------------|------------------------------|
| Introduction to Data Structures   | Arrays and Dynamic Arrays, Stacks and Queues                                  | Background                   |
| **Trees and Binary Search Trees** | Introduction to Trees, **Binary Search Trees (BST)**, AVL Trees and Rotations | **Yes — search demo target** |
| Sorting Algorithms                | Comparison-Based Sorting, Non-Comparison Sorting                              | Background                   |
| Graph Algorithms                  | Graph Representations, Shortest Paths                                         | Background                   |

### Additional Courses (Prof. Sofia as Instructor)

These give the demo a realistic feel — a professor managing multiple courses, not just one.

| Course                            | Short Name   | Semester | Exercises                             | Lectures | Students |
|-----------------------------------|--------------|----------|---------------------------------------|----------|----------|
| Introduction to Informatics       | IntroInf2026 | SS2026   | 3 (1 programming, 1 text, 1 modeling) | 2        | 15       |
| Software Engineering Fundamentals | SE2026       | WS2025   | 4 (1 programming, 1 text, 2 modeling) | 3        | 20       |

Both courses have Luca enrolled as a student and Tom as an instructor, matching the story.

### Communication
- 5 posts from Prof. Sofia, Luca, and Tom in the course channel (DSA-themed)
- 8 reply answers from various students
- Reactions from students on posts

## How to Run

### Prerequisites
1. Artemis server running locally (or accessible at a URL)
2. Node.js 18+ installed
3. Admin account (`artemis_admin` / `artemis_admin` by default)

### Run the Script

```bash
# From the repo root
cd supporting_scripts/course-scripts/demo-setup

# Default: connects to http://localhost:8080, creates 50 students
node demo-setup.mjs

# Custom server URL
node demo-setup.mjs --server-url=http://localhost:8080

# Custom admin credentials
node demo-setup.mjs --admin-user=artemis_admin --admin-password=artemis_admin

# Fewer students (faster setup)
node demo-setup.mjs --student-count=20

# Skip communication data (faster)
node demo-setup.mjs --skip-participations

# All options combined
node demo-setup.mjs \
  --server-url=http://localhost:8080 \
  --admin-user=artemis_admin \
  --admin-password=artemis_admin \
  --student-count=50
```

### Options

| Option                  | Default                 | Description                |
|-------------------------|-------------------------|----------------------------|
| `--server-url`          | `http://localhost:8080` | Artemis server URL         |
| `--admin-user`          | `artemis_admin`         | Admin username             |
| `--admin-password`      | `artemis_admin`         | Admin password             |
| `--student-count`       | `50`                    | Number of generic students |
| `--skip-participations` | `false`                 | Skip communication posts   |

Environment variables `ARTEMIS_SERVER_URL`, `ARTEMIS_ADMIN_USER`, and `ARTEMIS_ADMIN_PASSWORD` are also supported.

## Demo Flow (Presentation Order)

### 1. Passkey Registration (as Prof. Sofia)
1. Open Artemis, login as `prof_sofia` / `Password123!`
2. Go to account settings → register a passkey
3. Follow the browser prompt to create the passkey

### 2. Passkey Login
1. Log out
2. On the login page, the browser suggests the passkey
3. One touch → logged in

### 3. Locked Indicator (Privileged Admin Actions)
1. Login with **password only** (not passkey) as `prof_sofia`
2. Navigate to an admin action (e.g., course management)
3. Show the lock icon indicating the action requires a passkey session

### 4. Global Search
1. Press **Cmd+K** (or click the search icon)
2. Type **"binary search trees"**
3. Results should show:
   - **Exercise**: "Binary Search Trees" (programming exercise)
   - **Lecture**: "Trees and Binary Search Trees" (with BST text units)

## Which Course to Use for the Demo

Use the course **"Data Structures and Algorithms"** (`DSA2026`).

The course ID will be printed at the end of the script execution. You can also find it in the Artemis admin panel under Course Management.

## Re-running the Script

The script is **idempotent** for users — if a user already exists, it skips creation and reuses the existing account. However, exercises and lectures will be created as new entries each time. If you need to re-run:

1. Delete the course in Artemis admin panel
2. Run the script again

Or simply use the latest created course (it will have the most recent exercises).

## Troubleshooting

| Problem                             | Solution                                                      |
|-------------------------------------|---------------------------------------------------------------|
| `ECONNREFUSED`                      | Make sure Artemis is running (`./gradlew bootRun`)            |
| `Authentication failed`             | Check admin credentials                                       |
| `Course already exists`             | Script will try to find and reuse the existing course         |
| Programming exercise creation fails | The local CI setup (LocalCI) must be configured and running   |
| Quiz creation fails                 | Quiz exercises require specific API format; check server logs |
