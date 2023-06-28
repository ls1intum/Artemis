.. _Local CI and local VC Setup:

Local CI and local VC setup
---------------------------

This section describes how to set up a programming exercise environment based on the local CI and local VC systems.
These two systems are integrated into the Artemis server application and thus the setup is greatly simplified compared to the external options.
This also reduces system requirements as you do not have to run any systems in addition to the Artemis server.
For now, this setup is only recommended for development and testing purposes.
If you are setting Artemis up for the first time, these are the steps you should follow:

- Install and run Docker: https://docs.docker.com/get-docker
- Start the database: :ref:`Database Setup`
- :ref:`Configure Artemis`
- (optional) :ref:`Configure Jira`
- :ref:`Start Artemis`
- :ref:`Test the Setup`

You can see the configuration in the following video:

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/34536?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Video version of the setup guide on TUM-Live.
    </iframe>

.. contents:: Content of this section
    :local:
    :depth: 1


.. _Configure Artemis:

Configure Artemis
^^^^^^^^^^^^^^^^^

Create a file ``src/main/resources/config/application-local.yml`` with the following content:

.. code:: yaml

       artemis:
           user-management:
               use-external: false # if you do not wish to use Jira for user management
           version-control:
               url: http://localhost:8080

The values configured here are sufficient for a basic Artemis setup that allows for running programming exercises with the local VC and local CI systems.

.. HINT::
   If you are running Artemis in Windows, you also need to add a property ``artemis.continuous-integration.docker-connection-uri`` with the value ``tcp://localhost:2375``.
   The default value for this property is ``unix:///var/run/docker.sock`` which is defined in ``src/main/resources/config/application-localci.yml``. The ``application-local.yml`` file overrides the values set there.

When you start Artemis for the first time, it will automatically create an admin user called "artemis_admin". If this does not work, refer to the guide for the :ref:`Jenkins and GitLab Setup` to manually create an admin user in the database.
You can then use that admin user to create further users in Artemis' internal user management system.


.. _Configure Jira:

Configure Jira
^^^^^^^^^^^^^^

The local CI and local VC systems work fine without external user management configured so this step is **optional**.
Setting up Jira allows you to run a script that sets up a number of users and groups for you.

If you have already set up your system with Bamboo, Bitbucket, and Jira, you can keep using Jira for user management. Just stop the Bamboo and Bitbucket containers.
If you want to use Jira for user management, but have not configured it yet, refer to the guide for the :ref:`Bamboo Bitbucket and Jira Setup`.
You can follow all steps to set up the entire Atlassian stack, or just get the license for Jira and only follow steps 1-3 leaving out the setup of the Bamboo and Bitbucket containers.
You can stop and remove the Bamboo and Bitbucket containers or just stop them in case you want to set them up later on.

You also need to configure further settings in the ``src/main/resources/config/application-local.yml`` properties:

.. code:: yaml

       artemis:
           user-management:
               use-external: true
               external:
                   url: http://localhost:8081
                   user:  <jira-admin-user> # insert the admin user you created in Jira
                   password: <jira-admin-password> # insert the admin user's password
                   admin-group-name: instructors


.. _Start Artemis:

Start Artemis
^^^^^^^^^^^^^

Start Artemis with the profiles ``localci`` and ``localvc`` so that the correct adapters will be used,
e.g.:

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,local

All of these profiles are enabled by default when using the ``Artemis (Server, LocalVC & LocalCI)`` run configuration in IntelliJ.
Add ``jira`` to the list of profiles if you want to use Jira for user management: `dev,localci,localvc,artemis,scheduling,local,jira`
Please read :ref:`Server Setup` for more details.


.. _Test the Setup:

Test the Setup
^^^^^^^^^^^^^^

You can now test the setup:

- Create a course and a programming exercise.

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/34537?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Video of creating a programming exercise on TUM-Live.
    </iframe>

- Log in as a student registered for that course and participate in the programming exercise, either from the online editor or by cloning the repository and pushing from your local environment.

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/34538?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Video showcasing how to participate in a programming exercise from the online editor and from a local Git client on TUM-Live.
    </iframe>

- Make sure that the result of your submission is displayed in the Artemis UI.

.. HINT::
   At the moment, the local VC system only supports accessing repositories via HTTP(S) and Basic Auth. We plan to add SSH support in the future. For now, you need to enter your Artemis credentials (username and password) when accessing template, solution, test, and assignment repositories.

For unauthorized access, your Git client will display the respective error message:

.. raw:: html

    <iframe src="https://live.rbg.tum.de/w/artemisintro/34539?video_only=1&t=0" allowfullscreen="1" frameborder="0" width="600" height="350">
        Video showcasing unauthorized access to a local VC repository on TUM-Live.
    </iframe>
