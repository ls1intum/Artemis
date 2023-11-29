Security
========


Passwords
---------

The Artemis configuration files contain a few default passwords and secrets
that have to be overridden in your own configuration files or via environment
variables (`Spring relaxed binding <https://github.com/spring-projects/spring-boot/wiki/Relaxed-Binding-2.0>`_).

.. code-block:: yaml

    artemis:
        user-management:
            internal-admin:
                username: "artemis-admin"
                # can be changed later, Artemis will update the password in the database
                # and connected systems on the next start
                password: "artemis-admin"
    jhipster:
        security:
            authentication:
                jwt:
                    # used to sign the JWT tokens for user authentication
                    # can be changed later, will require all users to log in again
                    #
                    # encoded using Base64 (you can use `echo 'secret-key'|base64` on your command line)
                    base64-secret: ""
        registry:
            password: "change-me"  # only for distributed setups with multiple Artemis instances

    spring:
        prometheus:
            # if Prometheus monitoring is enabled: a comma-separated list of
            # IPs that are allowed to access the metrics endpoint
            monitoring-ip: "127.0.0.1"
        websocket:
            broker:
                username: "guest"  # only for distributed setups
                password: "guest"  # only for distributed setups


.. note::

    The usernames/passwords for external systems (Bamboo, Bitbucket, GitLab,
    Jenkins, …) are not listed here since the general setup documentation
    describes how to set up those systems.
    Without replacing the default values the connection to them will not work.


.. note::

    Ensure restrictive access to the configuration files so that access is only
    possible for the system account that runs Artemis and administrators.

