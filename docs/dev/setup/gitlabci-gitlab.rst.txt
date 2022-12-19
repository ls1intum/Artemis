GitLab CI and GitLab Setup
--------------------------

This section describes how to set up a programming exercise environment
based on GitLab CI and GitLab.

**Prerequisites:**

* `Docker <https://docs.docker.com/install>`__
* `Docker-Compose <https://docs.docker.com/compose/install/>`__

.. contents:: Content of this section
    :local:
    :depth: 1


GitLab
^^^^^^

This section describes how to set up a development environment for Artemis with GitLab and GitLab CI.
The same basic steps as for a `GitLab and Jenkins <#jenkins-and-gitlab-setup>`__ setup apply, but the steps that describe generating tokens for Jenkins can be skipped.
For a production setup of GitLab, also see the documentation of the GitLab and Jenkins setup.

GitLab
""""""

1. Configure GitLab
    .. code:: bash

        cp src/main/docker/env.example.gitlab-gitlabci.txt src/main/docker/.env

    Now edit the file ``src/main/docker/.env``.

2. Start GitLab and the GitLab Runner
    .. code:: bash

        docker-compose -f src/main/docker/gitlab-gitlabci.yml --env-file src/main/docker/.env up --build -d

3. Get your GitLab root password
    .. code:: bash

        docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password

4. Generate an access token
    Go to ``http://gitlab/-/profile/personal_access_tokens`` and generate an access token with all scopes.
    This token is used in the Artemis configuration as `artemis.version-control.token`.

GitLab Runner
"""""""""""""

1. Register a new runner
    Login to your GitLab instance and open ``http://gitlab/admin/runners``.
    Click on ``Register an instance runner`` and copy the registration token.

    Then execute this command with the registration token:

    .. code:: bash

        docker exec -it gitlab-runner gitlab-runner register \
        --non-interactive \
        --executor "docker" \
        --docker-image alpine:latest \
        --url http://gitlab:80 \
        --registration-token "PROJECT_REGISTRATION_TOKEN" \
        --description "docker-runner" \
        --maintenance-note "Test Runner" \
        --tag-list "docker,artemis" \
        --run-untagged="true" \
        --locked="false" \
        --access-level="not_protected"

    .. note::
        For local development, you might add ``--clone-url http://gateway.docker.internal:80`` or ``--clone-url http://172.17.0.1:80`` to the command above.

    You should now find the runner in the list of runners (``http://gitlab/admin/runners``)

.. note::
    Adding a runner in a production setup works the same way.
    The GitLab administration page also contains alternative ways of setting up GitLab runners.
    All variants should allow the passing of the configuration options ``tag-list``, ``run-untagged``, ``locked``, and ``access-level`` similarly as in the Docker command above.
    If forgotten, Artemis might not use this runner to run the tests for exercise submissions.


Artemis
^^^^^^^

1. Generate authentication token
    The notification plugin has to authenticate to upload the test results.
    Therefore, a random string has to be generated, e.g., via a password generator.
    This should be used in place of ``notification-plugin-token`` value in the example config below.
2. Configure Artemis
    For local development, copy the following configuration into the ``application-local.yml`` file and adapt it with the values from the previous steps.
    Please make sure, that the GitLab Runner can access Artemis via the URL specified under ``artemis.server.url``.

    .. code:: yaml

        artemis:
            course-archives-path: ./exports/courses
            repo-clone-path: ./repos
            repo-download-clone-path: ./repos-download
            encryption-password: artemis_admin           # LEGACY: arbitrary password for encrypting database values
            bcrypt-salt-rounds: 11  # The number of salt rounds for the bcrypt password hashing. Lower numbers make it faster but more unsecure and vice versa.
            # Please use the bcrypt benchmark tool to determine the best number of rounds for your system. https://github.com/ls1intum/bcrypt-Benchmark
            user-management:
                use-external: false
                internal-admin:
                    username: artemis_admin
                    password: artemis_admin
                accept-terms: false
                login:
                    account-name: TUM
            version-control:
                url: http://gitlab # TODO
                user: root
                password: password # TODO
                token: gitlab-personal-access-token # TODO
            continuous-integration:
                build-timeout: 30
                artemis-authentication-token-value: notification-plugin-token # TODO
            git:
                name: Artemis
                email: artemis.in@tum.de
        server:
            port: 8080
            url: http://artemis:8080 # TODO

3. Start Artemis
    Start Artemis with the ``gitlab`` and ``gitlabci`` profile.
