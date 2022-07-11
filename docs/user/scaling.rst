.. _scaling:

Scaling
=======

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

Artemis is a scalable system that supports large courses: Some courses at TUM have been conducted using Artemis with more than 2000 students.
Depending on your setup and the number of user - as well as other requirements such as availability - it might be necessary for you to scale your Artemis installations.


Different aspects of the Artemis infrastructure can be scaled as listed in this document.


Build system
------------

The build system is responsible for providing feedback to students when they work on programming exercises.
Especially during lecture and before deadlines, a lot of students work simultaneously on their submissions, causing high load on the build system.
The build system must be scaled adequately in order to provide the feedback within a reasonable time.

More build agents can be added to the build system, guides can be found `here for Bamboo <https://confluence.atlassian.com/bamboo/bamboo-remote-agent-installation-guide-289276832.html>`_ and `here for Jenkins </dev/setup/jenkins-gitlab#build-agents>`_.

Note that for very large setups, scaling the version control system might be necessary as well.


Artemis
-------

Artemis supports scaling to provide high availability as well as improved performance.

This is especially important if you plan to conduct exams using Artemis.
Details regarding the scaling of Artemis can be found in the `corresponding section of the documentation</dev/setup/distributed>`_.

Depending on your setup and the number of users, you might be required to also scale/optimize other parts of the Artemis infrastructure, e.g. the database or used third-party systems.
Please refer to the corresponding documentations for detailed information.

Note that support for Kubernetes is currently being added.
