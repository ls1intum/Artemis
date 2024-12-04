Adaptive Learning (Student)
===========================

.. contents:: Content of this document
    :local:
    :depth: 2

.. _competencies_student:

Competencies
------------
Competencies allow students to understand the learning goals of the course and measure their progress toward achieving them.

Artemis measures two metrics for each competency: **progress** and **mastery**.
The progress starts at 0% and increases with every completed lecture unit and score achieved in exercises linked to the competency.
The mastery is a weighted metric of the student's progress. It can be influenced by different factors, e.g. if the latest exercise scores are higher or lower than the student's average score in the competency.
In Artemis, a competency is considered mastered by a student when the mastery is greater than or equal to the threshold set by the instructor.

In case competencies are defined, students can get an overview of their individual progress and confidence on the competencies tab.
The page lists all competencies with their title, description, and `taxonomy <https://en.wikipedia.org/wiki/Bloom%27s_taxonomy>`_.

Expanding the prerequisites section shows the student all competencies the instructor has selected as a prerequisite for this course.

|students-learning-goals-statistics|

When clicking on a competency, a page opens and displays detailed statistics about the competency together with all linked lecture units and exercises.
The two rings show the student's advancement:
The **green ring describes the progress**, the percentage of completed lecture units and achieved scores exercises.
The **red ring indicates the mastery**, which shows the overall advancement toward competency completion.

If the mastery diverges from the progress, the student can see the main reason for this divergence as a tooltip next to the mastery value.

|students-learning-goals-statistics-detail|

.. _learning_paths_student:

Learning Paths
--------------

Students can access their learning path in the learning path tab. Here, they can access recommended lecture units and participate in exercises.
Recommendations are generated via an intelligent agent that accounts for multiple metrics, e.g. prior performance, confidence, relations, and due dates, to support students in their selection of learning resources.
Students can use the "Previous" and "Next" buttons to navigate to the previous or next recommendation respectively.

|students-learning-path-participation|

Students can access all scheduled competencies and prerequisites by clicking on the title of the learning object they are currently viewing. Expanding a competency or prerequisite in the list reveals its associated learning objects, each indicating whether it has been completed.
To navigate to a specific learning object, students can simply click on its title.
For a broader view of how competencies and prerequisites are interconnected, students can open the course competency graph. This graph starts with competencies that have no prerequisites and progresses to those that build upon earlier knowledge. To aid navigation, a mini-map is available in the top-right corner.

|students-learning-path-graph|

.. |students-learning-goals-statistics| image:: student/students-learning-goals-statistics.png
    :width: 1000
.. |students-learning-goals-statistics-detail| image:: student/students-learning-goals-statistics-detail.png
    :width: 1000
.. |students-learning-path-participation| image:: student/students-learning-path-participation.png
    :width: 1000
.. |students-learning-path-graph| image:: student/students-learning-path-graph.png
    :width: 1000
