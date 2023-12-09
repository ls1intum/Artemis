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

.. code-block:: yaml

       artemis:
           user-management:
               use-external: false # if you do not wish to use Jira for user management
           version-control:
               url: http://localhost:8080

The values configured here are sufficient for a basic Artemis setup that allows for running programming exercises with the local VC and local CI systems.

.. HINT::
   If you are running Artemis in Windows, you also need to add a property ``artemis.continuous-integration.docker-connection-uri`` with the value ``tcp://localhost:2375``.
   The default value for this property is ``unix:///var/run/docker.sock`` which is defined in ``src/main/resources/config/application-localci.yml``. The ``application-local.yml`` file overrides the values set there. When using Windows, you will also need make sure to enable the Docker setting "Expose daemon on tcp://localhost:2375 without TLS". You can do this in Docker Desktop under Settings > General.

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

.. code-block:: yaml

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

To create a course with registered users, you can use the scripts from ``supporting_scripts/course-setup-quickstart``.

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

.. _Setup with Docker Compose:

Setup with Docker Compose
^^^^^^^^^^^^^^^^^^^^^^^^^

You can also use Docker Compose to set up the local CI and local VC systems. Using the following command, you can start the Artemis and MySQL containers:

.. code-block:: bash

    docker compose -f docker/artemis-dev-local-vc-local-ci-mysql.yml up

.. HINT::
    Unix systems: When running the Artemis container on a Unix system, you will have to give the user running the container permission to access the Docker socket by adding them to the ``docker`` group. You can do this by changing the value of ``services.artemis-app.group_add`` in the ``docker/artemis-dev-local-vc-local-ci-mysql.yml`` file to the group ID of the ``docker`` group on your system. You can find the group ID by running ``getent group docker | cut -d: -f3``. The default value is ``999``.

    Windows: If you want to run the Docker containers locally on Windows, you will have to change the value for the Docker connection URI. You can add ``ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="tcp://host.docker.internal:2375"`` to the environment file, found in ``docker/artemis/config/dev-local-vc-local-ci.env``. This overwrites the default value ``unix:///var/run/docker.sock`` for this property defined in ``src/main/resources/config/application-docker.yml``.


Podman as Docker alternative
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

`Podman <https://podman.io/>`_ offers a container runtime that is API-compatible with Docker.
Rather than having a system-wide socket that runs with administrative permissions, Podman allows to create containers with only user permissions.
In single-user setups this might not be as relevant, but offers additional security in a production environment where the Artemis CI has to execute untrusted student code.

.. admonition:: Podman is supported on a best effort basis.

    We are relying on the API compatibility to provide support but are not actively testing against Podman on a test system or in the continuous integration.
    If you notice any issues, feel free to open an issue or pull request so that we can try to fix them.

.. note::

    These setup steps are mostly focused on Linux systems.
    On mac and Windows, both Docker and Podman run the containers in a small virtual machine anyway.
    Therefore, there is little technical benefit relevant to Artemis for choosing one over the other in local development setups.
    If in doubt, we recommend to use Docker, since that solution is most likely to be tested by other Artemis developers.


Linux setup
"""""""""""

Podman itself should be available via your regular package manager.

After the installation, you have to ensure that your user is allowed to create containers.
This is managed by the files ``/etc/subuid`` and ``/etc/subgid``.
Ensure both files contain a line starting with your username.
If not, you can generate the relevant lines by executing the following command:

.. code-block:: bash

    #! /usr/bin/env sh

    printf "%s:%d:65536\n" "$USER" "$(( $(id -u) * 65536 ))" | tee -a /etc/subuid /etc/subgid

After that, enable the Podman user socket that provides the API for the container management:

.. code-block:: bash

    systemctl --user enable --now podman.socket

Configure the connection to this socket in Artemis by replacing ``${UID}`` with your actual user id (``id -u``):

.. code-block:: yaml

    artemis:
        continuous-integration:
            docker-connection-uri: "unix:///run/user/${UID}/podman/podman.sock"
            # alternatively, if you use the `DOCKER_HOST` environment variable already
            # to tell other tools to use the Podman socket instead of the Docker one:
            # docker-connection-uri: "${DOCKER_HOST}"


Windows or mac setup
""""""""""""""""""""

Podman offers a `desktop application <https://podman-desktop.io/>`_ application similar to Docker desktop and `CLI tools <https://podman.io>`_ for Windows, macOS, and Linux.
As with Docker, to run containers on Windows or macOS the runtime has to start a small virtual Linux machine that then actually runs the containers.
You can probably connect to this VM similarly as described in the regular setup steps above
(`additional Podman documentation <https://podman-desktop.io/docs/migrating-from-docker/using-the-docker_host-environment-variable>`_).

.. note::

    If you try out Podman on a Windows or mac system and have additional setup tips, feel free to submit a pull request to extend this documentation section.
