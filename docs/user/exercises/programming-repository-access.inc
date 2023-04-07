The following tables lists the different types of repositories and gives an overview of the access rights different users have.
To gain these access rights, a user must assume the indicated role in the course the repository belongs to.

The different repository types are:
- **Base**: This includes all repositories that are set up when the exercise is created (template repository, solution repository, tests repository, auxiliary repositories).
- **Student Assignment**: A student's assignment repository copied from the template repository. This includes team assignment repositories.
- **Teaching Assistant (TA) Assignment**: An assignment repository created by a teaching assistant for themself.
- **Instructor Assignment**: An assignment repository created by an editor or instructor for themselves.
- **Student Practice**: A student's practice repository copied either from the template repository, or from their assignment repository. Can only be created after the due date of the exercise has passed.
- **Teaching Assistant (TA) Practice**: A practice repository created by a teaching assistant for themself.
- **Instructor Practice**: A practice repository created by an editor or instructor for themselves.

The different roles are:
- **Student (S)**: A student in the course.
- **Teaching Assistant (TA)**: A teaching assistant (tutor) in the course.
- **Editor**: An editor in the course.
- **Instructor (I)**: An instructor in the course.
Note: Editors and instructors are included in the role "Instructor" in the table as both roles have the same access rights.

The different points in time are:
- **Before start**: Before the exercise start date for a course exercise, before the exam start date for an exam exercise.
- **Working time**: After the exercise start date for a course exercise, after the exam release date for an exam exercise, before the due date for a course exercise, before the exam end date for an exam exercise.
- **After due**: After the due date for a course exercise, after the exam end date for an exam exercise.

Read access **(R)** includes `git fetch`, `git clone`, and `git pull`, if you are using your local Git client to access the repository.
Write access **(W)** corresponds to `git push` if you are using your local Git client to access the repository.

+--------------------------+------+---------------+--------+
| Repository type          | Role | Point in time | Access |
+==========================+======+===============+========+
| Base                     | S    | all           | -      |
+--------------------------+------+---------------+--------+
| Base                     | TA   | all           | -      |
+--------------------------+------+---------------+--------+
| Base                     | I    | all           | R/W    |
+--------------------------+------+---------------+--------+
| Student Assignment       | S    | Before start  | -      | 1)
+--------------------------+------+---------------+--------+
| Student Assignment       | S    | Working time  | R/W    |
+--------------------------+------+---------------+--------+
| Student Assignment       | S    | After due     | R      |
+--------------------------+------+---------------+--------+
| Student Assignment       | TA   | all           | R      |
+--------------------------+------+---------------+--------+
| Student Assignment       | I    | all           | R/W    |
+--------------------------+------+---------------+--------+
| TA Assignment            | S    | all           | -      |
+--------------------------+------+---------------+--------+
| TA Assignment            | TA   | Before start  | R      |
+--------------------------+------+---------------+--------+
| TA Assignment            | TA   | Working time  | R/W    |
+--------------------------+------+---------------+--------+
| TA Assignment            | TA   | After due     | R      |
+--------------------------+------+---------------+--------+
| TA Assignment            | I    | all           | R/W    |
+--------------------------+------+---------------+--------+
| Instructor Assignment    | S    | all           | -      |
+--------------------------+------+---------------+--------+
| Instructor Assignment    | TA   | all           | R      |
+--------------------------+------+---------------+--------+
| Instructor Assignment    | I    | all           | R/W    | 2)
+--------------------------+------+---------------+--------+
| Student Practice         | S    | Before start  | -      |
+--------------------------+------+---------------+--------+
| Student Practice         | S    | Working time  | -      |
+--------------------------+------+---------------+--------+
| Student Practice         | S    | After due     | R/W    |
+--------------------------+------+---------------+--------+
| Student Practice         | TA   | Before start  | -      |
+--------------------------+------+---------------+--------+
| Student Practice         | TA   | Working time  | -      |
+--------------------------+------+---------------+--------+
| Student Practice         | TA   | After due     | R      |
+--------------------------+------+---------------+--------+
| Student Practice         | I    | Before start  | -      |
+--------------------------+------+---------------+--------+
| Student Practice         | I    | Working time  | -      |
+--------------------------+------+---------------+--------+
| Student Practice         | I    | After due     | R/W    |
+--------------------------+------+---------------+--------+
| TA Practice              | S    | all           | -      |
+--------------------------+------+---------------+--------+
| TA Practice              | TA   | Before start  | -      |
+--------------------------+------+---------------+--------+
| TA Practice              | TA   | Working time  | -      |
+--------------------------+------+---------------+--------+
| TA Practice              | TA   | After due     | R/W    |
+--------------------------+------+---------------+--------+
| TA Practice              | I    | Before start  | -      |
+--------------------------+------+---------------+--------+
| TA Practice              | I    | Working time  | -      |
+--------------------------+------+---------------+--------+
| TA Practice              | I    | After due     | R/W    |
+--------------------------+------+---------------+--------+
| Instructor Practice      | S    | all           | -      |
+--------------------------+------+---------------+--------+
| Instructor Practice      | TA   | Before start  | -      |
+--------------------------+------+---------------+--------+
| Instructor Practice      | TA   | Working time  | -      |
+--------------------------+------+---------------+--------+
| Instructor Practice      | TA   | After due     | R      |
+--------------------------+------+---------------+--------+
| Instructor Practice      | I    | Before start  | -      |
+--------------------------+------+---------------+--------+
| Instructor Practice      | I    | Working time  | -      |
+--------------------------+------+---------------+--------+
| Instructor Practice      | I    | After due     | R/W    |
+--------------------------+------+---------------+--------+

1) If the exercise belongs to a course, it might happen that the instructor decides to add a start date after publishing the exercise. In this case, students can read the repository before the start date, if they had started the exercise earlier. In case the exercise is an exam exercise, students can never read the repository before the start date!

2) The instructor can access the Instructor Assignment repository using the online editor either from the *Edit in editor* view accessed via the Course Management (-> Exercises -> Edit in Editor)  or from the Course Overview (clicking on the course card -> Open code editor).
After the due date of the exercise has passed, the instructor can push to the repository only via the online editor reached from the Course Management or using their local Git client.
The online editor accessible from the Course Overview will show that the repository is locked, as it does for all students taking part in the course.

Note: The Practice repositories in the table above only exist for course exercises. Exam test run repositories are not part of the listing as they work differently.
- **Instructor Exam Test Run**: A test run repository created by an editor or instructor for an exam. An instructor can create an exam test run before the start date of the exam to allow the instructor to test the exam from a student perspective before releasing it.
Instructors assign a working time to the exam test run and can fetch and push to the repository during this time. After the working time is up, they can only fetch.
- **Student Exam Test Run**: A test run repository for a student for an exam.
- **TA Exam Test Run**: A test run repository for a teaching assistant for an exam.
