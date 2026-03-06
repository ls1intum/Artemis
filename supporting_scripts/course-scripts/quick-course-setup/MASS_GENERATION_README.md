# Mass Course Generation Script

This script generates a large number of courses (e.g., 2000) with a specified number of students registered to each course (e.g., 1500), where **each course has unique user groups**.

## Key Features

- **Reuses the same pool of students** across all courses
- **Each course gets unique user groups** (e.g., `course1-students`, `course2-students`, etc.)
- Efficient batch processing to avoid overwhelming the system
- Configurable via `mass_generation_config.ini`
- Progress tracking and error handling

## How It Works

1. **Step 1**: Creates a pool of N students once (e.g., 1500 students named `mass_student1`, `mass_student2`, etc.)
2. **Step 2**: Creates M courses (e.g., 2000 courses) with unique user groups for each
3. **Step 3**: Registers all students to each course with the course-specific group name

### Example

For 2 courses with 3 students:

**Students Created Once:**
- `mass_student1`
- `mass_student2`
- `mass_student3`

**Course 1:**
- Name: `MassGenCourse-1`
- Short Name: `mgc1`
- User Groups:
  - `course1-students` (contains mass_student1, mass_student2, mass_student3)
  - `course1-tutors`
  - `course1-editors`
  - `course1-instructors`

**Course 2:**
- Name: `MassGenCourse-2`
- Short Name: `mgc2`
- User Groups:
  - `course2-students` (contains mass_student1, mass_student2, mass_student3)
  - `course2-tutors`
  - `course2-editors`
  - `course2-instructors`

### Login Credentials

All generated students use their **username as their password** for simplicity:

| Username | Password | Email |
|----------|----------|-------|
| `mass_student1` | `mass_student1` | `mass_student1@example.com` |
| `mass_student2` | `mass_student2` | `mass_student2@example.com` |
| `mass_student3` | `mass_student3` | `mass_student3@example.com` |
| ... | ... | ... |

This makes it easy to log in as any test user for debugging or testing purposes.

## Configuration

### Option 1: Use mass_generation_config.ini (Recommended)

Edit `mass_generation_config.ini`:

```ini
[MassGeneration]
# Number of courses to create
courses = 2000

# Number of students per course (shared pool)
students_per_course = 1500

# Prefix for course names
course_name_prefix = MassGenCourse

# Short name prefix for courses
course_short_name_prefix = mgc

# NOTE: Student passwords are automatically set to match their usernames
# Example: username "mass_student1" will have password "mass_student1"

[Settings]
# Server connection
server_url = http://localhost:8080/api
client_url = http://localhost:8080/api

# Admin credentials
admin_user = artemis_admin
admin_password = artemis_admin

# Thread pool size (keep lower for mass generation)
max_threads = 4
```

### Option 2: Modify config.ini

The script also reads from the existing `config.ini` file as a fallback.

## Usage

### Prerequisites

1. Python 3.14 or higher
2. Virtual environment activated (see main README)
3. Dependencies installed: `pip install -r requirements.txt`
4. Local Artemis instance running
5. Admin access to the instance

### Running the Script

```bash
python3 mass_course_generation.py
```

### Expected Output

```
================================================================================
MASS COURSE GENERATION SCRIPT
================================================================================
Students to create (shared pool): 1500
Courses to create: 2000
Students per course: 1500
Total course registrations: 3000000
================================================================================
WARNING: This will create a MASSIVE amount of data!
Only proceed if you are running on a local test instance.
================================================================================
Admin authentication successful
Step 1: Creating student pool...
Creating 1500 students...
Successfully created/verified 1500 students
Student pool ready: 1500 students
Step 2: Creating courses and registering students...
Created course MassGenCourse-1 with shortName mgc1
Course 1 created with ID 123
Course 1: Added students 1 to 100
Course 1: Added students 101 to 200
...
Progress: 10/2000 courses completed
...
================================================================================
MASS COURSE GENERATION COMPLETE
================================================================================
Created 1500 students
Created 2000 courses
Total registrations: 3000000
================================================================================
```

## Performance Considerations

### Resource Usage

- **Students**: 1500 users created once
- **Courses**: 2000 courses with unique groups
- **Registrations**: 3,000,000 course-student relationships
- **Database impact**: Significant! Only run on local test instances

### Optimization Settings

The script uses several strategies to avoid overwhelming your system:

1. **Thread pool size**: Limited to 4-8 workers (configurable)
2. **Batch processing**: Students are added to courses in batches of 100
3. **Sequential course creation**: Courses are created one at a time
4. **Error handling**: Individual failures don't stop the entire process

### Estimated Runtime

- **Student creation**: ~5-10 minutes for 1500 students (depends on system)
- **Course creation + registration**: ~30-60 seconds per course
- **Total**: Approximately 16-33 hours for 2000 courses with 1500 students each

To speed this up, you could:
- Increase `max_threads` (if your system can handle it)
- Parallelize course creation (modify the main loop)
- Run on a more powerful machine

## Safety Features

The script includes several safety features:

1. **Test course marker**: All courses are marked as `testCourse: True`
2. **Duplicate handling**: Existing students/courses are skipped, not errored
3. **Batch processing**: Prevents overwhelming the database
4. **Progress logging**: Every 10 courses, progress is logged
5. **Error resilience**: Individual errors are logged but don't stop execution

## Cleanup

If you need to delete the generated data:

```bash
# Delete students (if implemented in delete_students.py)
python3 delete_students.py

# For courses, you'll need to use the Artemis admin interface or database
# as there's no bulk delete script for courses yet
```

## Troubleshooting

### "Could not create course" errors

- Check that course short names are unique and alphanumeric
- Verify admin credentials in config
- Ensure Artemis is running and accessible

### "Could not add user to course" errors

- Verify students were created successfully in Step 1
- Check that course groups are properly configured
- Review Artemis logs for server-side errors

### Performance issues

- Reduce `max_threads` to 2-4
- Reduce `students_per_course` for testing
- Check database connection limits
- Monitor system resources (CPU, memory, disk I/O)

## Differences from large_course_main.py

| Feature | large_course_main.py | mass_course_generation.py |
|---------|---------------------|---------------------------|
| Courses created | 1 | 2000 (configurable) |
| Students | Unique per course | Shared pool across courses |
| User groups | Standard names | Unique per course |
| Programming exercises | Yes | No (can be added) |
| Commits/participations | Yes | No |

## Warning

**This script creates a MASSIVE amount of data!**

- 1500 student users
- 2000 courses
- 3,000,000 course registrations

**Only run this on a local test instance!** Never run on production or shared test servers.

The generated data can significantly impact:
- Database size
- Application performance
- Memory usage
- Query response times

Always ensure you can restore your database if needed.
