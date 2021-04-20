.. _docker:

Builds and Dependency Management
================================

General structure of Programming Exercise Execution
---------------------------------------------------

Artemis uses Docker containers for running programming exercises. This ensures that the code of the students doesn't have direct access to the hardware of the build agents.
To reduce the needed time of each test run, these docker containers already contain often used dependencies like JUnit.

There are currently docker containers for:

- Maven (Java and Kotlin): https://github.com/ls1intum/artemis-maven-docker
- OCaml: https://github.com/ls1intum/artemis-ocaml-docker
- Python: https://github.com/ls1intum/artemis-python-docker
- C: https://github.com/ls1intum/artemis-c-docker
- Haskell (external): https://github.com/lukasstevens/docker-fpv-stack
- Swift (external): https://github.com/norio-nomura/docker-swiftlint
- Assembler (external): https://hub.docker.com/r/tizianleonhardt/era-artemis-assembler
- VHDL (external): https://hub.docker.com/r/tizianleonhardt/era-artemis-vhdl

Steps for Updating the DockerBuilds used in Artemis
---------------------------------------------------
Updating can only be done with access to the account on Dockerhub. If you need access, contact `Stephan Krusche <krusche@in.tum.de>`_.

1. Update the dependencies via a pull request in the docker. Each docker has its own repository as listed above.
2. After the PR got merged, wait for the "latest" build on Dockerhub to finish

   - Choose the correct repository on `Dockerhub <https://hub.docker.com/orgs/ls1tum/repositories>`_
   - Go to the "Builds" tab of that repository
3. Wait for a successful build of "latest"
4. | Click on "Configure Automated Builds" and create a new build rule for a new tag, following the naming scheme:
   | "<programming language><Version of programming language>-<Upwards counting number>", for example "java16-3"

   - The "Sourcetype" should be "Tag"
   - This tag should be used as "Source Tag" and "Docker Tag"

   .. figure:: docker/new-docker-image-example.png
      :align: center
      :alt: Needed steps for creating a new build

5. Go back to the repository of the docker container in GitHub and create a new Tag

   - Click on releases on the right on the front page of that repository
   - Then create a new release with the button called "Draft release" and give it the same name as from step 4
6. Wait for the build in Dockerhub of the newly created Tag
7. Change the docker version in Artemis to the newly created tag: `ContinuousIntegrationService.java <https://github.com/ls1intum/Artemis/blob/develop/src/main/java/de/tum/in/www1/artemis/service/connectors/ContinuousIntegrationService.java>`_
8. If the used docker container should also be changed for already created exercises you have to change the build plan of that exercise

   - Change the used docker image under this setting: Configure Buildplan > Default Job > Docker > Docker Image
