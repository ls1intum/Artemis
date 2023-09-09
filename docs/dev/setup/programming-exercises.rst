.. _programming-exercises:

Programming Exercise adjustments
--------------------------------

There are several variables that can be configured when using programming exercises.
They are presented in this separate section to keep the 'normal' setup guide shorter.


Path variables
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


Jenkins template
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
  * - ``#isTestWiseCoverageEnabled``
    - Defines if testwise coverage should be collected.
    - Exercise configuration

The ``pipeline.groovy`` file can be customized further by instructors after creating the exercise from within
Artemis via the ‘Edit Build Plan’ button on the details page of the exercise.


Caching example for Maven
^^^^^^^^^^^^^^^^^^^^^^^^^

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
      setDockerFlags()

      docker.image(dockerImage).inside(dockerFlags) { c ->
          runTestSteps()
      }
  }

  private void setDockerFlags() {
      if (isSolutionBuild) {
          dockerFlags += " -v artemis_maven_cache:/maven_cache"
      } else {
          dockerFlags += " -v artemis_maven_cache:/maven_cache:ro"
      }
  }

This mounts the cache as writeable only when executing the tests for the solution repository, and as read-only when
running the tests for students’ code.


Caching example for Gradle
^^^^^^^^^^^^^^^^^^^^^^^^^^

In case of always writeable caches you can set ``-e GRADLE_USER_HOME=/gradle_cache`` as part of the ``dockerFlags``
instead of the ``MAVEN_OPTS`` like above.

For read-only caches like in the Maven example, define ``setDockerFlags()`` as

.. code:: groovy

  private void setDockerFlags() {
      if (isSolutionBuild) {
          dockerFlags += ' -e GRADLE_USER_HOME="/gradle_cache"'
          dockerFlags += ' -v artemis_gradle_cache:/gradle_cache'
      } else {
          dockerFlags += ' -e GRADLE_RO_DEP_CACHE="/gradle_cache/caches/"'
          dockerFlags += ' -v artemis_gradle_cache:/gradle_cache:ro'
      }
  }

