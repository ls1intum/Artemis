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


Starting Artemis via a Systemd Service
--------------------------------------

This setup is recommended for production instances as it registers Artemis as a service and e.g. enables auto-restarting
of Artemis after the VM running Artemis has been restarted.
Alternatively, you could look at the section below about
`deploying artemis as docker container <#run-the-server-via-docker>`__.

This is a service file that works on Debian/Ubuntu assuming you have created a system user ``artemis`` which has
permissions in the ``/opt/artemis/`` directory that contains the ``Artemis.war`` file:

::

   [Unit]
   Description=Artemis
   After=syslog.target

   [Service]
   User=artemis
   WorkingDirectory=/opt/artemis
   ExecStart=/usr/bin/java \
     -Djdk.tls.ephemeralDHKeySize=2048 \
     -DLC_CTYPE=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Dsun.jnu.encoding=UTF-8 \
     -Djava.security.egd=file:/dev/./urandom \
     -Xmx2048m \
     --add-modules java.se \
     --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
     --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens java.management/sun.management=ALL-UNNAMED \
     --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
     -jar Artemis.war \
     --spring.profiles.active=prod,bamboo,bitbucket,jira,ldap,scheduling,openapi
   SuccessExitStatus=143
   StandardOutput=/opt/artemis/artemis.log  # remove to use default journald logging/cleanup mechanisms
   StandardError=inherit

   [Install]
   WantedBy=multi-user.target


The following parts might also be useful for other (production) setups, even if this service file is not used:

- ``-Djava.security.egd=file:/dev/./urandom``: This is required if repositories are cloned via SSH from the VCS.
   The default (pseudo-)random-generator ``/dev/random`` is blocking which results in very bad performance when using
   SSH due to lack of entropy.


The file should be placed at ``/etc/systemd/system/artemis.service`` and after running ``sudo systemctl daemon-reload``,
you can start the service using ``sudo systemctl start artemis``.

You can stop the service using ``sudo service artemis stop`` and restart it using ``sudo service artemis restart``.

Logs can be fetched using ``sudo journalctl -u artemis -f -n 200``.


Nginx Configuration Templates
-----------------------------

There are some placeholders in the following configurations.
Replace them with your setup specific values.

GitLab
^^^^^^

::

   server {
       listen 443 ssl http2;
       server_name your.gitlab.domain;
       ssl_session_cache shared:GitLabSSL:10m;
       include /etc/nginx/common/common_ssl.conf;
       add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload";
       add_header X-Frame-Options DENY;
       add_header Referrer-Policy same-origin;
       client_max_body_size 10m;
       client_body_buffer_size 1m;

       location / {
           proxy_pass              http://localhost:<your exposed GitLab HTTP port (default 80)>;
           proxy_read_timeout      300;
           proxy_connect_timeout   300;
           proxy_http_version      1.1;
           proxy_redirect          http://         https://;

           proxy_set_header    Host                $http_host;
           proxy_set_header    X-Real-IP           $remote_addr;
           proxy_set_header    X-Forwarded-For     $proxy_add_x_forwarded_for;
           proxy_set_header    X-Forwarded-Proto   $scheme;

           gzip off;
       }
   }


.. _jenkins-1:

Jenkins
^^^^^^^

::

   server {
       listen 443 ssl http2;
       server_name your.jenkins.domain;
       ssl_session_cache shared:JenkinsSSL:10m;
       include /etc/nginx/common/common_ssl.conf;
       add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload";
       add_header X-Frame-Options DENY;
       add_header Referrer-Policy same-origin;
       client_max_body_size 10m;
       client_body_buffer_size 1m;

       location / {
           proxy_pass              http://localhost:<your exposed Jenkins HTTP port (default 8081)>;
           proxy_set_header        Host                $host:$server_port;
           proxy_set_header        X-Real-IP           $remote_addr;
           proxy_set_header        X-Forwarded-For     $proxy_add_x_forwarded_for;
           proxy_set_header        X-Forwarded-Proto   $scheme;
           proxy_redirect          http://             https://;

           # Required for new HTTP-based CLI
           proxy_http_version 1.1;
           proxy_request_buffering off;
           proxy_buffering off; # Required for HTTP-based CLI to work over SSL

           # workaround for https://issues.jenkins-ci.org/browse/JENKINS-45651
           add_header 'X-SSH-Endpoint' 'your.jenkins.domain.com:50022' always;
       }

       error_page 502 /502.html;
       location /502.html {
           root /usr/share/nginx/html;
           internal;
       }
   }

/etc/nginx/common/common_ssl.conf
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you haven’t done so, generate the DH param file:
``sudo openssl dhparam -out /etc/nginx/dhparam.pem 4096``

::

   ssl_certificate     <path to your fullchain certificate>;
   ssl_certificate_key <path to the private key of your certificate>;
   ssl_protocols       TLSv1.2 TLSv1.3;
   ssl_dhparam /etc/nginx/dhparam.pem;
   ssl_prefer_server_ciphers   on;
   ssl_ciphers ECDH+CHACHA20:EECDH+AESGCM:EDH+AESGCM:!AES128;
   ssl_ecdh_curve secp384r1;
   ssl_session_timeout  10m;
   ssl_session_cache shared:SSL:10m;
   ssl_session_tickets off;
   ssl_stapling on;
   ssl_stapling_verify on;
   resolver <if you have any, specify them here> valid=300s;
   resolver_timeout 5s;


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
