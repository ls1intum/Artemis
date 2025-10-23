.. _programming_exercises:

Programming Exercise Adjustments
--------------------------------

There are several variables that can be configured when using programming exercises.
They are presented in this separate section to keep the 'normal' setup guide shorter.


Path Variables
^^^^^^^^^^^^^^

There are variables for several paths:

- ``artemis.repo-clone-path``

  Repositories that the Artemis server needs are stored in this folder.
  This e.g. affects repositories from students which use the online code editor or
  the template/solution repositories of new exercises, as they are pushed to the VCS after modification.

  Files in this directory are usually not critical, as the latest pushed version of these repositories are
  also stored at the VCS.
  However, changed that are saved in the online code editor but not yet committed will be lost when
  this folder is deleted.

- ``artemis.repo-download-clone-path``

  Repositories that were downloaded from Artemis are stored in this directory.

  Files in this directory can be removed without loss of data, if the downloaded repositories are still present
  at the VCS.
  No changes to the data in the VCS are stored in this directory (or they can be retrieved by performing
  the download-action again).

- ``artemis.template-path``

  Templates are available within Artemis.
  The templates should fit to most environments, but there might be cases where one wants to change the templates.

  This value specifies the path to the templates which should overwrite the default ones.
  Note that this is the path to the folder where the ``templates`` folder is located, not the path to the
  ``templates`` folder itself.



Templates
^^^^^^^^^

Templates are shipped with Artemis (they can be found within the ``src/main/resources/templates`` folder in GitHub).
These templates should fit well for many deployments, but one might want to change some of them for special deployments.

As of now, you can overwrite the ``jenkins`` folders that is present within the ``src/main/resources/templates`` folder
by placing a ``templates/`` directory with the same structure next to the Artemis ``.war`` archive.
Files that are present in the file system will be used, if a file is not present in the file system,
it is loaded from the classpath (e.g. the ``.war`` archive).

We plan to make other folders configurable as well, but this is not supported yet.

Jenkins Template
""""""""""""""""

The build process in Jenkins is stored in a ``config.xml``-file (in ``src/main/resources/templates/jenkins/``).
It is extended by a ``Jenkinsfile`` in the same directory that will be placed inside the ``config.xml`` file.
The ``Jenkinsfile`` handles the functionality shared by all programming languages like checking out the repositories and
loading the actual exercise-specific pipeline script from the Artemis server.

.. note::

    When overriding the ``Jenkinsfile`` with a custom one, note that it **must** start either

    - with ``pipeline`` (there must not be a comment before pipeline, but there can be one at any other position,
      if the Jenkinsfile-syntax allows it)
    - or the special comment ``// ARTEMIS: JenkinsPipeline`` in the first line.

The actual programming language or exercise-type specific pipeline steps are defined in the form of
`scripted pipelines <https://www.jenkins.io/doc/book/pipeline/syntax/#scripted-pipeline>`_.
In principle, this is a Groovy script which allows structuring the pipeline into smaller methods and allows
conditionally executing steps, but inside still allows the core structure blocks from
`declarative pipelines <https://www.jenkins.io/doc/book/pipeline/syntax/#declarative-pipeline>`_.
You can override those ``pipeline.groovy`` files with the template mechanism described above.

Inside the ``pipeline.groovy`` some placeholders exist that will be filled by Artemis upon exercise creation from the
server or exercise settings:

.. list-table:: ``pipeline.groovy`` placeholders
  :widths: 25 50 25
  :header-rows: 1

  * - Variable
    - Replacement
    - Origin
  * - ``#dockerImage``
    - The container image that the tests will run in.
    - Server configuration
  * - ``#dockerArgs``
    - Additional flags passed to Docker when starting the container.
    - Server configuration
  * - ``#isStaticCodeAnalysisEnabled``
    - Defines if static code analysis should be performed.
    - Exercise configuration

The ``pipeline.groovy`` file can be customized further by instructors after creating the exercise from within
Artemis via the ‘Edit Build Plan’ button on the details page of the exercise.


.. _dependecies-sonatype-nexus:

Caching Maven Dependencies with Sonatype Nexus
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

With Sonatype Nexus you can run a caching server in your local network for Maven dependencies.
An alternative approach for caching is with docker volumes, see :ref:`dependecies-docker-volumes`.

.. note::

    The following steps assume ``artemis.example.com`` is the host ``10.0.73.42`` and is using port ``8443`` for the cache.
    Adapt the URLs for your actual setup.

Sonatype Nexus Setup
""""""""""""""""""""

1. Set up Sonatype Nexus to run on ``artemis.example.com:8443`` e.g. in a `Docker container <https://hub.docker.com/r/sonatype/nexus3/>`_ behind a `proxy <https://help.sonatype.com/en/run-behind-a-reverse-proxy.html>`_.
2. In the initial setup steps: Allow anonymous access.
3. Set up the Maven proxy repository:
    a. Create a new repository (**Repository - Repositories - Create repository**) of type ``maven2 (proxy)`` with name ``maven-proxy``.
    b. The remote URL is https://repo1.maven.org/maven2/.
4. Optionally create a new cleanup policy under *Repository - Cleanup Policies*
    a. Format: ``maven2``
    b. Release type: Releases & Pre-releases/Snapshots
    c. Cleanup criteria: e.g. ‘Component Usage 14’ will remove all files that have not been downloaded for 14 days.
    d. You can now add this cleanup policy to the policies in the repository you created earlier.

Adding proxy to a Maven build
"""""""""""""""""""""""""""""

