Artemis supports an integrated version control system (VCS) and continuous integration system (CIS).
If you use this *local VCS* and *local CIS*, the architecture differs from the architecture with external VC and CI systems.
The deployment with the local VCS and local CIS (without using an external user management system) looks like this:

.. figure:: /dev/system-design/localvc-localci/LocalVC_LocalCI_Deployment.png
   :align: center
   :width: 800
   :alt: Local VC and Local CI Deployment

   Local VC and Local CI Deployment

Employing the local VCS and local CIS, administrators and developers can set the Artemis application up without the need for dedicated VCS and CIS installations.
This new architecture simplifies the setup process, reduces dependencies on external systems, and streamlines maintenance for both developers and administrators.
Developers have fewer applications to run in parallel, which translates into decreased system requirements.
See :ref:`Local CI and local VC Setup` on how to set the system up.

.. HINT::
   The system is still in an experimental state and not recommended for production. It currently only supports Java programming exercises built with Gradle and none of the advanced exercise configurations (like static code analysis).

The local VC subsystem
^^^^^^^^^^^^^^^^^^^^^^

The following diagram shows an overview of the components in the local VC subsystem:

.. figure:: /dev/system-design/localvc-localci/LocalVC_Subsystem.png
   :align: center
   :width: 800
   :alt: Local VC Subsystem

   Local VC Subsystem

The ``Local VC Service`` implements the ``VersionControlService`` interface and thus contains methods that the exercise management subsystem and the exercise participation subsystem need to interact with the VC system.
E.g. the ``createRepository()`` method creates a repository on the file system.
For users to be able to access the repositories using their local Git client, the local VC subsystem contains a ``Git Server`` component.
It responds to ``fetch`` and ``push`` requests from Git clients, enabling instructors and students to interact with their repositories the way they are used to.
It encompasses all the logic for implementing the Git HTTP protocol server-side.
This includes extracting the command and parameters from the client request and executing the Git commands on the server-side repository, provided the repository exists, and the user has the requisite permissions.
It reads objects and refs from the repository, updates the repository for push requests, and formats the results of the Git commands it executes into a response that it sends back to the client.
This could involve sending objects and refs to the client in a packfile, or transmitting error messages.
The ``Git Server`` delegates all logic connected to Artemis to the ``Local VC Servlet Service``.
This service resolves the repository from the file system depending on the repository URL. It also handles user authentication (only Basic Auth for now) and authorization.
For authorization (e.g. "is the requesting user the owner of the repository?", "has the due date already passed?"), it uses the logic outsourced to the ``RepositoryAccessService`` that the existing online editor also uses.
For push requests, the ``Local VC Servlet Service`` calls the ``processNewProgrammingSubmission()`` method of the ``Programming Submission Service`` to create a new submission and finally calls the local CI subsystem to trigger a new build.

Integrating the VC system into the Artemis server application improves performance.
For instance, when an instructor creates a new programming exercise, Artemis needs to copy the template source code to the template repository.
Using the local VCS, Artemis merely needs to communicate with the host file system, copying the files from one location in the file system to another, which is faster than communicating with the external VCS through the network.

The local CI subsystem
^^^^^^^^^^^^^^^^^^^^^^

The following diagram shows an overview of the components in the local CI subsystem:

.. figure:: /dev/system-design/localvc-localci/LocalCI_Subsystem.png
   :align: center
   :width: 800
   :alt: Local CI Subsystem

   Local CI Subsystem

The local CIS provides a concrete implementation of the ``ContinuousIntegrationTriggerService`` interface for the local CIS, the ``LocalCITriggerService``, providing a ``triggerBuild`` method.
For instance, instructors can trigger builds for all student repositories from the Artemis user interface, when they changed the configuration of a programming exercise.
This may be the case after adapting the test cases for the exercise, rendering the build results of all students invalid.
Similarly, the student can manually trigger a build for their assignment repository from the Artemis user interface when there was an issue during the build process.

For each call to the ``triggerBuild`` method, the ``LocalCITriggerService`` delegates a new build job to the local CI build system.
We implemented the local CI build system in such a way that it restricts the amount of build jobs that can run concurrently and adds build jobs to a blocking queue in case it reaches the maximum amount of builds.

The local CI build system consists of four main services, that provide the task of managing a queue of build jobs, executing build jobs, and returning the build results.
The ``LocalCIBuildJobManagementService`` contains the logic for managing build jobs.
It prepares a build task in form of a lambda function and submits this task to the ``ExecutorService``.
The ``ExecutorService`` encapsulates the low level logic for handling of the queue and the concurrency when running multiple build jobs at a time.
As soon as a build job finishes, the ``ExecutorService`` returns the result of the task execution to the ``LocalCIBuildJobManagementService``.
The ``ExecutorService`` makes sure that errors happening during the build job execution are propagated to the ``LocalCIBuildJobManagementService``, so it can handle all errors in one spot.

To improve the reliability of the system, the ``LocalCIBuildJobManagementService`` implements a timeout mechanism.
Administrators can configure a maximum amount of time that build jobs can run by setting the ``artemis.continuous-integration.timeout-seconds`` environment variable. The default value is 120 seconds.
If a build job times out, the  ``LocalCIBuildJobManagementService`` interrupts the build job.
This is crucial to prevent jobs that require an abnormally high amount of time from clogging up the system and reducing overall system performance.

The ``LocalCIBuildJobExecutionService`` has the method ``runBuildJob``, that contains the actual logic for executing a build job.

A basic build job for the purpose of providing automated assessment in Artemis consists of the following steps:

- Start a Docker container for the build job.
- Run the build script on the container. This involves:

  - Check out the repository under test (e.g. the student assignment repository) and the test repository containing the test cases.
  - Compile the source code of both the test repository and the repository under test.
  - Execute the test cases.

- Retrieve the test results from the container.
- Stop the container.
- Parse the test results.

We designed the local CIS such that the process of scheduling and managing build jobs is decoupled from the process of actually running the builds and tests.
Artemis only needs to create a new build job and add it to the queue.
It does not need to know how or where the build job will be executed.
This means that we can replace the mechanism for executing the build jobs without aﬀecting the rest of the application, which allows us to outsource the tasks to external build agents in the future.

To address potential security risks associated with executing student code during automated assessment, we run the build job in a container, that the ``LocalCIContainerService`` creates and starts just for this purpose.
This container functions as an isolated environment.
If a student submits potentially malicious code, the container confines its execution, preventing it from directly affecting the host system or other containers.

The ephemeral nature of Docker containers allows the ``LocalCIBuildJobExecutionService`` to quickly remove them and the data they produced during the build when a build job finishes.

Finally, when the build ran through successfully, the local CI trigger service communicates the build result to the feedback subsystem, that makes it available to the instructor or student.
If there were any errors, the ``LocalCIBuildJobManagementService`` sends an error message to the Artemis user interface, that enables the instructor or student to take further action.
It also stops the container the build job runs in using the ``LocalCIContainerService``.

