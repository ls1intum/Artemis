Artemis offers a build agent view that shows the status of all build agents in the system. This view is available only to Administrators.
The build agent view consists of two parts: the *Build Agent Summary View* and the *Build Agent Details*.


The **Build Agent Summary View** shows a list of all build agents in the system, along with their status (idle and running), maximum number of concurrent builds, and running builds.
The running builds can be canceled individually by clicking the red cancellation button next to the build. An Administrator can also cancel all running builds on a build agent by clicking the '*Cancel All*' button.

.. figure:: local-ci/build-agent-summary.png
   :alt: Build Agent Summary View
   :align: center

   Build Agent Summary View


The **Build Agent Details View** shows detailed information about a selected build agent.
Firstly, it shows the status, maximum number of concurrent builds, and running builds for the build agent.
It offers the same functionality as the *Build Agent Summary View*, allowing the Administrator to cancel all running builds on the build agent.
Secondly, it shows the build agent's 20 recent build jobs. This includes information such as the build job's name, participation ID,status (success with result, failure or canceled),
the submission time, start time, end time, the duration of the build job, the course ID, and the commit hash.
The administrator can click on the participation ID to navigate to the participation's submission page.
The administrator can also click on the commit hash to navigate to the commits details page and the course ID to navigate to the course management page.

.. figure:: local-ci/build-agent-details.png
   :alt: Build Agent Details
   :align: center

   Build Agent Details

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/46914?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="400">
        Watch this video on TUM-Live.
    </iframe>
