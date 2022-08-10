Grading
=======

Artemis allows instructors to define grading keys for courses and exams.
When the grading key is defined, the total points obtained by a student from all exercises are calculated and mapped to a grade.
Grading Keys can be either:

- ``Grade`` type which maps the points to a letter or numeric grade with a ``First Passing Grade``. This can be used e.g., for final exams or courses without exams that are graded solely by exercises.
- ``Bonus`` type which maps the points to a numeric value. This can be used e.g., when the grade obtained in the given course or exam is not an end result but complements a final exam.

Instructors
-----------
Instructors can export and import grading keys in CSV format to reuse them inside or outside of Artemis.
If a grading key is defined, exporting student results includes the obtained grade information as well.
It is also possible to create or modify grading keys after an exam or course is over.
In that case student grades will be adjusted automatically.

    .. note::
        You can check :ref:`how to create grading keys for courses and exams <exam_grading_key>` for more detailed information.

    .. figure:: grading/grade_key_bonus.png
       :alt: Course Grading Key with Bonus type
       :align: center

Students
--------
During a semester, students can see their grade based on attainable points for a course.
Attainable points are the sum of the maximum points for all exercises assessed so far, independent of a student's participation.
This means the students can track their relative performance during the semester without having to wait until all the exercises are conducted and assessed.

    .. note::
        The :ref:`Grades section <exam_grades>` has more detailed information on how to read the boundaries of the grading keys.

    .. figure:: grading/course_statistics_attainable.png
       :alt: Bonus Grade in Course Statistics Page
       :align: center
