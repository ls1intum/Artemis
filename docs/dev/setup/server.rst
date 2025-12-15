.. _Server Setup:

Server Setup
------------

To start the Artemis application server from the development environment, first import the project into IntelliJ IDEA.
Make sure to install the **Spring Boot** plugins for IntelliJ IDEA, this will make it easier to run the server.

Before starting the application, update the required configuration settings.

Configuration Setup
^^^^^^^^^^^^^^^^^^^

Configuration settings are stored in ``application-artemis.yml`` (located in ``src/main/resources/config``).
However, **do not modify this file directly**, as it may lead to accidental commits of sensitive information.

Instead, follow these best practices:

#. **Create a custom configuration file**
	* In ``src/main/resources/config``, create a new file named ``application-local.yml``.
	* This file is used to override settings in application.yml for local development environments. It is configured to be ignored by Git to ensure that local configurations are not committed.

#. **Customize configuration settings**
    * If you want to change application properties, you should do that in ``application-local.yml``.
    * If needed, you can modify values such as database credentials, authentication and application settings.

#. **Activate the correct profile**
	* When running Artemis, ensure the ``local`` profile is selected in the run configurations.
	* For additional custom configurations, create ``application-<name>.yml`` and activate the ``<name>`` profile.

Common Configuration Options
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For **development purposes, these settings are preconfigured, and no action is necessary** in this step.
You only need to modify them if your specific work or production environments require changes.
For example, when using Hyperion for AI-based programming exercise generation, you will need to add an OpenAI key.

.. code:: yaml

   artemis:
       repo-clone-path: ./repos/
       legal-path: ./legal/
       repo-download-clone-path: ./repos-download/
       bcrypt-salt-rounds: 11   # The number of salt rounds for bcrypt password hashing.
                                # Lower values improve speed but reduce security.
                                # Use the bcrypt benchmark tool to determine an optimal value: https://github.com/ls1intum/bcrypt-Benchmark

       user-management:
           use-external: true # enables ldap authentication
           password-reset:
                links:
                    en: '<link>'
                    de: '<link>'
           ldap:
               url: <url>
               user-dn: <user-dn>
               password: <password>
               base: <base>
               allowed-username-pattern: '^([a-z]{2}\d{2}[a-z]{3})$'    # example for a TUM identifier, e.g. ab12cde

       git:
           name: Artemis
           email: artemis@in.tum.de

       athena:
            # If you want to use Athena, refer to the dedicated configuration section. Under Administration Guide, Setup of Extension Services.

**Note:**
If you use a password for authentication, update it in ``gradle/liquibase.gradle``.

Version Control & Continuous Integration Setup
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you are setting up **programming exercises**, additional configuration is required for the version control and build system.
Refer to one of the following guides based on your preferred setup:

- **Integrated Code Lifecycle Setup (Recommended):** :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`
- **LocalVC + Jenkins Setup:** :ref:`Jenkins and LocalVC Setup <Jenkins and LocalVC Setup>`

**Note:**
If you use a password for authentication, update it in ``gradle/liquibase.gradle``.


.. _RunServerWithIntelliJ:

Run the server via a run configuration in IntelliJ
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The project comes with some pre-configured run / debug configurations that are stored in the ``.idea`` directory.
When you import the project into IntelliJ, the run configurations will also be imported.

The recommended way is to run the server and the client separately. This provides fast rebuilds of the server and hot
module replacement in the client.

* **Artemis (Client):** Will execute ``npm install`` and ``npm run start``. The client will be available at
  `http://localhost:9000/ <http://localhost:9000/>`__ with hot module replacement enabled (also see
  :ref:`Client Setup <client-setup>`).
* **Artemis Server (Dev, BuildAgend+LocalCI):** The server will be started separately from the client with the profiles
  ``dev,artemis,localci,localvc,scheduling,buildagent,core,ldap,local``.
* **Artemis Server (Dev, Core, Jenkins):** The server will be started separately from the client with the profiles
  ``dev,jenkins,localvc,artemis,scheduling,core,local``.
* **Artemis Server (Dev, LocalCI+BuildAgent, Athena):** The server will be started separately from the client with the profiles
  ``dev,localci,localvc,artemis,scheduling,athena,buildagent,core,local``.
* **Artemis Server (Dev, LocalCI+BuildAgent, Iris):** The server will be started separately from the client with the profiles
  ``dev,localci,localvc,artemis,scheduling,iris,buildagent,core,local``.
* **Artemis Server (Dev, LocalCI+BuildAgent, Theia):** The server will be started separately from the client with the profiles
  ``theia,dev,localci,localvc,artemis,scheduling,buildagent,core,ldap,local``.
* **Artemis Server (Prod, Core, LocalCI):** The server will be started separately from the client with the profiles
  ``prod,core,ldap,localvc,localci,scheduling,local``.
* **Artemis (BuildAgent):** The server will be started separately from the client with the profiles ``buildagent,local``.
  This configuration is used to run the build agent for the local CI. This configuration is rarely needed for development.

Deprecated Options
"""""""""""""""""""

* **Artemis (Server):** The server will be started separated from the client. The startup time decreases significantly.
* **Artemis (Server & Client):** Will start the server and the client. The client will be available at
  `http://localhost:8080/ <http://localhost:8080/>`__ with hot module replacement disabled.


Run the server via Docker
^^^^^^^^^^^^^^^^^^^^^^^^^

| **This method provides a fast way to start Artemis for demonstration purposes.**
| It is **not recommended** for development, as it does not support code modifications or debugging.

Artemis provides a Docker image named ``ghcr.io/ls1intum/artemis:<TAG/VERSION>``:

- The **current develop branch** is available under the tag ``develop``.
- The **latest stable release** can be retrieved using the tag ``latest``.
- **Specific releases**, such as ``7.10.8``, can be accessed with ``ghcr.io/ls1intum/artemis:7.10.8``.
- **Branches tied to a pull request** can be obtained using ``PR-<PR NUMBER>``.

Dockerfile
""""""""""

You can find the latest Artemis Dockerfile at ``docker/artemis/Dockerfile``.

* The Dockerfile has `multiple stages <https://docs.docker.com/build/building/multi-stage/>`__: A **builder** stage,
  building the ``.war`` file, an optional **external_builder** stage to import a pre-built ``.war`` file,
  a **war_file** stage to choose between the builder stages via build argument and a **runtime** stage with minimal
  dependencies just for running artemis.

* The Dockerfile defines three Docker volumes (at the specified paths inside the container):

    * **/opt/artemis/config:**

      This can be used to store additional configurations of Artemis in YAML files.
      The usage is optional, and we recommend using the environment files for overriding your custom configurations
      instead of using ``src/main/resources/application-local.yml`` as such an additional configuration file.
      The other configurations like ``src/main/resources/application.yml``, ... are built into the ``.war`` file and
      therefore are not needed in this directory.

      .. tip::
        Instead of mounting this config directory, you can also use environment variables for the configuration as
        defined by the
        `Spring relaxed binding <https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0#environment-variables>`__.
        You can either place those environment variables directly in the ``environment`` section,
        or create a `.env-file <https://docs.docker.com/compose/environment-variables/set-environment-variables/#substitute-with-an-env-file>`__.
        When starting an Artemis container directly with the Docker-CLI, an .env-file can also be given via the
        ``--env-file`` option.

        To ease the transition of an existing set of YAML configuration files into the environment variable style, a
        `helper script <https://github.com/b-fein/spring-yaml-to-env>`__ can be used.

    * **/opt/artemis/data:**

      This directory should be used for any data (e.g., local clone of repositories).
      This is preconfigured in the ``docker`` Java Spring profile (which sets the following values:
      ``artemis.repo-clone-path``, ``artemis.repo-download-clone-path``,
      ``artemis.course-archives-path``, ``artemis.submission-export-path`` ``artemis.legal-path``, and ``artemis.file-upload-path``).


    * **/opt/artemis/public/content:**

      This directory will be used for branding.
      You can specify a favicon here.

* The Dockerfile assumes that the mounted volumes are located on a file system with the following locale settings
  (see `#4439 <https://github.com/ls1intum/Artemis/issues/4439>`__ for more details):

    * LC_ALL ``en_US.UTF-8``
    * LANG ``en_US.UTF-8``
    * LANGUAGE ``en_US.UTF-8``

.. warning::
  **ARM64 Image builds** might run out of memory if not provided with enough memory and/or swap space.
  On a *Apple M1* we had to set the **Docker Desktop** memory limit to 12GB or more.

.. _Docker Debugging:

Debugging with Docker
"""""""""""""""""""""

| The Docker containers have the possibility to enable Java Remote Debugging via Java environment variables.
| Java Remote Debugging lets you use your preferred debugger connected to port 5005.
  For IntelliJ, you can use the `Remote Java Debugging for Docker` profile shipped in the git repository.

With the following Java environment variable, you can configure the Remote Java Debugging inside a container:

::

   _JAVA_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

| This is already pre-set in the Docker Compose **Artemis-Dev-MySQL** Setup.
| For issues at the startup, you might have to suspend the java command until a Debugger is connected.
  This is possible by setting ``suspend=y``.

Run the server with Spring Boot and Spring profiles
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Artemis server should startup by running the main class
``de.tum.cit.aet.artemis.ArtemisApp`` using Spring Boot.

.. note::
    Artemis uses Spring profiles to segregate parts of the
    application configuration and make it only available in certain
    environments. For development purposes, the following program arguments
    can be used to enable the ``dev`` profile and the profiles for Jenkins and LocalVC:

::

   --spring.profiles.active=dev,jenkins,localvc,artemis,scheduling

If you use IntelliJ (Community or Ultimate) you can set the active
profiles by

* Choosing ``Run | Edit Configurations...``
* Going to the ``Configuration Tab``
* Expanding the ``Environment`` section to reveal ``VM Options`` and setting them to
  ``-Dspring.profiles.active=dev,jenkins,localvc,artemis,scheduling``

Set Spring profiles with IntelliJ Ultimate
""""""""""""""""""""""""""""""""""""""""""

If you use IntelliJ Ultimate, add the following entry to the section
``Active Profiles`` (within ``Spring Boot``) in the server run
configuration:

::

   dev,jenkins,localvc,artemis,scheduling

Run the server with the command line (Gradle wrapper)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you want to run the application via the command line instead, make
sure to pass the active profiles to the ``gradlew`` command like this:

.. code:: bash

   ./gradlew bootRun --args='--spring.profiles.active=dev,jenkins,localvc,artemis,scheduling'

.. _hyperion-service:

Hyperion (Optional)
^^^^^^^^^^^^^^^^^^^^

Hyperion provides AI-assisted exercise creation features via Spring AI. No external Edutelligence service is required, only a LLM provider such as OpenAI or Azure OpenAI.

.. note::
   For local development with LM Studio (no cloud API keys required), see the `Spring AI Development Guide <https://ls1intum.github.io/Artemis/staff/spring-ai>`_ in the new documentation.

Production setup
""""""""""""""""

See :ref:`Hyperion Service <hyperion_admin_setup>` in the Administration Guide for instructions on enabling the
module in production and configuring LLM credentials.
