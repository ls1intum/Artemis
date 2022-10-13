Setup Guide
===========

In this guide you learn how to setup the development environment of
Artemis. Artemis is based on `JHipster <https://jhipster.github.io>`__,
i.e. \ `Spring Boot <http://projects.spring.io/spring-boot>`__
development on the application server using Java 17, and TypeScript
development on the application client in the browser using
`Angular <https://angular.io>`__ and Webpack. To get an overview of the
used technology, have a look at the `JHipster Technology stack <https://jhipster.github.io/tech-stack>`__
and other tutorials on the JHipster homepage.

You can find tutorials how to setup JHipster in an IDE (`IntelliJ IDEA
Ultimate <https://www.jetbrains.com/idea>`__ is recommended) on
https://jhipster.github.io/configuring-ide. Note that the Community
Edition of IntelliJ IDEA does not provide Spring Boot support (see the
`comparison
matrix <https://www.jetbrains.com/idea/features/editions_comparison_matrix.html>`__).
Before you can build Artemis, you must install and configure the
following dependencies/tools on your machine:

1. `Java
   JDK <https://www.oracle.com/java/technologies/javase-downloads.html>`__:
   We use Java (JDK 17) to develop and run the Artemis application
   server which is based on `Spring
   Boot <http://projects.spring.io/spring-boot>`__.
2. `MySQL Database Server 8 <https://dev.mysql.com/downloads/mysql>`__:
   Artemis uses Hibernate to store entities in a MySQL database.
   Download and install the MySQL Community Server (8.0.x) and configure it according to section
   `MySQL Setup <#mysql-setup>`__.
3. `Node.js <https://nodejs.org/en/download>`__: We use Node LTS (>=16.13.0 < 17) to compile
   and run the client Angular application. Depending on your system, you
   can install Node either from source or as a pre-packaged bundle.
4. `Npm <https://nodejs.org/en/download>`__: We use Npm (>=8.1.0) to
   manage client side dependencies. Npm is typically bundled with Node.js,
   but can also be installed separately.
