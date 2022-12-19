.. _system_design:

System Design
=============

Top-Level Design
----------------

The following diagram shows the top-level design of Artemis which is decomposed into an application client
(running as Angular web app in the browser) and an application server (based on Spring Boot).
For programming exercises, the application server connects to a version control system (VCS) and
a continuous integration system (CIS).
Authentication is handled by an external user management system (UMS).

.. figure:: system-design/TopLevelDesign.png
    :align: center
    :alt: Top-Level Design

    Top-Level Design

While Artemis includes generic adapters to these three external systems with a defined protocol that can be instantiated
to connect to any VCS, CIS or UMS, it also provides 3 concrete implementations for these adapters to connect to:

1. **VCS:** Atlassian Bitbucket Server
2. **CIS:** Atlassian Bamboo Server
3. **UMS:** Atlassian JIRA Server (more specifically Atlassian Crowd on the JIRA Server)

Deployment
----------

The following UML deployment diagram shows a typical deployment of Artemis application server and application client.
Student, Instructor and Teaching Assistant (TA) computers are all equipped equally with the Artemis application client
being displayed in the browser.

The Continuous Integration Server typically delegates the build jobs to local build agents within
the university infrastructure or to remote build agents, e.g. hosted in the Amazon Cloud (AWS).

.. figure:: system-design/DeploymentOverview.svg
    :align: center
    :alt: Deployment Overview

    Deployment Overview


Data Model
----------

The Artemis application server uses the following (simplified) data model in the MySQL database.
It supports multiple courses with multiple exercises.
Each student in the participating student group can participate in the exercise by clicking
the **Start Exercise** button.
Then a repository and a build plan for the student (User) will be created and configured.
The initialization state helps to track the progress of this complex operation and allows recovering from errors.
A student can submit multiple solutions by committing and pushing the source code changes to a given example code
into the version control system or using the user interface.
The continuous integration server automatically tests each submission, and notifies the Artemis application server,
when a new result exists.
In addition, teaching assistants can assess student solutions and "manually" create results.

.. figure:: system-design/DataModel.svg
    :align: center
    :alt: Data Model

    Data Model

Please note, that the actual database model is more complex. The UML class diagram above omits some details for
readability (e.g. lectures, student questions, exercise details, static code analysis, quiz questions, exam sessions,
submission subclasses, etc.)


Server Architecture
-------------------

The following UML component diagram shows more details of the Artemis application server architecture and its
REST interfaces to the application client.

.. figure:: system-design/ServerArchitecture.png
    :align: center
    :alt: Server Architecture

    Server Architecture
