Artemis offers a build agent view that shows the status of all build agents in the system. This view is available only to Administrators.
The build agent view consists of two parts: the *Build Agent Summary View* and the *Build Agent Details*.


The **Build Agent Summary View** shows a list of all build agents in the system, along with their status (idle and running), maximum number of concurrent builds, and running builds.
The running builds can be canceled individually by clicking the red cancellation button next to the build. An Administrator can also cancel all running builds on a build agent by clicking the '*Cancel All*' button.
Administrators also have the option to pause all build agents, which will prevent all build agents from accepting new build jobs. All build agents can also be resume with a button.
They can also clear distributed data, this includes the build job queue, the results queue, the processing jobs distributed map, and the build agent information map. More information can be found :ref:`here <hazelcast_data>`.

.. figure:: local-ci/build-agent-summary.png
   :alt: Build Agent Summary View
   :align: center

   Build Agent Summary View

The Build Agent Details View provides detailed information about a selected build agent.

At the top, the view displays the build agent’s status, start and last build dates, average build duration, the maximum number of concurrent builds allowed on the agent, and the current Git revision.
Administrators can pause the build agent using the Pause button.

The Overview section presents a summary of build statistics, including the number of builds, successful builds, failed builds, canceled builds, and timed-out builds.
A circular progress indicator visually represents these statistics. This section tracks data since the last boot-up of the build agent.
If the build agent restarts, all values in this section reset. However, pausing the build agent does not affect these statistics.

The Running Build Jobs section lists all currently active build jobs on the agent, displaying details such as the job ID, repository name, commit hash, submission date, build duration, and priority.
Administrators can cancel all running jobs using the Cancel All button.

The Finished Build Jobs section shows recent build jobs processed by the agent, including the job name, status, participation ID, commit hash, course ID, submission date, build start time, and build duration.
Clicking on the participation ID navigates to the submission page, while selecting the commit hash leads to the commit details page.
The course ID links to the course management page. A search function and filtering options allow administrators to find specific finished jobs, and the Refresh button updates the displayed information.

.. figure:: local-ci/build-agent-details.png
   :alt: Build Agent Details
   :align: center

   Build Agent Details

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/46914?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="400">
        Watch this video on TUM-Live.
    </iframe>