5. ( `Graphviz <https://www.graphviz.org/download/>`__: We use Graphviz to generate graphs within exercise task descriptions.
   It's not necessary for a successful build,
   but it's necessary for production setups as otherwise errors will show up during runtime. )
6. ( A **version control** and **build** system is necessary for the **programming exercise** feature of Artemis.
   There are multiple stacks available for the integration with Artemis:

   * `GitLab and Jenkins <#jenkins-and-gitlab-setup>`__
   * GitLab and GitLab CI (under development, not yet production ready)
   * `Bamboo, Bitbucket and Jira <#bamboo-bitbucket-and-jira-setup>`__)

------------------------------------------------------------------------------------------------------------------------

MySQL Setup
------------

The required Artemis schema will be created / updated automatically at startup time of the
server application.

As an alternative to a native MySQL setup, you can run the MySQL Database Server inside a Docker container
using e.g. ``docker-compose -f src/main/docker/mysql.yml up``.

If you run your own MySQL server, make sure to specify the default ``character-set``
as ``utf8mb4`` and the default ``collation`` as ``utf8mb4_unicode_ci``.
You can achieve this e.g. by using a ``my.cnf`` file in the location ``/etc``.

.. code::

    [client]
    default-character-set = utf8mb4
    [mysql]
    default-character-set = utf8mb4
    [mysqld]
    character-set-client-handshake = TRUE
    init-connect='SET NAMES utf8mb4'
    character-set-server = utf8mb4
    collation-server = utf8mb4_unicode_ci

Make sure the configuration file is used by MySQL when you start the server.
You can find more information on `<https://dev.mysql.com/doc/refman/8.0/en/option-files.html>`__

MySQL 8 Docker Container for ARM64 processors
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
.. TODO: check if this is still needed or if the other images already run with ARM64 processors

In case you are using a computer with an arm64 processor you might want to change the used image
in the ``src/main/docker/mysql.yml`` file. Using e.g. ``ubuntu/mysql:8.0-21.10_beta`` will let the MySQL database
run natively on arm64 processors.

Users for MySQL
^^^^^^^^^^^^^^^
| For the development environment the default MySQL user is ‘root’ with an empty password.
| (In case you want to use a different password, make sure to change the value in
  ``application-local.yml`` *(spring > datasource > password)* and in ``liquibase.gradle``
  *(within the 'liquibaseCommand' as argument password)*).

Set empty root password for MySQL 8
"""""""""""""""""""""""""""""""""""
If you have problems connecting to the MySQL 8 database using an empty root password, you can try the following command
to reset the root password to an empty password:

.. code::

    mysql -u root --execute "ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY ''";

.. warning::
    Empty root passwords should only be used in a development environment.
    The root password for a production environment must never be empty.

------------------------------------------------------------------------------------------------------------------------

.. _Server Setup:

Server Setup
------------

To start the Artemis application server from the development
environment, first import the project into IntelliJ and then make sure
to install the Spring Boot plugins to run the main class
``de.tum.in.www1.artemis.ArtemisApp``. Before the application runs, you
have to change some configuration options.
You can change the options directly in the file ``application-artemis.yml`` in the folder
``src/main/resources/config``. However, you have to be careful that you do not
accidentally commit your password. Therefore, we strongly recommend, to create a new file
``application-local.yml`` in the folder ``src/main/resources/config`` which is ignored by default.
You can override the following configuration options in this file.

.. code:: yaml

   artemis:
       repo-clone-path: ./repos/
       repo-download-clone-path: ./repos-download/
       encryption-password: <encrypt-password>      # LEGACY: arbitrary password for encrypting database values
       bcrypt-salt-rounds: 11   # The number of salt rounds for the bcrypt password hashing. Lower numbers make it faster but more unsecure and vice versa.
                                # Please use the bcrypt benchmark tool to determine the best number of rounds for your system. https://github.com/ls1intum/bcrypt-Benchmark
       user-management:
           use-external: true
           password-reset:
                credential-provider: <provider> # The credential provider which users can log in though (e.g. TUMonline)
                links: # The password reset links for different languages
                    en: '<link>'
                    de: '<link>'
           external:
               url: https://jira.ase.in.tum.de
               user: <username>    # e.g. ga12abc
               password: <password>
               admin-group-name: tumuser
           ldap:
               url: <url>
               user-dn: <user-dn>
               password: <password>
               base: <base>
       version-control:
           url: https://bitbucket.ase.in.tum.de
           user: <username>    # e.g. ga12abc
           password: <password>
           token: <token>                 # VCS API token giving Artemis full Admin access. Not needed for Bamboo+Bitbucket
           ci-token: <token from the CI>   # Token generated by the CI (e.g. Jenkins) for webhooks from the VCS to the CI. Not needed for Bamboo+Bitbucket
       continuous-integration:
           url: https://bamboo.ase.in.tum.de
           user: <username>    # e.g. ga12abc
           token: <token>      # Enter a valid token generated by bamboo or leave this empty to use the fallback authentication user + password
           password: <password>
           vcs-application-link-name: LS1 Bitbucket Server     # If the VCS and CI are directly linked (normally only for Bitbucket + Bamboo)
           empty-commit-necessary: true                        # Do we need an empty commit for new exercises/repositories in order for the CI to register the repo
           # Hash/key of the ci-token, equivalent e.g. to the ci-token in version-control
           # Some CI systems, like Jenkins, offer a specific token that gets checked against any incoming notifications
           # from a VCS trying to trigger a build plan. Only if the notification request contains the correct token, the plan
           # is triggered. This can be seen as an alternative to sending an authenticated request to a REST API and then
           # triggering the plan.
           # In the case of Artemis, this is only really needed for the Jenkins + GitLab setup, since the GitLab plugin in
           # Jenkins only allows triggering the Jenkins jobs using such a token. Furthermore, in this case, the value of the
           # hudson.util.Secret is stored in the build plan, so you also have to specify this encrypted string here and NOT the actual token value itself!
           # You can get this by GETting any job.xml for a job with an activated GitLab step and your token value of choice.
           secret-push-token: <token hash>
           # Key of the saved credentials for the VCS service
           # Bamboo: not needed
           # Jenkins: You have to specify the key from the credentials page in Jenkins under which the user and
           #          password for the VCS are stored
           vcs-credentials: <credentials key>
           # Key of the credentials for the Artemis notification token
           # Bamboo: not needed
           # Jenkins: You have to specify the key from the credentials page in Jenkins under which the notification token is stored
           notification-token: <credentials key>
           # The actual value of the notification token to check against in Artemis. This is the token that gets send with
           # every request the CI system makes to Artemis containing a new result after a build.
           # Bamboo: The token value you use for the Server Notification Plugin
           # Jenkins: The token value you use for the Server Notification Plugin and is stored under the notification-token credential above
           authentication-token: <token>
       lti:
           id: artemis_lti
           oauth-key: artemis_lti_key
           oauth-secret: <secret>    # only important for online courses on the edX platform, can typically be ignored
           user-prefix-edx: edx_
           user-prefix-u4i: u4i_
           user-group-name-edx: edx
           user-group-name-u4i: u4i
       git:
           name: Artemis
           email: artemis@in.tum.de
       athene:
           url: http://localhost
           base64-secret: YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=
           token-validity-in-seconds: 10800

Change all entries with ``<...>`` with proper values, e.g. your TUM
Online account credentials to connect to the given instances of JIRA,
Bitbucket and Bamboo. Alternatively, you can connect to your local JIRA,
Bitbucket and Bamboo instances. It’s not necessary to fill all the
fields, most of them can be left blank. Note that there is additional
information about the setup for programming exercises provided:

.. note::
   Be careful that you do not commit changes to ``application-artemis.yml``.
   To avoid this, follow the best practice when configuring your local development environment:

   1) Create a file named ``application-local.yml`` under ``src/main/resources/config``.
   2) Copy the contents of ``application-artemis.yml`` into the new file.
   3) Update configuration values in ``application-local.yml``.

   By default, changes to ``application-local.yml`` will be ignored by git so you don't accidentally
   share your credentials or other local configuration options. The run configurations contain a profile
   ``local`` at the end to make sure the ``application-local.yml`` is considered. You can create your own
   configuration files ``application-<name>.yml`` and then activate the profile ``<name>`` in the run
   configuration if you need additional customizations.

If you use a password, you need to adapt it in
``gradle/liquibase.gradle``.


Run the server via a service configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This setup is recommended for production instances as it registers Artemis as a service and e.g. enables auto-restarting
of Artemis after the VM running Artemis has been restarted.
As alternative you could take a look at the section below about
`deploying artemis as docker container <#run-the-server-via-docker>`__.
For development setups, see the other guides below.

This is a service file that works on Debian/Ubuntu (using systemd):

::

   [Unit]
   Description=Artemis
   After=syslog.target
   [Service]
   User=artemis
   WorkingDirectory=/opt/artemis
   ExecStart=/usr/bin/java \
     -Djdk.tls.ephemeralDHKeySize=2048 \
     -DLC_CTYPE=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Dsun.jnu.encoding=UTF-8 \
     -Djava.security.egd=file:/dev/./urandom \
     -Xmx2048m \
     --add-modules java.se \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens java.management/sun.management=ALL-UNNAMED \
     --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
     -jar artemis.war \
     --spring.profiles.active=prod,bamboo,bitbucket,jira,ldap,scheduling,openapi
   SuccessExitStatus=143
   StandardOutput=/opt/artemis/artemis.log
   StandardError=inherit
   [Install]
   WantedBy=multi-user.target


The following parts might also be useful for other (production) setups, even if this service file is not used:

- ``-Djava.security.egd=file:/dev/./urandom``: This is required if repositories are cloned via SSH from the VCS.
   The default (pseudo-)random-generator ``/dev/random`` is blocking which results in very bad performance when using
   SSH due to lack of entropy.


The file should be placed at ``/etc/systemd/system/artemis.service`` and after running ``sudo systemctl daemon-reload``,
you can start the service using ``sudo systemctl start artemis``.

You can stop the service using ``sudo service artemis stop`` and restart it using ``sudo service artemis restart``.

Logs can be fetched using ``sudo journalctl -u artemis -f -n 200``.

Run the server via Docker
^^^^^^^^^^^^^^^^^^^^^^^^^

| Artemis provides a Docker image named ``ghcr.io/ls1intum/artemis:<TAG/VERSION>``.
| The current develop branch is provided by the tag ``develop``.
| The latest release is provided by the tag ``latest``.
| Specific releases like ``5.7.1`` can be retrieved as ``ghcr.io/ls1intum/artemis:5.7.1``.
| Branches tied to a pull request can be obtained by using the tag ``PR-<PR NUMBER>``.


Dockerfile
""""""""""

You can find the latest Artemis Dockerfile at ``src/main/docker/Dockerfile``.

* The Dockerfile defines three Docker volumes

    * ``/opt/artemis/config``: This will be used to store the configuration of Artemis in YAML files. If this directory is empty, the default configuration of Artemis will be copied upon container start.

      .. tip::
        Instead of mounting this config directory, you can also use environment variables for the configuration as defined by the `Spring relaxed binding <https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0#environment-variables>`__.
        You can either place those environment variables directly in the ``environment`` section, or create an `.env-file <https://docs.docker.com/compose/environment-variables/#the-env-file>`__.
        When starting an Artemis container directly with the Docker-CLI, an .env-file can also be given via the ``--env-file`` option.

        To ease the transition of an existing set of YAML configuration files into the environment variable style, a `helper script <https://github.com/b-fein/spring-yaml-to-env>`__ can be used.

    * ``/opt/artemis/data``: This directory should be used for any data (e.g., local clone of repositories).
      Therefore, configure Artemis to store this files into this directory. In order to do that, you have to change
      some properties in configuration files (i.e., ``artemis.repo-clone-path``, ``artemis.repo-download-clone-path``,
      ``artemis.course-archives-path``, ``artemis.submission-export-path``, and ``artemis.file-upload-path``).
      Otherwise you'll get permission failures.
    * ``/opt/artemis/public/content``: This directory will be used for branding.
      You can specify a favicon, ``imprint.html``, and ``privacy_statement.html`` here.

* The Dockerfile sets the correct permissions to the folders that are mounted to the volumes on startup (not recursive).

* The startup script is located `here <https://github.com/ls1intum/Artemis/blob/develop/bootstrap.sh>`__.

* The Dockerfile assumes that the mounted volumes are located on a file system with the following locale settings
  (see `#4439 <https://github.com/ls1intum/Artemis/issues/4439>`__ for more details):

    * LC_ALL ``en_US.UTF-8``
    * LANG ``en_US.UTF-8``
    * LANGUAGE ``en_US.UTF-8``

Run the server via a run configuration in IntelliJ
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The project comes with some pre-configured run / debug configurations that are stored in the ``.idea`` directory.
When you import the project into IntelliJ the run configurations will also be imported.

The recommended way is to run the server and the client separated. This provides fast rebuilds of the server and hot
module replacement in the client.

* **Artemis (Server):** The server will be started separated from the client. The startup time decreases significantly.
* **Artemis (Client):** Will execute ``npm install`` and ``npm run serve``. The client will be available at
  `http://localhost:9000/ <http://localhost:9000/>`__ with hot module replacement enabled (also see `Client Setup <#client-setup>`__).

Other run / debug configurations
""""""""""""""""""""""""""""""""

* **Artemis (Server & Client):** Will start the server and the client. The client will be available at
  `http://localhost:8080/ <http://localhost:8080/>`__ with hot module replacement disabled.
* **Artemis (Server, Jenkins & GitLab):** The server will be started separated from the client with the profiles
  ``dev,jenkins,gitlab,artemis`` instead of ``dev,bamboo,bitbucket,jira,artemis``.
* **Artemis (Server, Athene):** The server will be started separated from the client with ``athene`` profile enabled
  (see `Athene Service <#athene-service>`__).

Run the server with Spring Boot and Spring profiles
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Artemis server should startup by running the main class
``de.tum.in.www1.artemis.ArtemisApp`` using Spring Boot.

.. note::
    Artemis uses Spring profiles to segregate parts of the
    application configuration and make it only available in certain
    environments. For development purposes, the following program arguments
    can be used to enable the ``dev`` profile and the profiles for JIRA,
    Bitbucket and Bamboo:

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling

If you use IntelliJ (Community or Ultimate) you can set the active
profiles by

* Choosing ``Run | Edit Configurations...``
* Going to the ``Configuration Tab``
* Expanding the ``Environment`` section to reveal ``VM Options`` and setting them to
  ``-Dspring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling``

Set Spring profiles with IntelliJ Ultimate
""""""""""""""""""""""""""""""""""""""""""

If you use IntelliJ Ultimate, add the following entry to the section
``Active Profiles`` (within ``Spring Boot``) in the server run
configuration:

::

   dev,bamboo,bitbucket,jira,artemis,scheduling

Run the server with the command line (Gradle wrapper)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you want to run the application via the command line instead, make
sure to pass the active profiles to the ``gradlew`` command like this:

::

   ./gradlew bootRun --args='--spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling'

As an alternative, you might want to use Jenkins and GitLab with an
internal user management in Artemis, then you would use the profiles:

::

   dev,jenkins,gitlab,artemis,scheduling

Configure Text Assessment Analytics Service
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Text Assessment Analytics is an internal analytics service used to gather data regarding the features of the text
assessment process. Certain assessment events are tracked:

1. Adding new feedback on a manually selected block
2. Adding new feedback on an automatically selected block
3. Deleting a feedback
4. Clicking to resolve feedback conflicts
5. Clicking to view origin submission of automatically generated feedback
6. Hovering over the text assessment feedback impact warning
7. Editing/Discarding an automatically generated feedback
8. Clicking the Submit button when assessing a text submission
9. Clicking the Assess Next button when assessing a text submission

These events are tracked by attaching a POST call to the respective DOM elements on the client side.
The POST call accesses the **TextAssessmentEventResource** which then adds the events in its respective table.
This feature is disabled by default. We can enable it by modifying the configuration in the file:
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   info:
      text-assessment-analytics-enabled: true

------------------------------------------------------------------------------------------------------------------------

Client Setup
------------

You need to install Node and Npm on your local machine.

Using IntelliJ
^^^^^^^^^^^^^^

If you are using **IntelliJ** you can use the pre-configured ``Artemis (Client)``
run configuration that will be delivered with this repository:

* Choose ``Run | Edit Configurations...``
* Select the ``Artemis (Client)`` configuration from the ``npm section``
* Now you can run the configuration in the upper right corner of IntelliJ

.. _UsingTheCommandLine:

Using the command line
^^^^^^^^^^^^^^^^^^^^^^

You should be able to run the following
command to install development tools and dependencies. You will only
need to run this command when dependencies change in ``package.json``.

::

   npm install

To start the client application in the browser, use the following
command:

::

   npm run serve

This compiles TypeScript code to JavaScript code, starts the hot module
replacement feature in Webpack (i.e. whenever you change a TypeScript
file and save, the client is automatically reloaded with the new code)
and will start the client application in your browser on
``http://localhost:9000``. If you have activated the JIRA profile (see
above in Server Setup) and if you have configured
``application-artemis.yml`` correctly, then you should be able to login
with your TUM Online account.

