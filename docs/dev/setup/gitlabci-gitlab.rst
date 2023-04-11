GitLab CI and GitLab Setup
--------------------------

This section describes how to set up a programming exercise environment
based on GitLab CI and GitLab.

.. note::
    Depending on your operating system, it might not work with the predefined values (``host.docker.internal``).
    Therefore, it might be necessary to adapt these with e.g. your local IP address.

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

1. Depending on your operating system, it is necessary to update the host file of your machine to include the following line:

    .. code:: text

        127.0.0.1       host.docker.internal
        ::1             host.docker.internal

2. Configure GitLab
    .. code:: bash

        cp docker/env.example.gitlab-gitlabci.txt docker/.env

3. Start GitLab and the GitLab Runner
    .. code:: bash

        docker-compose -f docker/gitlab-gitlabci.yml --env-file docker/.env up --build -d

4. Get your GitLab root password
    .. code:: bash

        docker exec -it gitlab grep 'Password:' /etc/gitlab/initial_root_password

5. Generate an access token
    Go to ``http://host.docker.internal/-/profile/personal_access_tokens`` and generate an access token with all scopes.
    This token is used in the Artemis configuration as ``artemis.version-control.token``.

6. Allow outbound requests to local network
    For setting up the webhook between Artemis and GitLab, it is necessary to allow requests to the local network.
    Go to ``http://host.docker.internal/admin/application_settings/network`` and allow the outbound requests.
    More information about this aspect can be found in the `GitLab setup instructions <#gitlab-access-token>`__ (step 12).

GitLab Runner
"""""""""""""

1. Register a new runner
    Login to your GitLab instance and open ``http://host.docker.internal/admin/runners``.
    Click on ``Register an instance runner`` and copy the registration token.

    Then execute this command with the registration token:

    .. code:: bash

        docker exec -it gitlab-runner gitlab-runner register \
        --non-interactive \
        --executor "docker" \
        --docker-image alpine:latest \
        --url http://host.docker.internal:80 \
        --registration-token "PROJECT_REGISTRATION_TOKEN" \
        --description "docker-runner" \
        --maintenance-note "Test Runner" \
        --tag-list "docker,artemis" \
        --run-untagged="true" \
        --locked="false" \
        --access-level="not_protected"

    You should now find the runner in the list of runners (``http://host.docker.internal/admin/runners``)

.. note::
    Adding a runner in a production setup works the same way.
    The GitLab administration page also contains alternative ways of setting up GitLab runners.
    All variants should allow the passing of the configuration options ``tag-list``, ``run-untagged``, ``locked``, and ``access-level`` similarly as in the Docker command above.
    If forgotten, Artemis might not use this runner to run the tests for exercise submissions.


Artemis
^^^^^^^

.. note::
    Make sure that the database is empty and contains no data from previous Artemis runs.

1. Generate authentication token
    The notification plugin has to authenticate to upload the test results.
    Therefore, a random string has to be generated, e.g., via a password generator.
    This should be used in place of ``notification-plugin-token`` value in the example config below.
2. Configure Artemis
    For local development, copy the following configuration into the ``application-local.yml`` file and adapt it with the values from the previous steps.

    .. code:: yaml

        artemis:
            user-management:
                use-external: false
                internal-admin:
                    username: artemis_admin
                    password: gHn7JlggD9YPiarOEJSx19EFp2BDkkq9
                login:
                    account-name: TUM
            version-control:
                url: http://host.docker.internal:80
                user: root
                password: password # change this value
                token: gitlab-personal-access-token # change this value
            continuous-integration:
                build-timeout: 30
                artemis-authentication-token-value: notification-plugin-token # change this value
            git:
                name: Artemis
                email: artemis.in@tum.de
        server:
            url: http://host.docker.internal:8080

.. note::
    In GitLab, the password of a user must not be the same as the username and must fulfill specific requirements.
    Therefore, there is a random password in the example above.

3. Start Artemis
    Start Artemis with the ``gitlab`` and ``gitlabci`` profile.
