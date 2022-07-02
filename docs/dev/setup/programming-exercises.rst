Adjustments for programming exercises
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are several variables that can be configured when using programming exercises.
They are presented in this separate page to keep the 'normal' setup guide shorter.


Path variables
##############

There are variables for several paths:

- ``artemis.repo-clone-path``

  Repositories that the Artemis server needs are stored in this folder.
  This e.g. affects repositories from students which use the online code editor or the template/solution repositories of new exercises, as they are pushed to the VCS after modification.

  Files in this directory are usually not critical, as the latest pushed version of these repositories are also stored at the VCS.
  However, changed that are saved in the online code editor but not yet committed will be lost when this folder is deleted.

- ``artemis.repo-download-clone-path``

  Repositories that were downloaded from Artemis are stored in this directory.

  Files in this directory can be removed without loss of data, if the downloaded repositories are still present at the VCS.
  No changes to the data in the VCS are stored in this directory (or they can be retrieved by performing the download-action again).

- ``artemis.template-path``

  Templates are available within Artemis. The templates should fit to most environments, but there might be cases where one wants to change the templates.

  This value specifies the path to the templates which should overwrite the default ones.
  Note that this is the path to the folder where the `templates` folder is located, not the path to the `templates` folder itself.



Templates
#########

Templates are shipped with Artemis (they can be found within the ``src/main/resources/templates`` folder in GitHub).
These templates should fit well for many deployments, but one might want to change some of them for special deployments.

As of now, you can overwrite the ``jenkins`` folders that is present within the ``src/main/resources/templates`` folder.
Files that are present in the file system will be used, if a file is not present in the file system, it is loaded from the classpath (e.g. the .war archive).

We plan to make other folders configurable as well, but this is not supported yet.

Jenkins template
----------------
The build process in Jenkins is stored in a ``config.xml``-file (``src/main/resources/templates/jenkins``) that shares common steps for all programming languages (e.g. triggering a build when a push to GitLab occurred).
It is extended by a ``Jenkinsfile`` that is dependent on the used programming language which will be included in the generic ``config.xml`` file.
The builds steps (including used docker images, the checkout process, the actual build steps, and the reporting of the results to Artemis) is included in the ``Jenkinsfile``.

A sample ``Jenkinsfile`` can be found at ``src/main/resources/templates/jenkins/java/Jenkinsfile``.
Note that the ``Jenkinsfile`` **must** start either

- with ``pipeline`` (there must not be a comment before pipeline, but there can be one at any other position, if the Jenkinsfile-syntax allows it)
- or the special comment ``// ARTEMIS: JenkinsPipeline`` in the first line.

The variables `#dockerImage`, `#testRepository`, `#assignmentRepository`, `#jenkinsNotificationToken` and `#notificationsUrl` will automatically be replaced (for the normal Jenkinsfile, within the Jenkinsfile-staticCodeAnalysis, #staticCodeAnalysisScript is also replaced).

You should not need to touch any of these variables, except the `#dockerImage` variable, if you want to use a different agent setup (e.g. a Kubernetes setup).


Caching example for Maven
^^^^^^^^^^^^^^^^^^^^^^^^^
The Docker image used to run the maven-tests already contains a set of commonly used dependencies (see `artemis-maven-docker <https://github.com/ls1intum/artemis-maven-docker>`__).
This significantly speeds up builds as the dependencies do not have to be downloaded every time a build is started.
However, the dependencies included in the Docker image might not match the dependencies required in your tests (e.g. because you added new dependencies or the Docker image is outdated).

You can cache the maven-dependencies also on the machine that runs the builds (that means, outside the docker container) using the following steps:

Adjust the agent-args and add the environment block.


.. code:: bash

        agent {
            docker {
                image '#dockerImage'
                label 'docker'
                args '-v $HOME/maven-cache-docker:/var/maven'
            }
        }
        environment {
          JAVA_TOOL_OPTIONS = '-Duser.home=/var/maven'
        }
        stages {
            stage('Checkout') {



You have to add permissions to the folder (which will be located at the $HOME folder of the user that jenkins uses), e.g. with ``sudo chmod 777 maven-cache-docker -R``.

Note that this might allow students to access shared resources (e.g. jars used by Maven), and they might be able to overwrite them.
You can use `Ares <https://github.com/ls1intum/Ares>`__ to prevent this by restricting the resources the student's code can access.
