.. _grading:

Grading
=======

Artemis allows instructors to define grading keys for courses and exams.
When the grading key is defined, the total points obtained by a student from all exercises are calculated and mapped to a grade.
Grading Keys can be either:

- ``Grade`` type which maps the points to a letter or numeric grade with a ``First Passing Grade``. This can be used e.g., for final exams or courses without exams that are graded solely by exercises.
- ``Bonus`` type which maps the points to a numeric value. This can be used e.g., when the grade obtained in the given course or exam is not an end result but complements a final exam.

There are two configurable special grades that are automatically treated as failing grades:

- ``Plagiarism Grade`` (default ``U``) is assigned when a student has received a `Plagiarism verdict <https://docs.artemis.cit.tum.de/Artemis/instructor/plagiarism-check#instructors>`_ in one or more exercises.
- ``No-participation Grade`` (default ``X``) is assigned according to the conditions below for courses and exams:

    - For a course, a student receives this grade if they do not start any exercise, i.e., the number of participations is 0.
    - For an exam, a student receives this grade if they do not submit the exam.

In bonus assignment calculations, these two special grades are treated equivalent as receiving a total score of 0 from the corresponding course or exam.

The ``Presentation Mode`` allows you to decide whether students can hold presentations in the course, e.g. to be eligible for an exam bonus.
Note that you can define for every exercise whether it should be eligible for the presentation score within the exercise settings. There are three different modes:

- ``None``: No presentation can be held.
- ``Basic``: Students must hold a given ``Number`` of presentations to be eligible for the exam bonus. The presentations are not graded.
- ``Graded``: Students can hold a given ``Number`` of presentations. The presentations are graded, and their ``Combined Weight`` makes up for the specified percentage of points in the course.

.. note::
    The ``Presentation Mode`` is only available for grading keys of courses but not for exams.

Instructors
-----------
Instructors can export and import grading keys in CSV format to reuse them inside or outside of Artemis.
If a grading key is defined, exporting student results includes the obtained grade information as well.
It is also possible to create or modify grading keys after an exam or course is over.
In that case student grades will be adjusted automatically.

    .. note::
        You can check the `how to create grading keys for courses and exams <https://docs.artemis.cit.tum.de/Artemis/instructor/exams/exam-timeline#36-grading-key>`_ for more detailed information.

    .. figure:: grading/grade_key_bonus.png
       :alt: Course Grading Key with Bonus type
       :align: center

Students
--------
During a semester, students can see their grade based on attainable points for a course.
Attainable points are the sum of the maximum points for all exercises assessed so far, independent of a student's participation.
This means the students can track their relative performance during the semester without having to wait until all the exercises are conducted and assessed.

    .. note::
        The `Grades section <https://docs.artemis.cit.tum.de/Artemis/student/exams#grades>`_ has more detailed information on how to read the boundaries of the grading keys.

    .. figure:: grading/course_statistics_attainable.png
       :alt: Bonus Grade in Course Statistics Page
       :align: center

Bonus
-----
Instructors can create bonus configurations for exams with ``Grade`` type grading keys by clicking on |bonus| on the exam detail page.
A bonus configuration maps the grade received from a bonus source, which can be a course or another exam, as an improvement to the final exam grade.
In order to configure a bonus, an instructor needs to choose appropriate values for the fields below:

1. **Bonus strategy** defines how the grade obtained from the bonus source will affect the final exam. Artemis currently supports 2 strategies:

  - *Grades*: First, calculates the target exam grade. Then, applies the bonus to that.
  - *Points*: First, applies the bonus to the student's points. Then, calculates the final grade by matching the resulting points to the target exam's grading key.

2. **Discreteness** (Only applicable if *grades* bonus strategy is selected) specifies how to combine the bonus grade with the exam grade. Currently only the first discreteness option is implemented.

  - *Continuous*: Applies bonus arithmetically to the student's grade. Final grade can be any numeric value between the best and the worst grade step values (e.g. from 1.3 to 1.2).
  - *Discrete*: (Not available yet) Bumps the student's grade to a better grade step. Final grade will be one of the grade steps in the target exam (e.g. from 1.3 to 1.0 or from B to A).

3. **Calculation** defines the sign of the operation to indicate if it is an addition or subtraction.

  - *−* (Default option for *grades*): Subtracts bonus from target exam's grades/points. Prefer this when lower means better.
  - *+* (Default option for *points*): Adds bonus to target exam's grades/points. Prefer this when higher means better.

4. **Bonus source** is the course or exam determining the bonus amount. When calculating the final grade for a student, the grade they received from the bonus source is substituted into the ``Bonus`` parameter in the formula explained below. The dropdown lists courses and exams with ``Bonus`` type grading keys if the current user is an instructor of it.

The bonus configuration page has a wizard mode where the options appear one by one initially to navigate the new users through the process easily.
When an instructor opens the bonus configuration page for an exam without a bonus, Artemis displays the options in wizard mode. Artemis shows the grade steps and max points of the selected grading key below the dropdown as a reminder to the instructor.

    .. figure:: grading/bonus_create_options.png
       :alt: Bonus Options in Create Mode
       :align: center

       Bonus Options in Create Mode

When the instructor is editing an already saved bonus configuration, Artemis hides the explanations inside the tooltip |bonus_tooltip| and only shows them on hover. Also, Artemis presents all options at once to provide a compact view that is quicker to navigate for the users who are already familiar with the bonus configuration.

    .. figure:: grading/bonus_edit_options.png
       :alt: Bonus Options in Edit Mode
       :align: center

       Bonus Options in Edit Mode

After the instructor chose values for all the fields above, Artemis generates the bonus calculation formula along with 5 examples to enable instructors to check the bonus configuration is correct before saving. Artemis tries to generate the examples using a heuristic with the following conditions:

- the exam points are in ascending order,
- the bonus source student points are in descending order,
- the first example shows that the bonus is not applied when the exam grade is a failing grade,
- the final example shows final grade cannot exceed the maximum grade.

    .. figure:: grading/bonus_formula_examples.png
       :alt: Formula and Static Examples
       :align: center

       Formula and Static Examples

The last row of examples enables instructors to type arbitrary exam points and bonus source student points to try out custom examples dynamically to test the bonus configuration manually.
Artemis calculates the resulting values in the example table when the instructor types the desired value in the corresponding number input field and then clicks outside of the current input.

    .. figure:: grading/bonus_dynamic_example.png
       :alt: Dynamic Example
       :align: center

       Dynamic Example

.. |bonus| image:: grading/bonus_button.png
.. |bonus_tooltip| image:: grading/bonus_tooltip.png
