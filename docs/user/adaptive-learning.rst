.. _adaptive-learning:

Adaptive Learning
=================

Adaptive Learning in Artemis is centered around **competencies**, overarching learning objectives that tie together various lectures and exercises. Artemis is also able to provide students with individualized **learning paths** based on competencies and their relations.

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/26313?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Watch this video on TUM-Live.
    </iframe>

Students can track their progress in :ref:`competencies_student` and view the next recommended content in their :ref:`learning_paths_student`.

Instructors have multiple ways to add competencies to their course: They can :ref:`create new competencies<create_competencies>`, :ref:`import existing ones <import_competencies>` or :ref:`generate competencies <generate_competencies>` using the integrated LLM subsystem of Artemis.
They can also then link exercises and lecture units to competencies and define competency relations. These relations determine the order in which competencies are presented in the students' learning paths.

Administrators :ref:`manage the standardized competency catalog <standardized_competency_catalog>`, an instance-wide catalog from which instructors can import competencies into their course.

.. toctree::

   adaptive-learning/adaptive-learning-student
   adaptive-learning/adaptive-learning-instructor
   adaptive-learning/adaptive-learning-admin