The following changes have to be made inside the `tests` repository.

Option 1
========

Configure Maven so that it can find your Maven cache:

.. code-block:: xml
    :caption: ``pom.xml``

    <repositories>
        <repository>
            <id>artemis-cache</id>
            <url>https://artemis.example.com:8443/repository/maven-proxy/</url>
        </repository>
    </repositories>
    <pluginRepositories>
        <pluginRepository>
            <id>artemis-cache</id>
            <url>https://artemis.example.com:8443/repository/maven-proxy/</url>
        </pluginRepository>
    </pluginRepositories>

Option 2 (more rigorous alternative)
====================================

This setup forces Maven to exclusively download dependencies from the own proxy.

.. code-block:: xml
    :caption: ``.mvn/local-settings.xml``

    <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
    <mirrors>
        <mirror>
        <id>artemis-cache</id>
        <name>Artemis Cache</name>
        <url>https://artemis.example.com:8443/repository/maven-proxy/</url>
        <mirrorOf>*</mirrorOf>
        <blocked>false</blocked>
        </mirror>
    </mirrors>
    </settings>


.. code-block:: shell
    :caption: ``.mvn/maven.config``

    --settings
    ./.mvn/local-settings.xml

Adding proxy to a Gradle build
""""""""""""""""""""""""""""""

The following changes have to be made inside the `tests` repository.

.. code-block:: groovy
    :caption: ``build.gradle``

    repositories {
        maven {
            url "https://artemis.example.com:8443/repository/maven-proxy/"
        }
        // …
    }


.. code-block:: kotlin
    :caption: Gradle ``build.gradle.kts``

    repositories {
        maven {
            url = uri("https://artemis.example.com:8443/repository/maven-proxy/")
        }
        // …
    }

Security Considerations
"""""""""""""""""""""""

When you are using secret tests as part of your exercise, you might want to restrict network traffic leaving the CI run to avoid students leaking information.

Jenkins
=======

In Jenkins setups, you can restrict the network access by adjusting the ``pipeline.groovy`` script.
Add some flags to the ``dockerFlags`` variable:

.. code:: groovy

    dockerFlags += '--add-host "artemis.example.com:10.0.73.42" \
        --network "artemis-restricted"'

Additionally, on the CI runner host you will have to create the `artemis-restricted` Docker network and some iptables firewall rules to restrict traffic:

.. code-block:: sh

   docker network create --opt com.docker.network.bridge.name=artemis-restr artemis-restricted
   iptables -I DOCKER-USER -i artemis-restr -j DROP
   iptables -I DOCKER-USER -i artemis-restr -d $IP_OF_ARTEMIS_EXAMPLE_COM_CACHE -p tcp --dport 8443 -j ACCEPT


.. _dependecies-docker-volumes:

Caching with Docker Volumes
^^^^^^^^^^^^^^^^^^^^^^^^^^^

With Docker volumes you can cache Maven dependencies.
An alternative approach for caching is with Sonatype Nexus, see :ref:`dependecies-sonatype-nexus`.

Example for Maven
"""""""""""""""""

