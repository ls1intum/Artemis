.. _docker:

Builds and Dependency Management
================================

General Structure of Programming Exercise Execution
---------------------------------------------------

Artemis uses docker containers to run programming exercises. This ensures that the students' code does not have direct access to the build agents' hardware.
To reduce the time required for each test run, these docker containers already include commonly used dependencies such as JUnit.

There are currently docker containers for:

- `Maven (Java and Kotlin) <https://github.com/ls1intum/artemis-maven-docker>`_
- `OCaml <https://github.com/ls1intum/artemis-ocaml-docker>`_
- `Python <https://github.com/ls1intum/artemis-python-docker>`_
- `C <https://github.com/ls1intum/artemis-c-docker>`_
- `Haskell <https://github.com/b-fein/artemis-haskell>`_ (external)
- `Swift <https://github.com/norio-nomura/docker-swiftlint>`_ (external)
- `Assembler <https://hub.docker.com/r/tizianleonhardt/era-artemis-assembler>`_ (external)
- `VHDL <https://hub.docker.com/r/tizianleonhardt/era-artemis-vhdl>`_ (external)

Steps for Updating the DockerBuilds used in Artemis
---------------------------------------------------
Updating can only be done with access to the account on Dockerhub. If you need access, contact `Stephan Krusche <krusche@in.tum.de>`_.

1. Update the dependencies via a pull request in the repository of the docker container. Each docker container has its own repository as listed above
2. After the PR got merged, go to the build overview on Dockerhub:

   - Choose the correct repository on `Dockerhub <https://hub.docker.com/orgs/ls1tum/repositories>`_
   - Go to the "Builds" tab of that repository
3. Wait for a successful build of "latest"
4. | Click "Configure Automated Builds" and create a new build rule for a new tag, following the naming scheme:
   | "<programming language><Version of programming language>-<Upwards counting number>", for example "java17-3"

   - The "Sourcetype" should be "Tag"
   - This tag should be used as "Source Tag" and "Docker Tag"

   .. figure:: docker/new-docker-image-example.png
      :align: center
      :alt: Required steps for creating a new build

5. Go back to the repository of the docker container in GitHub and create a new Tag

   - Click "Releases" on the right on the front page of that repository
   - Create a new release with the button called "Draft release" and give it the same name as in step 4
6. Wait for the build in Dockerhub of the newly created Tag
7. Change the docker image used in Artemis to the newly created tag: `application.yml <https://github.com/ls1intum/Artemis/blob/develop/src/main/resources/config/application.yml>`_
8. If the used docker container should also be changed for already created exercises you have to change the build plan of that exercise

   - Change the docker image used by following this path: Configure Buildplan > Default Job > Docker > Docker Image
