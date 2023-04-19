.. _admin_generalSetupTips:

Additional Production Setup Tips
================================

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