The container image used to run the maven-tests already contains a set of commonly used dependencies
(see `artemis-maven-docker <https://github.com/ls1intum/artemis-maven-docker>`__).
This significantly speeds up builds as the dependencies do not have to be downloaded every time a build is started.
However, the dependencies included in the container image might not match the dependencies required in your tests
(e.g. because you added new dependencies or the container image is outdated).

You can cache the maven-dependencies also on the machine that runs the builds
(that means, outside the container) by editing the ``pipeline.groovy`` template.

Adjust the ``dockerFlags`` variable:

.. code:: groovy

  dockerFlags = '#dockerArgs -v artemis_maven_cache:/maven_cache -e MAVEN_OPTS="-Dmaven.repo.local=/maven_cache/repository"'


Note that this might allow students to access shared resources (e.g. jars used by Maven), and they might be able
to overwrite them.
You can use `Ares <https://github.com/ls1intum/Ares>`__ to prevent this by restricting the resources
the student's code can access.

Alternatively, you can restrict the access to the mounted volume by changing the ``dockerFlags`` to

.. code:: groovy

  dockerFlags = '#dockerArgs -e MAVEN_OPTS="-Dmaven.repo.local=/maven_cache/repository"'

and changing the ``testRunner`` method into

.. code:: groovy

  void testRunner() {
      setup()

      docker.image(dockerImage).inside(dockerFlags) { c ->
          runTestSteps()
      }
  }

  private void setup() {
      if (isSolutionBuild) {
          dockerFlags += " -v artemis_maven_cache:/maven_cache"
      } else {
          dockerFlags += " -v artemis_maven_cache:/maven_cache:ro"
      }
  }

This mounts the cache as writeable only when executing the tests for the solution repository, and as read-only when
running the tests for students’ code.


Example for Gradle
""""""""""""""""""

In case of always writeable caches you can set ``-e GRADLE_USER_HOME=/gradle_cache`` as part of the ``dockerFlags``
instead of the ``MAVEN_OPTS`` like above.

For read-only caches like in the Maven example, define ``setup()`` as

.. code:: groovy

  private void setup() {
      if (isSolutionBuild) {
          dockerFlags += ' -e GRADLE_USER_HOME="/gradle_cache"'
          dockerFlags += ' -v artemis_gradle_cache:/gradle_cache'
      } else {
          dockerFlags += ' -e GRADLE_RO_DEP_CACHE="/gradle_cache/caches/"'
          dockerFlags += ' -v artemis_gradle_cache:/gradle_cache:ro'
      }
  }