In case you encounter any problems regarding JavaScript heap memory leaks when executing ``npm run serve`` or any other
scripts from ``package.json``,
you can add a memory limit parameter (``--max_old_space_size=5120``) in the script.
You can do it by changing the **start** script in ``package.json`` from:

::

   "start": "ng serve --hmr",

to

::

   "start": "node --max_old_space_size=5120 ./node_modules/@angular/cli/bin/ng serve --hmr",

If you still face the issue, you can try to set a higher value than 5120. Possible values are 6144, 7168, and 8192.

The same change could be applied to each **ng** command as in the example above.

Make sure to **not commit this change** in ``package.json``.


For more information, review `Working with
Angular <https://www.jhipster.tech/development/#working-with-angular>`__.
For further instructions on how to develop with JHipster, have a look at
`Using JHipster in
development <http://www.jhipster.tech/development>`__.

------------------------------------------------------------------------------------------------------------------------

Customize your Artemis instance
-------------------------------

You can define the following custom assets for Artemis to be used
instead of the TUM defaults:

* The logo next to the “Artemis” heading on the navbar → ``${artemisRunDirectory}/public/images/logo.png``
* The favicon → ``${artemisRunDirectory}/logo/favicon.svg``
* The privacy statement HTML → ``${artemisRunDirectory}/public/content/privacy_statement.html``
* The imprint statement HTML → ``${artemisRunDirectory}/public/content/imprint.html``
* The contact email address in the ``application-{dev,prod}.yml`` configuration file under the key ``info.contact``

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/programming-exercises.rst.txt

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/bamboo-bitbucket-jira.rst.txt

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/jenkins-gitlab.rst.txt

