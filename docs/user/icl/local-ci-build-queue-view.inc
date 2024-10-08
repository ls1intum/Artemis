Artemis provides a build overview view that displays all queued, running, and finished build jobs. Access to the system-wide build overview is
exclusively available to **Administrators** through the *System Administration* menu.

.. figure:: local-ci/buildQueueSystemAdministration.png
   :alt: Build Overview System Administration
   :align: center

   Build Overview System Administration

**Instructors** can access the build overview for a specific course through the *Build Overview* button located in *Course Management*.
This view displays only the queued, running, and finished build jobs associated with the selected course.

.. figure:: local-ci/buildQueueCourseManagement.png
   :alt: Build Overview Course Management
   :align: center

   Build Overview Course Management

The build overview view is divided into three sections:



Running Build Jobs
""""""""""""""""""
The "Running Build Jobs" section displays all build jobs currently being executed by a build agent.
Jobs are dynamically added to and removed from the queue in real-time.
Users have the ability to cancel any job in progress. The table provides the following information:


.. figure:: local-ci/running-build-jobs.png
   :alt: Running Build Jobs
   :align: center

   Running Build Jobs



Queued Build Jobs
""""""""""""""""""
The "Queued Build Jobs" section lists all build jobs currently awaiting execution.
Jobs are continuously added to and removed from the queue in real-time.
Users also have the option to cancel any job that is queued.
The table below displays the following information:


.. figure:: local-ci/queued-build-jobs.png
   :alt: Queued Build Jobs
   :align: center

   Queued Build Jobs



Finished Build Jobs
"""""""""""""""""""
The "Finished Build Jobs" section displays all build jobs that have concluded, whether successfully completed, with or without a result, failed, or cancelled.
Information about finished build jobs is progressively loaded as the user navigates through the pages.
Instructors can also access build logs with detailed information about the build execution by clicking on **View build logs**. This is particularly helpful for failed builds.
The table provides the following information:


.. figure:: local-ci/finished-build-jobs.png
   :alt: Finished Build Jobs
   :align: center

   Finished Build Jobs


.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/46947?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="400">
        Watch this video on TUM-Live.
    </iframe>



