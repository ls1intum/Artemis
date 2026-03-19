# Demo Data Setup Guide — Master Thesis Presentation

## Overview

This script sets up realistic demo data for the master thesis presentation on **"Enhancing Navigation in a Learning Management System through Passkey Authentication and Semantic Search"**.

It creates three courses matching the storytelling in the presentation:

- **"Algorithms" (SS 2026)** — main course, richest content, search demo target
- **"Introduction to Programming" (SS 2026)** — additional course
- **"Data Structures" (WS 2025)** — additional course

The story:
- **Prof. Sofia** manages all three courses and demonstrates passkey authentication
- **Tom** is her colleague who fell for a phishing email — Sofia can't safely share her password with him
- **Luca** (student) emails Sofia about a "runtime complexity" exercise, but Sofia doesn't know which course it's in
- Sofia uses **global search** to instantly find the exercise across all courses

## What Gets Created

### Demo Characters

| Character   | Login        | Password       | Role               | Story Role                                    |
|-------------|--------------|----------------|--------------------|--------------------------------------------|
| Prof. Sofia | `prof_sofia` | `Password123!` | Admin + Instructor | Registers passkey, manages courses, searches  |
| Luca        | `luca`       | `Password123!` | Student            | Emails about "runtime complexity" exercise    |
| Tom         | `tom`        | `Password123!` | Admin + Instructor | Compromised colleague (phishing)              |

### Course 1: Algorithms (Main Course)

| Field      | Value                          |
|------------|--------------------------------|
| Title      | Algorithms                     |
| Short Name | ALG2026                        |
| Semester   | SS2026                         |
| Duration   | 30 days ago → 90 days from now |

#### Exercises

| Type               | Title                                        | Key for Demo?                  |
|--------------------|----------------------------------------------|-------------------------------|
| Programming (Java) | **Runtime Complexity Analysis**               | **Yes — search demo target**  |
| Programming (Java) | Graph Traversal Algorithms                   | Background content            |
| Programming (Java) | Sorting Algorithms                           | Background content            |
| Modeling           | UML Class Diagram: Sorting Algorithm Hierarchy | Background content          |
| Modeling           | Activity Diagram: Algorithm Complexity Comparison | Background content       |
| Text               | Essay: Comparing Sorting Algorithms          | Background content            |
| Text               | Summary: Runtime Complexity in Practice      | Background content            |
| Quiz               | Algorithm Fundamentals Quiz                  | Background content            |
| Quiz               | Sorting Algorithms Quiz                      | Background content            |

#### Lectures (with text units)

| Lecture                                | Text Units                                                                       | Key for Demo?                |
|----------------------------------------|----------------------------------------------------------------------------------|------------------------------|
| **Runtime Complexity and Big-O Notation** | Introduction to Runtime Complexity, Analyzing Algorithm Complexity, Practical Runtime Analysis | **Yes — search demo target** |
| Sorting Algorithms                     | Comparison-Based Sorting, Non-Comparison Sorting                                 | Background                   |
| Graph Algorithms                       | Graph Representations, Shortest Paths                                            | Background                   |

### Course 2: Introduction to Programming

| Field      | Value                          |
|------------|--------------------------------|
| Title      | Introduction to Programming    |
| Short Name | IntroProg2026                  |
| Semester   | SS2026                         |

Exercises: 3 (1 programming, 1 text, 1 modeling)
Lectures: 2
Students: 15

### Course 3: Data Structures

| Field      | Value                          |
|------------|--------------------------------|
| Title      | Data Structures                |
| Short Name | DS2026                         |
| Semester   | WS2025                         |

Exercises: 4 (2 programming incl. BST and Linked List, 1 text, 1 modeling)
Lectures: 3
Students: 20

### Students & Tutors (Main Course)
- **50 students** with realistic German names (`alg_student_1` through `alg_student_50`)
- **2 tutors** (`alg_tutor_1`, `alg_tutor_2`)
- All use password `Password123!`

### Communication
- 5 posts from Prof. Sofia, Luca, and Tom in the course channel (algorithms-themed)
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

### Story Context
Sofia recently joined a new research group. She helps out in three courses on Artemis: Algorithms, Introduction to Programming, and Data Structures. She also administrates Artemis for the chair.

As her paper deadline approaches, she wants to delegate admin tasks to Tom — but Tom has clicked on every phishing link the university has ever sent. She trusts Tom with the work, but not with a password that unlocks admin features.

A student (Luca) emails her about an exercise on runtime complexity. Since Sofia is new, she doesn't know which course the exercise belongs to and would have to search each course one by one.

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
2. Type **"runtime complexity"**
3. Results should show:
   - **Exercise**: "Runtime Complexity Analysis" (programming exercise)
   - **Lecture**: "Runtime Complexity and Big-O Notation" (with text units)

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