------------------------------------------------------------------------------------------------------------------------

Athene Service
--------------

The semi-automatic text assessment relies on the Athene_ service.
To enable automatic text assessments, special configuration is required:

Enable the ``athene`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling,athene

Configure API Endpoints:
^^^^^^^^^^^^^^^^^^^^^^^^

The Athene service is running on a dedicated machine and is addressed via
HTTP. We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   artemis:
     # ...
     athene:
       url: http://localhost
       base64-secret: YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=
       token-validity-in-seconds: 10800

.. _Athene: https://github.com/ls1intum/Athene

------------------------------------------------------------------------------------------------------------------------

Apollon Service
---------------

The `Apollon Converter`_ is needed to convert models from their JSON representaiton to PDF.
Special configuration is required:

Enable the ``apollon`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling,apollon

Configure API Endpoints:
^^^^^^^^^^^^^^^^^^^^^^^^

The Apollon conversion service is running on a dedicated machine and is adressed via
HTTP. We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   apollon:
      conversion-service-url: http://localhost:8080


.. _Apollon Converter: https://github.com/ls1intum/Apollon_converter

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/common-problems.rst.txt

------------------------------------------------------------------------------------------------------------------------

.. TODO: ask Simon or someone if structure is alright
.. include:: setup/distributed.rst.txt

