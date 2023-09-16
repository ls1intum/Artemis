.. _admin_generalSetupTips:

General Production Setup Tips
=============================

Automatic Cleanup
-----------------

Artemis runs an automatic scheduled task every night at 03:00 AM that removes

- old local copies of cloned Git repositories,
- no longer used build plans on the continuous integration service.

Take this into consideration when scheduling your own automatic jobs like backups, system updates, and reboots.


Maintenance Page
----------------

In an nginx proxy, you can define a fallback page that is shown when Artemis is not reachable.
Add the special location and ``error_page`` directive to the ``server`` section for Artemis as shown below.
Place the webpage that should be shown in case of Artemis being unreachable (in this case ``/srv/http/service-down.html``) somewhere readable by the system user that runs nginx.

.. code-block::

    server {
        location /service-down.html {
            root /srv/http;
            internal;
        }

        location / {
            # regular proxy configuration
        }

        error_page 501 502 503 /service_down.html;
    }

## Gather all Docker Compose-related tips here which are not relevant for developers!
.. _docker_compose_setup_prod:

Docker Compose Setup
--------------------

The :ref:`development section of the documentation <docker_compose_setup_dev>` provides a introduction to
Docker Compose setups for Artemis.
This section provides additional information for administrators.

File Permissions
^^^^^^^^^^^^^^^^
If you use the production Docker Compose Setups (``artemis-prod-*.yml``) with bind mounts change
the file permissions accordingly:

.. code:: bash

   sudo chown -R $(id -u):70 docker/.docker-data/artemis-postgres-data
   sudo chown -R $(id -u):999 docker/.docker-data/artemis-mysql-data
   sudo chown -R $(id -u):1337 docker/.docker-data/artemis-data