Security Considerations
"""""""""""""""""""""""

When you are using secret tests as part of your exercise, you might want to disable network traffic leaving the CI run to avoid students leaking information.
Thanks to the fact that the cache is prepared while running for the solution, you can disable the network for students submissions.
Adjust ``dockerFlags`` and ``mavenFlags`` only for student submissions, like this:

.. code:: groovy

  private void setup() {
      if (isSolutionBuild) {
          // handle docker flags
      } else {
          // handle docker flags
          // if not solution repo, disallow network access from containers
          dockerFlags += ' --network none'
          mavenFlags += ' --offline'
      }


Timeout Options
^^^^^^^^^^^^^^^

This setting is relevant only when using :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.

You can adjust possible :ref:`timeout options<edit_build_duration>` for the build process in :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.
These values will determine what is the minimum, maximum, and default value for the build timeout in seconds that can be set in the Artemis UI.
The max value is the upper limit for the timeout, if the value is set higher than the max value, the max value will be used.

If you want to change these values, you need to change them in ``localci`` and ``buildagent`` nodes.
The corresponding configuration files are ``application-localci.yml`` and ``application-buildagent.yml``.
Ensure that the values are consistent across these files. In a :ref:`multi-node setup<setup_distributed>`, also verify that both core nodes and build agent nodes use the same values.

Default values have already been set, and modifying these values is not required unless a specific adjustment is needed.

    .. code-block:: yaml

        artemis:
            continuous-integration:
                build-timeout-seconds:
                    min: <value>
                    max: <value>
                    default: <value>


Build Log Configuration
^^^^^^^^^^^^^^^^^^^^^^^

This setting is relevant only when using :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.

The build log settings control the amount of log data stored per build job.
These values define the maximum number of lines and the maximum number of characters per line that can be retained in the logs.
The `Java Docker client <https://github.com/docker-java/docker-java>`_ imposes a hard limit of 1024 characters per line.
If a lower value is configured, logs exceeding this limit will be truncated, and excess characters will be lost.
These settings only need to be adjusted in the buildagent configuration file, ``application-buildagent.yml``.

Default values have already been set, and modifying these values is not required unless a specific adjustment is needed.

    .. code-block:: yaml

        artemis:
            continuous-integration:
                build-logs:
                    max-lines-per-job: <value>
                    max-chars-per-line: <value>

Container Resource Restrictions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This setting is relevant only when using :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.

Instructors can edit the resources allocated to containers resulting from a programming exercise.
These restrictions ensure that assigned values do not exceed predefined limits.
To enforce these constraints, update the buildagent configuration files, ``application-buildagent.yml``.
Ensure that these values remain consistent across all nodes in :ref:`multi-node setups<setup_distributed>` to maintain uniform resource allocation.

Default values have already been set, and modifying these values is not required unless a specific adjustment is needed.

    .. code-block:: yaml

        artemis:
            continuous-integration:
                container-flags-limit:
                    # Optional: restrict which custom Docker networks instructors can select
                    # Comma-separated list. Use 'none' to allow "no network" option
                    # This list must match networks available on the build agent host(s)
                    # Example: none, artemis-restricted
                    allowed-custom-networks: <network1>, <network2>
                    max-cpu-count: <value>
                    max-memory: <value>
                    max-memory-swap: <value>

Custom Docker Networks
^^^^^^^^^^^^^^^^^^^^^^

This setting is relevant only when using :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.

Artemis allows instructors to select a custom Docker network for build containers per programming exercise. This can be used to
isolate containers (e.g., disable all outbound traffic) or to place them into a restricted network that only allows access to
approved services such as artifact caches.

- Configure the list of networks that instructors are allowed to choose from using the ``allowed-custom-networks`` property.
- Provide a comma-separated list of Docker network names. Use ``none`` to offer the "no network" option.
- The selected network must exist on the build agent host(s) where containers are created (e.g., create with ``docker network create ...``).
- If no network is selected in the UI, Docker’s default network mode is used.

Configuration (server/core node):

.. code-block:: yaml

   artemis:
       continuous-integration:
           container-flags-limit:
               # Example: offer a fully isolated option and a restricted bridge network
               allowed-custom-networks: none, artemis-restricted

.. important::

    In distributed deployments, configure the same list on all core nodes. The build agent uses the network passed from the core
    and expects the network to be present on the Docker host.

.. warning::

    Selecting ``none`` disables network access for the container. Ensure all dependencies are available in the image or are cached,
    and configure builds to run offline where applicable (e.g., Maven ``--offline``), otherwise builds may fail.

Pause Grace Period
^^^^^^^^^^^^^^^^^^^^^^

This setting is relevant only when using :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.

The pause grace period determines how long the build agent waits after being paused before canceling all currently running jobs on that agent and adding them back to the queue.
This setting should be adjusted only if the default grace period is unsuitable for the specific environment. The configuration is set in the buildagent configuration file, ``application-buildagent.yml``.

Default values have already been set, and modifying these values is not required unless a specific adjustment is needed.

    .. code-block:: yaml

        artemis:
            continuous-integration:
                pause-grace-period-seconds: <value>