------------------------------------------------------------------------------------------------------------------------

Alternative: Docker-Compose Setup
---------------------------------

A full functioning development environment can also be set up using
docker-compose:

1. Install `docker <https://docs.docker.com/install/>`__ and `docker-compose <https://docs.docker.com/compose/install/>`__
2. Configure the credentials in ``application-artemis.yml`` in the folder ``src/main/resources/config`` as described above
3. Run ``docker-compose up``
4. Go to http://localhost:9000

The client and the server will run in different containers. As Npm is
used with its live reload mode to build and run the client, any change
in the client’s codebase will trigger a rebuild automatically. In case
of changes in the codebase of the server one has to restart the
``artemis-server`` container via
``docker-compose restart artemis-server``.

(Native) Running and Debugging from IDEs is currently not supported.

Get a shell into the containers
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

-  app container:
   ``docker exec -it $(docker-compose ps -q artemis-app) sh``
-  mysql container:
   ``docker exec -it $(docker-compose ps -q artemis-mysql) mysql``

Other useful commands
^^^^^^^^^^^^^^^^^^^^^

-  Stop the server: ``docker-compose stop artemis-server`` (restart via
   ``docker-compose start artemis-server``)
-  Stop the client: ``docker-compose stop artemis-client`` (restart via
   ``docker-compose start artemis-client``)

------------------------------------------------------------------------------------------------------------------------

.. include:: setup/kubernetes.rst.txt
