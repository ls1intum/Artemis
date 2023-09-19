.. _Jenkins and GitLab Setup:

Jenkins and GitLab Setup
------------------------

This section describes how to set up a programming exercise environment
based on Jenkins and GitLab. Optional commands are in curly brackets ``{}``.

The following assumes that all instances run on separate servers. If you
have one single server, or your own NGINX instance, just skip all NGINX
related steps and use the configurations provided under *Separate NGINX
Configurations*

**If you want to setup everything on your local development computer,
ignore all NGINX related steps.** **Just make sure that you use
unique port mappings for your Docker containers (e.g.** ``8081`` **for
GitLab,** ``8082`` **for Jenkins,** ``8080`` **for Artemis)**

**Prerequisites:**

* `Docker <https://docs.docker.com/install>`__

 Make sure that docker has enough memory (~ 6GB). To adapt it, go to ``Preferences -> Resources`` and restart Docker.

.. contents:: Content of this section
    :local:
    :depth: 3

Artemis
^^^^^^^

In order to use Artemis with Jenkins as **Continuous Integration**
Server and Gitlab as **Version Control** Server, you have to configure
the file ``application-prod.yml`` (Production Server) or
``application-artemis.yml`` (Local Development) accordingly. Please note
that all values in ``<..>`` have to be configured properly. These values
will be explained below in the corresponding sections. If you want to set up a local environment, copy the values
below into your ``application-artemis.yml`` or ``application-local.yml`` file (the latter is recommended), and follow
the `Gitlab Server Quickstart <#gitlab-server-quickstart>`__ guide.

.. code:: yaml

   artemis:
    course-archives-path: ./exports/courses
    repo-clone-path: ./repos
    repo-download-clone-path: ./repos-download
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
        url: http://localhost:8081
        user: root
        password: artemis_admin # created in Gitlab Server Quickstart step 2
        token: artemis-gitlab-token # generated in Gitlab Server Quickstart steps 4 and 5
    continuous-integration:
        user: artemis_admin
        password: artemis_admin
        url: http://localhost:8082
        secret-push-token: AQAAABAAAAAg/aKNFWpF9m2Ust7VHDKJJJvLkntkaap2Ka3ZBhy5XjRd8s16vZhBz4fxzd4TH8Su # pre-generated or replaced in Automated Jenkins Server step 3
        vcs-credentials: artemis_gitlab_admin_credentials
        artemis-authentication-token-key: artemis_notification_plugin_token
        artemis-authentication-token-value: artemis_admin
        build-timeout: 30
    git:
        name: Artemis
        email: artemis.in@tum.de
   jenkins:
       internal-urls:
           ci-url: http://jenkins:8080
           vcs-url: http://gitlab:80
       use-crumb: false
   server:
        port: 8080
        url: http://172.17.0.1:8080 # `http://host.docker.internal:8080` for Windows

In addition, you have to start Artemis with the profiles ``gitlab`` and
``jenkins`` so that the correct adapters will be used, e.g.:

::

   --spring.profiles.active=dev,jenkins,gitlab,artemis,scheduling

Please read :ref:`Server Setup` for more details.

For a local setup on Windows you can use `http://host.docker.internal` appended
by the chosen ports as the version-control and continuous-integration url.

Make sure to change the ``server.url`` value in ``application-dev.yml``
or ``application-prod.yml`` accordingly. This value will be used for the
communication hooks from GitLab to Artemis and from Jenkins to Artemis.
In case you use a different port than 80 (http) or 443 (https) for the
communication, you have to append it to the ``server.url`` value,
e.g. \ ``127.0.0.1:8080``.

When you start Artemis for the first time, it will automatically create
an admin user.

**Note:** Sometimes Artemis does not generate the admin user which may lead to a startup
error. You will have to create the user manually in the MySQL database and in GitLab. Make sure
both are set up correctly and follow these steps:

1.  Use the tool mentioned above to generate a password hash.
2.  Connect to the database via a client like `MySQL Workbench <https://dev.mysql.com/downloads/workbench/>`__
    and execute the following query to create the user. Replace `artemis_admin` and `HASHED_PASSWORD` with your
    chosen username and password:

    .. code:: sql

        INSERT INTO `artemis`.`jhi_user` (`id`,`login`,`password_hash`,`first_name`,`last_name`,`email`,
        `activated`,`lang_key`,`activation_key`,`reset_key`,`created_by`,`created_date`,`reset_date`,
        `last_modified_by`,`last_modified_date`,`image_url`,`last_notification_read`,`registration_number`)
        VALUES (1,"artemis_admin","HASHED_PASSWORD","artemis","administrator","artemis_admin@localhost",
        1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
3. Give the user admin and user roles:

    .. code:: sql

        INSERT INTO `artemis`.`jhi_user_authority` (`user_id`, `authority_name`) VALUES (1,"ROLE_ADMIN");
        INSERT INTO `artemis`.`jhi_user_authority` (`user_id`, `authority_name`) VALUES (1,"ROLE_USER");

4. Create a user in Gitlab (``http://your-gitlab-domain/admin/users/new``) and make sure that the username and
email are the same as the user from the database:

.. figure:: setup/jenkins-gitlab/gitlab_admin_user.png

5. Edit the new admin user (``http://your-gitlab-domain/admin/users/artemis_admin/edit``) to set the password to the
same value as in the database:

.. figure:: setup/jenkins-gitlab/gitlab_admin_user_password.png

Starting the Artemis server should now succeed.

GitLab
^^^^^^

GitLab Server Quickstart
""""""""""""""""""""""""

The following steps describes how to set up the GitLab server in a semi-automated way.
This is ideal as a quickstart for developers. For a more detailed setup, see
`Manual Gitlab Server Setup <#manual-gitlab-server-setup>`__.
In a production setup, you have to at least change the root password (by either specifying it in step 1 or extracting
the random password in step 2) and generate random access tokens (instead of the pre-defined values).
Set the variable ``GENERATE_ACCESS_TOKENS`` to ``true`` in the ``gitlab-local-setup.sh`` script and use the generated
tokens instead of the predefined ones.

1. Start the GitLab container defined in `docker/gitlab-jenkins-mysql.yml` by running

   ::

        GITLAB_ROOT_PASSWORD=QLzq3QvpD1Zbq7A1VWvw docker compose -f docker/<Jenkins setup to be launched>.yml up --build -d gitlab

   If you want to generate a random password for the ``root`` user, remove the part before ``docker compose`` from
   the command. GitLab passwords must not contain commonly used combinations of words and letters.

   The file uses the ``GITLAB_OMNIBUS_CONFIG`` environment variable to configure the Gitlab instance after the container
   is started.
   It disables prometheus monitoring, sets the ssh port to ``2222``, and adjusts the monitoring endpoint whitelist
   by default.

2. Wait a couple of minutes since GitLab can take some time to set up. Open the instance in your browser
   (usually ``http://localhost:8081``).

   You can then login using the username ``root`` and your password (which defaults to ``artemis_admin``,
   if you used the command from above).
   If you did not specify the password, you can get the initial one using:

   .. code:: bash

        docker compose -f docker/<Jenkins setup to be launched>.yml exec gitlab cat /etc/gitlab/initial_root_password

3. Insert the GitLab root user password in the file ``application-local.yml`` (in src/main/resources) and insert
   the GitLab admin account.
   If you copied the template from above and used the default password, this is already done for you.

   .. code:: yaml

       artemis:
           version-control:
               url: http://localhost:8081
               user: root
               password: your.gitlab.admin.password # artemis_admin

4. You now need to create an admin access token. You can do that using the following command (which takes a while
   to execute):

   ::

        docker compose -f docker/<Jenkins setup to be launched>.yml exec gitlab gitlab-rails runner "token = User.find_by_username('root').personal_access_tokens.create(scopes: ['api', 'read_api', 'read_user', 'read_repository', 'write_repository', 'sudo'], name: 'Artemis Admin Token', expires_at: 365.days.from_now); token.set_token('artemis-gitlab-token'); token.save!"

   | You can also manually create in by navigating to ``http://localhost:8081/-/profile/personal_access_tokens?name=Artemis+Admin+token&scopes=api,read_api,read_user,read_repository,write_repository,sudo`` and
     generate a token with all scopes.
   | Copy this token into the ``ADMIN_PERSONAL_ACCESS_TOKEN`` field in the
     ``docker/gitlab/gitlab-local-setup.sh`` file.
   | If you used the command to generate the token, you don't have to change the ``gitlab-local-setup.sh`` file.

5. Adjust the GitLab setup by running, this will configure GitLab's network setting to allow local requests:

   ::

        docker compose -f docker/<Jenkins setup to be launched>.yml exec gitlab /bin/sh -c "sh /gitlab-local-setup.sh"

   This script can also generate random access tokens, which should be used in a production setup. Change the
   variable ``$GENERATE_ACCESS_TOKENS`` to ``true`` to generate the random tokens and insert them into the Artemis
   configuration file.

6. You're done! Follow the `Automated Jenkins Server Setup <#automated-jenkins-server-setup>`__ section for
   configuring Jenkins.

Manual GitLab Server Setup
""""""""""""""""""""""""""

GitLab provides no possibility to set a users password via API without forcing the user to change it afterwards
(see `Issue 19141 <https://gitlab.com/gitlab-org/gitlab/-/issues/19141>`__).
Therefore, you may want to patch the official gitlab docker image.
Thus, you can use the following Dockerfile:

.. code:: dockerfile

    FROM gitlab/gitlab-ce:latest
    RUN sed -i '/^.*user_params\[:password_expires_at\] = Time.current if admin_making_changes_for_another_user.*$/s/^/#/' /opt/gitlab/embedded/service/gitlab-rails/lib/api/users.rb


This Dockerfile disables the mechanism that sets the password to expired state after changed via API.
If you want to use this custom image, you have to build the image and replace all occurrences of
``gitlab/gitlab-ce:latest`` in the following instructions by your chosen image name.


1. Pull the latest GitLab Docker image (only if you don't use your custom gitlab image)

   ::

       docker pull gitlab/gitlab-ce:latest

Start GitLab
############

2. Run the image (and change the values for hostname and ports). Add
   ``-p 2222:22`` if cloning/pushing via ssh should be possible. As
   GitLab runs in a docker container and the default port for SSH (22)
   is typically used by the host running Docker, we change the port
   GitLab uses for SSH to ``2222``. This can be adjusted if needed.

   Make sure to remove the comments from the command before running it.

   ::

       docker run -itd --name gitlab \
           --hostname your.gitlab.domain.com \   # Specify the hostname
           --restart always \
           -m 3000m \                            # Optional argument to limit the memory usage of Gitlab
           -p 8081:80 -p 443:443 \               # Alternative 1: If you are NOT running your own NGINX instance
           -p <some port of your choosing>:80 \  # Alternative 2: If you ARE running your own NGINX instance
           -p 2222:22 \                          # Remove this if cloning via SSH should not be supported
           -v gitlab_data:/var/opt/gitlab \
           -v gitlab_logs:/var/log/gitlab \
           -v gitlab_config:/etc/gitlab \
           gitlab/gitlab-ce:latest

3. Wait a couple of minutes until the container is deployed and GitLab
   is set up, then open the instance in you browser.
   You can get the initial password for the ``root`` user using
   ``docker exec gitlab cat /etc/gitlab/initial_root_password``.

4. We recommend to rename the ``root`` admin user to ``artemis``. To rename
   the user, click on the image on the top right and select ``Settings``.
   Now select ``Account`` on the left and change the username. Use the
   same password in the Artemis configuration file
   ``application-artemis.yml``

   .. code:: yaml

       artemis:
           version-control:
               user: artemis
               password: the.password.you.chose

5. **If you run your own NGINX or if you install Gitlab on a local development computer, then skip the next steps (6-7)**

6. Configure GitLab to automatically generate certificates using
   LetsEncrypt. Edit the GitLab configuration

   ::

       docker exec -it gitlab /bin/bash
       nano /etc/gitlab/gitlab.rb

   And add the following part

   ::

       letsencrypt['enable'] = true                          # GitLab 10.5 and 10.6 require this option
       external_url "https://your.gitlab.domain.com"         # Must use https protocol
       letsencrypt['contact_emails'] = ['gitlab@your.gitlab.domain.com'] # Optional

       nginx['redirect_http_to_https'] = true
       nginx['redirect_http_to_https_port'] = 80

7. Reconfigure GitLab to generate the certificate.

   ::

       # Save your changes and finally run
       gitlab-ctl reconfigure

   If this command fails, try using

   ::

       gitlab-ctl renew-le-certs

8. Login to GitLab using the Artemis admin account and go to the profile
   settings (upper right corner → *Preferences*)

   .. figure:: setup/jenkins-gitlab/gitlab_preferences_button.png
      :align: center

GitLab Access Token
###################

9.  Go to *Access Tokens*

   .. figure:: setup/jenkins-gitlab/gitlab_access_tokens_button.png
      :align: center

10. Create a new token named “Artemis” and give it rights ``api``, ``read_api``, ``read_user``, ``read_repository``, ``write_repository``, and ``sudo``.

   .. figure:: setup/jenkins-gitlab/artemis_gitlab_access_token.png
      :align: center

11. Copy the generated token and insert it into the Artemis
    configuration file *application-artemis.yml*

    .. code:: yaml

       artemis:
           version-control:
               token: your.generated.api.token

12. (Optional, only necessary for local setup) Allow outbound requests to local network

    There is a known limitation for the local setup: webhook URLs for the
    communication between GitLab and Artemis and between GitLab and Jenkins
    cannot include local IP addresses. This option can be deactivated in
    GitLab on ``<https://gitlab-url>/admin/application_settings/network`` →
    Outbound requests. Another possible solution is to register a local URL,
    e.g. using `ngrok <https://ngrok.com/>`__, to be available over a domain
    the Internet.

13. Adjust the monitoring-endpoint whitelist. Run the following command

    ::

           docker exec -it gitlab /bin/bash

    Then edit the GitLab configuration

    ::

           nano /etc/gitlab/gitlab.rb

    Add the following lines

    ::

       gitlab_rails['monitoring_whitelist'] = ['0.0.0.0/0']
       gitlab_rails['gitlab_shell_ssh_port'] = 2222

    This will disable the firewall for all IP addresses. If you only want to
    allow the server that runs Artemis to query the information, replace
    ``0.0.0.0/0`` with ``ARTEMIS.SERVER.IP.ADDRESS/32``

    If you use SSH and use a different port than ``2222``, you have to
    adjust the port above.

14. Disable prometheus.
    As we encountered issues with the Prometheus log files not being deleted and therefore filling up the disk space,
    we decided to disable Prometheus within GitLab.
    If you also want to disable prometheus, edit the configuration again using

    ::

        nano /etc/gitlab/gitlab.rb

    and add the following line

    ::

        prometheus_monitoring['enable'] = false

    The issue with more details can be found `here <https://gitlab.com/gitlab-org/omnibus-gitlab/-/issues/4166>`__.

15. Add a SSH key for the admin user.

    Artemis can clone/push the repositories during setup and for the online code editor using SSH.
    If the SSH key is not present, the username + token will be used as fallback (and all git operations will use
    HTTP(S) instead of SSH).

    You first have to create a SSH key (locally), e.g. using ``ssh-keygen`` (more information on how to create a SSH
    key can be found e.g. at `ssh.com <https://www.ssh.com/ssh/keygen/>`__ or
    at `gitlab.com <https://docs.gitlab.com/ee/ssh/#rsa-ssh-keys>`__).

    The list of supported ciphers can be found at `Apache Mina <https://github.com/apache/mina-sshd>`__.

    It is recommended to use a password to secure the private key, but it is not mandatory.

    Please note that the private key file **must** be named ``ìd_rsa``, ``id_dsa``, ``id_ecdsa`` or ``id_ed25519``,
    depending on the ciphers used.

    You now have to extract the public key and add it to GitLab.
    Open the public key file (usually called ``id_rsa.pub`` (when using RSA)) and copy it's content (you can also
    use ``cat id_rsa.pub`` to show the public key).

    Navigate to ``GITLAB-URL/-/profile/keys`` and add the SSH key by pasting the content of the public key.

    ``<ssh-key-path>`` is the path to the folder containing the ``id_rsa`` file (but without the filename). It will
    be used in the configuration of Artemis to specify where Artemis should look for the key and store
    the ``known_hosts`` file.

    ``<ssh-private-key-password>`` is the password used to secure the private key. It is also needed for the
    configuration of Artemis, but can be omitted if no password was set (e.g. for development environments).

16. Reconfigure GitLab

    ::

        gitlab-ctl reconfigure

Upgrade GitLab
""""""""""""""

You can upgrade GitLab by downloading the latest Docker image and
starting a new container with the old volumes:

    ::

        docker stop gitlab
        docker rename gitlab gitlab_old
        docker pull gitlab/gitlab-ce:latest

See https://hub.docker.com/r/gitlab/gitlab-ce/ for the latest version.
You can also specify an earlier one.

Note that **upgrading to a major version** may require following an upgrade path. You can view supported paths
`here <https://docs.gitlab.com/ee/update/#upgrade-paths>`__.

Start a GitLab container just as described in `Start-Gitlab <#start-gitlab>`__ and wait for a couple of minutes. GitLab
should configure itself automatically. If there are no issues, you can
delete the old container using ``docker rm gitlab_old`` and the olf
image (see ``docker images``) using ``docker rmi <old-image-id>``.
You can also remove all old images using ``docker image prune -a``

Jenkins
^^^^^^^

Automated Jenkins Server Setup
""""""""""""""""""""""""""""""

The following steps describe how to deploy a pre-configured version of the Jenkins server.
This is ideal as a quickstart for developers. For a more detailed setup, see
`Manual Jenkins Server Setup <#manual-jenkins-server-setup>`__.
In a production setup, you have to at least change the user credentials (in the file ``jenkins-casc-config.yml``) and
generate random access tokens and push tokens.

1. Create a new access token in GitLab named ``Jenkins`` and give it **api** and **read_repository** rights. You can
do either do it manually or using the following command:

    ::

        docker compose -f docker/<Jenkins setup to be launched>.yml exec gitlab gitlab-rails runner "token = User.find_by_username('root').personal_access_tokens.create(scopes: ['api', 'read_repository'], name: 'Jenkins', expires_at: 365.days.from_now); token.set_token('jenkins-gitlab-token'); token.save!"



2. You can now first build and deploy Jenkins, then you can also start the other services which weren't started yet:

    ::

       JAVA_OPTS=-Djenkins.install.runSetupWizard=false docker compose -f docker/<Jenkins setup to be launched>.yml up --build -d jenkins
       docker compose -f docker/<Jenkins setup to be launched>.yml up -d

   Jenkins is then reachable under ``http://localhost:8082/`` and you can login using the credentials specified
   in ``jenkins-casc-config.yml`` (defaults to ``artemis_admin`` as both username and password).

3. You need to generate the `secret-push-token`.

   ..
       Workaround as long as Github Issue 5973 (Default Push Notifications GitLab → Jenkins not working)
       for now just generate the secret-push-token manually

   As there is currently an `open issue with the presets for Jenkins in Development environments <https://github.com/ls1intum/Artemis/issues/5973>`__,
   follow the steps described in
   `Gitlab to Jenkins push notification token <#gitlab-to-jenkins-push-notification-token>`__ to generate the token.
   In a production setup, you should use a random ``master.key`` in the file ``gitlab-jenkins-mysql.yml``.

4. The `application-local.yml` must be adapted with the values configured in ``jenkins-casc-config.yml``:

.. code:: yaml

    artemis:
        user-management:
            use-external: false
            internal-admin:
                username: artemis_admin
                password: artemis_admin
        version-control:
            url: http://localhost:8081
            user: artemis_admin
            password: artemis_admin
        continuous-integration:
            user: artemis_admin
            password: artemis_admin
            url: http://localhost:8082
            secret-push-token: # pre-generated or replaced in Automated Jenkins Server step 3
            vcs-credentials: artemis_gitlab_admin_credentials
            artemis-authentication-token-key: artemis_notification_plugin_token
            artemis-authentication-token-value: artemis_admin

5. Open the ``src/main/resources/config/application-jenkins.yml`` and change the following:
   Again, if you are using a development setup, the template in the beginning of this page already contains the
   correct values.

.. code:: yaml

    jenkins:
        internal-urls:
            ci-url: http://jenkins:8080
            vcs-url: http://gitlab:80

6. You're done. You can now run Artemis with the GitLab/Jenkins environment.

Manual Jenkins Server Setup
"""""""""""""""""""""""""""

1. Pull the latest Jenkins LTS Docker image

   Run the following command to get the latest jenkins LTS docker image.

   ::

       docker pull jenkins/jenkins:lts

2. Create a custom docker image

   In order to install and use Maven with Java in the Jenkins container,
   you have to first install maven, then download Java and finally
   configure Maven to use Java instead of the default version.
   You also need to install Swift and SwiftLint if you want to be able to
   create Swift programming exercises.

   To perform all these steps automatically, you can prepare a Docker
   image:

   Create a Dockerfile with the content found `here <docker/jenkins/Dockerfile>`.
   Copy it in a file named ``Dockerfile``, e.g. in
   the folder ``/opt/jenkins/`` using ``vim Dockerfile``.

   Now run the command ``docker build --no-cache -t jenkins-artemis .``

   This might take a while because Docker will download Java, but this
   is only required once.

3. **If you run your own NGINX or if you install Jenkins on a local development computer, then skip the next steps (4-7)**

4. Create a file increasing the maximum file size for the nginx proxy.
   The nginx-proxy uses a default file limit that is too small for the
   plugin that will be uploaded later. **Skip this step if you have your
   own NGINX instance.**

   ::

       echo "client_max_body_size 16m;" > client_max_body_size.conf

5. The NGINX default timeout is pretty low. For plagiarism check and unlocking student repos for the exam a higher
   timeout is advisable. Therefore we write our own nginx.conf and load it in the container.


   .. code:: nginx

            user  nginx;
            worker_processes  auto;

            error_log  /var/log/nginx/error.log warn;
            pid        /var/run/nginx.pid;


            events {
                worker_connections  1024;
            }


            http {
                include       /etc/nginx/mime.types;
                default_type  application/octet-stream;

                log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                                  '$status $body_bytes_sent "$http_referer" '
                                  '"$http_user_agent" "$http_x_forwarded_for"';

                access_log  /var/log/nginx/access.log  main;

                fastcgi_read_timeout 300;
                proxy_read_timeout 300;

                sendfile        on;
                #tcp_nopush     on;

                keepalive_timeout  65;

                #gzip  on;

                include /etc/nginx/conf.d/*.conf;
            }
            daemon off

6. Run the NGINX proxy docker container, this will automatically setup
   all reverse proxies and force https on all connections. (This image
   would also setup proxies for all other running containers that have
   the VIRTUAL_HOST and VIRTUAL_PORT environment variables). **Skip this
   step if you have your own NGINX instance.**

   ::

       docker run -itd --name nginx_proxy \
           -p 80:80 -p 443:443 \
           --restart always \
           -v /var/run/docker.sock:/tmp/docker.sock:ro \
           -v /etc/nginx/certs \
           -v /etc/nginx/vhost.d \
           -v /usr/share/nginx/html \
           -v $(pwd)/client_max_body_size.conf:/etc/nginx/conf.d/client_max_body_size.conf:ro \
           -v $(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro \
           jwilder/nginx-proxy

7. The nginx proxy needs another docker-container to generate
   letsencrypt certificates. Run the following command to start it (make
   sure to change the email-address). **Skip this step if you have your
   own NGINX instance.**

   ::

       docker run --detach \
           --name nginx_proxy-letsencrypt \
           --volumes-from nginx_proxy \
           --volume /var/run/docker.sock:/var/run/docker.sock:ro \
           --env "DEFAULT_EMAIL=mail@yourdomain.tld" \
           jrcs/letsencrypt-nginx-proxy-companion

Start Jenkins
#############

8.  Run Jenkins by executing the following command (change the hostname
    and choose which port alternative you need)

    ::

        docker run -itd --name jenkins \
            --restart always \
            -v jenkins_data:/var/jenkins_home \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -v /usr/bin/docker:/usr/bin/docker:ro \
            -e VIRTUAL_HOST=your.jenkins.domain -e VIRTUAL_PORT=8080 \    # Alternative 1: If you are NOT using a separate NGINX instance
            -e LETSENCRYPT_HOST=your.jenkins.domain \                     # Only needed if Alternative 1 is used
            -p 8082:8080 \                                                # Alternative 2: If you ARE using a separate NGINX instance OR you ARE installing Jenkins on a local development computer
            -u root \
            jenkins/jenkins:lts

    If you still need the old setup with Python & Maven installed locally, use ``jenkins-artemis`` instead of
    ``jenkins/jenkins:lts``.
    Also note that you can omit the ``-u root``, ``-v /var/run/docker.sock:/var/run/docker.sock`` and
    ``-v /usr/bin/docker:/usr/bin/docker:ro`` parameters, if you do not want to run Docker builds on the Jenkins controller
    (but e.g. use remote agents).

9. Open Jenkins in your browser (e.g. ``localhost:8082``) and setup the
    admin user account (install all suggested plugins). You can get the
    initial admin password using the following command.

    ::

       # Jenkins highlights the password in the logs, you can't miss it
       docker logs -f jenkins
       or alternatively
       docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword

10. Set the chosen credentials in the Artemis configuration
    *application-artemis.yml*

    .. code:: yaml

       artemis:
           continuous-integration:
               user: your.chosen.username
               password: your.chosen.password

Required Jenkins Plugins
""""""""""""""""""""""""

**Note:** The custom Jenkins Dockerfile takes advantage of the
`Plugin Installation Manager Tool for Jenkins <https://github.com/jenkinsci/plugin-installation-manager-tool>`__
to automatically install the plugins listed below. If you used the Dockerfile, you can skip these steps and
`Server Notification Plugin <#server-notification-plugin>`__.
The list of plugins is maintained in ``docker/jenkins/plugins.yml``.


You will need to install the following plugins (apart from the
recommended ones that got installed during the setup process):

1.  `GitLab <https://plugins.jenkins.io/gitlab-plugin/>`__ for enabling
    webhooks to and from GitLab

2.  `Timestamper <https://plugins.jenkins.io/timestamper/>`__ for adding the
    time to every line of the build output (Timestamper might already be installed)

3.  `Pipeline <https://plugins.jenkins.io/workflow-aggregator/>`__ for defining the
    build description using declarative files (Pipeline might already be installed)

    **Note:** This is a suite of plugins that will install multiple plugins

4. `Pipeline Maven <https://plugins.jenkins.io/pipeline-maven/>`__ to use maven within the pipelines. If you want to
   use Docker for your build agents you may also need to install
   `Docker Pipeline <https://plugins.jenkins.io/docker-workflow/>`__ .

5. `Matrix Authorization Strategy Plugin <https://plugins.jenkins.io/matrix-auth/>`__ for configuring permissions
   for users on a project and build plan level (Matrix Authorization Strategy might already be installed).


The plugins above (and the pipeline-setup associated with it) got introduced in Artemis 4.7.3.
If you are using exercises that were created before 4.7.3, you also have to install these plugins:

Please note that this setup is **deprecated** and will be removed in the future.
Please migrate to the new pipeline-setup if possible.

1.  `Multiple SCMs <https://plugins.jenkins.io/multiple-scms/>`__ for combining the
    exercise test and assignment repositories in one build

2.  `Post Build Task <https://plugins.jenkins.io/postbuild-task/>`__ for preparing build
    results to be exported to Artemis

3.  `Xvfb <https://plugins.jenkins.io/xvfb/>`__ for exercises based on GUI
    libraries, for which tests have to have some virtual display

Choose “Download now and install after restart” and checking the
“Restart Jenkins when installation is complete and no jobs are running” box

Timestamper Configuration
"""""""""""""""""""""""""

Go to *Manage Jenkins → System Configuration → Configure*. There you will find the
Timestamper configuration, use the following value for both formats:

::

       '<b>'yyyy-MM-dd'T'HH:mm:ssX'</b> '

.. figure:: setup/jenkins-gitlab/timestamper_config.png
   :align: center

Server Notification Plugin
""""""""""""""""""""""""""

Artemis needs to receive a notification after every build, which
contains the test results and additional commit information. For that
purpose, we developed a Jenkins plugin, that can aggregate and *POST*
JUnit formatted results to any URL.

You can download the current release of the plugin
`here <https://github.com/ls1intum/jenkins-server-notification-plugin/releases>`__
(Download the **.hpi** file). Go to the Jenkins plugin page (*Manage
Jenkins → System Configuration → Plugins*) and install the downloaded file under the
*Advanced settings* tab under *Deploy Plugin*

.. figure:: setup/jenkins-gitlab/jenkins_custom_plugin.png
   :align: center

Jenkins Credentials
"""""""""""""""""""

Go to *Manage Jenkins → Security → Credentials → Jenkins → Global credentials* and create the
following credentials

GitLab API Token
################

1. Create a new access token in GitLab named ``Jenkins`` and give it
   **api** rights and **read_repository** rights. For detailed
   instructions on how to create such a token follow `Gitlab Access
   Token <#gitlab-access-token>`__.

   .. figure:: setup/jenkins-gitlab/gitlab_jenkins_token_rights.png
      :align: center

2. Copy the generated token and create new Jenkins credentials:

   1. **Kind**: GitLab API token
   2. **Scope**: Global
   3. **API token**: *your.copied.token*
   4. Leave the ID field blank
   5. The description is up to you

3. Go to the Jenkins settings *Manage Jenkins → System*. There
   you will find the GitLab settings. Fill in the URL of your GitLab
   instance and select the just created API token in the credentials
   dropdown. After you click on “Test Connection”, everything should
   work fine. If you have problems finding the right URL for your local docker setup,
   you can try `http://host.docker.internal:8081` for Windows or `http://docker.for.mac.host.internal:8081` for Mac
   if GitLab is reachable over port 8081.

   .. figure:: setup/jenkins-gitlab/jenkins_gitlab_configuration.png
      :align: center

Server Notification Token
#########################

1. Create a new Jenkins credential containing the token, which gets send
   by the server notification plugin to Artemis with every build result:

   1. **Kind**: Secret text
   2. **Scope**: Global
   3. **Secret**: *your.secret_token_value* (choose any value you want,
      copy it for the nex step)
   4. Leave the ID field blank
   5. The description is up to you

2. Copy the generated ID of the new credentials and put it into the
   Artemis configuration *application-artemis.yml*

   .. code:: yaml

       artemis:
           continuous-integration:
               artemis-authentication-token-key: the.id.of.the.notification.token.credential

3. Copy the actual value you chose for the token and put it into the
   Artemis configuration *application-artemis.yml*

   .. code:: yaml

       artemis:
           continuous-integration:
               artemis-authentication-token-value: the.actual.value.of.the.notification.token

GitLab Repository Access
########################

1. Create a new Jenkins credentials containing the username and password
   of the GitLab administrator account:

   1. **Kind**: Username with password
   2. **Scope**: Global
   3. **Username**: *the_username_you_chose_for_the_gitlab_admin_user*
   4. **Password**: *the_password_you_chose_for_the_gitlab_admin_user*
   5. Leave the ID field blank
   6. The description is up to you

2. Copy the generated ID (e.g. ``ea0e3c08-4110-4g2f-9c83-fb2cdf6345fa``)
   of the new credentials and put it into the Artemis configuration file
   *application-artemis.yml*

   .. code:: yaml

       artemis:
           continuous-integration:
               vcs-credentials: the.id.of.the.username.and.password.credentials.from.jenkins

GitLab to Jenkins push notification token
"""""""""""""""""""""""""""""""""""""""""

GitLab has to notify Jenkins build plans if there are any new commits to
the repository. The push notification that gets sent here is secured by
a token generated by Jenkins. In order to get this token, you have to do
the following steps:

1.  Create a new item in Jenkins (use the Freestyle project type) and
    name it **TestProject**

2.  In the project configuration, go to *Build Triggers → Build when a
    change is pushed to GitLab* and activate this option

3.  Click on *Advanced*.

4.  You will now have a couple of new options here, one of them being a
    “**Secret token**”.

5.  Click on the “*Generate*” button right below the text box for that
    token.

6.  Copy the generated value, let’s call it **$gitlab-push-token**

7.  Apply these change to the plan (i.e. click on *Apply*)

   .. figure:: setup/jenkins-gitlab/jenkins_test_project.png
      :align: center

8.  Perform a *GET* request to the following URL (e.g. with Postman)
    using Basic Authentication and the username and password you chose
    for the Jenkins admin account:

    ::

        GET https://your.jenkins.domain/job/TestProject/config.xml

    If you have xmllint installed, you can use this command, which will output the ``secret-push-token`` from
    steps 9 and 10 (you may have to adjust the username and password):

    ::

        curl -u artemis_admin:artemis_admin http://localhost:8082/job/TestProject/config.xml | xmllint --nowarning --xpath "//project/triggers/com.dabsquared.gitlabjenkins.GitLabPushTrigger/secretToken/text()" - | sed 's/^.\(.*\).$/\1/'

9.  You will get the whole configuration XML of the just created build
    plan, there you will find the following tag:

    ::

        <secretToken>{$some-long-encrypted-value}</secretToken>

   .. figure:: setup/jenkins-gitlab/jenkins_project_config_xml.png
      :align: center

      Job configuration XML

10. Copy the ``secret-push-token value`` in the line
    ``<secretToken>{secret-push-token}</secretToken>``. This is the encrypted value of the ``gitlab-push-token``
    you generated in step 5.

11. Now, you can delete this test project and input the following values
    into your Artemis configuration *application-artemis.yml* (replace
    the placeholders with the actual values you wrote down)

    .. code:: yaml

       artemis:
           continuous-integration:
               secret-push-token: $some-long-encrypted-value

12. In a local setup, you have to disable CSRF otherwise some API endpoints will return HTTP Status 403 Forbidden.
    This is done be executing the following command:
    ``docker compose -f docker/<Jenkins setup to be launched>.yml exec -T jenkins dd of=/var/jenkins_home/init.groovy < docker/jenkins/jenkins-disable-csrf.groovy``

    The last step is to disable the ``use-crumb`` option in ``application-local.yml``:

    .. code:: yaml

       jenkins:
           use-crumb: false

Upgrading Jenkins
"""""""""""""""""

In order to upgrade Jenkins to a newer version, you need to rebuild the Docker image targeting the new version.
The stable LTS versions can be viewed through the `changelog <https://www.jenkins.io/changelog-stable/>`__
and the corresponding Docker image can be found on
`dockerhub <https://hub.docker.com/r/jenkins/jenkins/tags?page=1&ordering=last_updated>`__.

1. Open the Jenkins Dockerfile and replace the value of ``FROM`` with ``jenkins/jenkins:lts``.
   After running the command ``docker pull jenkins/jenkins:lts``, this will use the latest LTS version
   in the following steps.
   You can also use a specific LTS version.
   For example, if you want to upgrade Jenkins to version ``2.289.2``, you will need to use the
   ``jenkins/jenkins:2.289.2-lts`` image.

2. If you're using ``docker compose``, you can simply use the following command and skip the next steps.

   ::

        docker compose -f docker/<Jenkins setup to be launched>.yml up --build -d

3. Build the new Docker image:

   ::

        docker build --no-cache -t jenkins-artemis .

   The name of the image is called ``jenkins-artemis``.

4. Stop the current Jenkins container (change jenkins to the name of your container):

   ::

        docker stop jenkins

5. Rename the container to ``jenkins_old`` so that it can be used as a backup:

   ::

        docker rename jenkins jenkins_old

6. Run the new Jenkins instance:

   ::

        docker run -itd --name jenkins --restart always \
         -v jenkins_data:/var/jenkins_home \
         -v /var/run/docker.sock:/var/run/docker.sock \
         -p 9080:8080 jenkins-artemis \

7. You can remove the backup container if it's no longer needed:

   ::

        docker rm jenkins_old


You should also update the Jenkins plugins regularly due to security
reasons. You can update them directly in the Web User Interface in the
Plugin Manager.

Build agents
^^^^^^^^^^^^

You can either run the builds locally (that means on the machine that hosts Jenkins) or on remote build agents.

Configuring local build agents
""""""""""""""""""""""""""""""

Go to `Manage Jenkins` → `Nodes` → `Built-In Node` → `Configure`

Configure your master node like this  (adjust the number of executors, if needed). Make sure to add the docker label.

   .. figure:: setup/jenkins-gitlab/jenkins_local_node.png
      :align: center

      Jenkins local node

Alternative local build agents setup using docker
"""""""""""""""""""""""""""""""""""""""""""""""""

An alternative way of adding a build agent that will use docker (similar to the remote agents below) but running
locally, can be done using the jenkins/ssh-agent docker image `docker image <https://hub.docker.com/r/jenkins/ssh-agent>`__.

Prerequisites:

1. Make sure to have Docker `installed <https://docs.docker.com/engine/install/>`__

Agent setup:

1. Create a new SSH key using ``ssh-keygen`` (if a passphrase is added, store it for later)

2. Copy the public key content (e.g. in ~/.ssh/id_rsa.pub)

3. Run::

    docker run -d --name jenkins_agent -v /var/run/docker.sock:/var/run/docker.sock \
    jenkins/ssh-agent:latest "<copied_public_key>"

4. Get the GID of the 'docker' group with ``cat /etc/groups`` and remember it for later

5. Enter the agent's container with ``docker exec -it jenkins_agent bash``

6. Install Docker with ``apt update && apt install docker.io``

7. Check if group 'docker' already exists with ``cat /etc/groups``. If yes, remove it with ``groupdel docker``

8. Add a new 'docker' group with the same GID as seen in point 2 with ``groupadd -g <GID> docker``

9. Add 'jenkins' user to the group with ``usermod -aG docker jenkins``

10. Activate changes with ``newgrp docker``

11. Now check if 'jenkins' has the needed permissions to run docker commands

    1. Log in as 'jenkins' with ``su jenkins``

    2. Try if ``docker inspect <agent_container_name>`` works or if a permission error occurs

    3. If an permission error occurs, try to restart the docker container

12. Now you can exit the container executing ``exit`` twice (the first will exit the jenkins user and
    the second the container)

Add agent in Jenkins:

1. Open Jenkins in your browser (e.g. localhost:8082)

2. Go to Manage Jenkins → Credentials → System → Global credentials (unrestricted) → Add Credentials

    - Kind: SSH Username with private key

    - Scope: Global (Jenkins, nodes, items, all child items, etc)

    - ID: leave blank

    - Description: Up to you

    - Username: jenkins

    - Private Key: <content of the previously generated private key> (e.g /root/.ssh/id_rsa)

    - Passphrase: <the previously entered passphrase> (you can leave it blank if none has been specified)

   .. figure:: setup/jenkins-gitlab/alternative_jenkins_node_credentials.png
      :align: center

3. Go to Manage Jenkins → Nodes → New Node

    - Node name: Up to you (e.g. Docker agent node)

    - Check 'Permanent Agent'

   .. figure:: setup/jenkins-gitlab/alternative_jenkins_node_setup.png
      :align: center

4. Node settings:

    - # of executors: Up to you (e.g. 4)

    - Remote root directory: /home/jenkins/agent

    - Labels: docker

    - Usage: Only build jobs with label expressions matching this node

    - Launch method: Launch agents via SSH

    - Host: output of command ``docker inspect --format '{{ .Config.Hostname }}' jenkins_agent``

    - Credentials: <the previously created SSH credential>

    - Host Key Verification Strategy: Non verifying Verification Strategy

    - Availability: Keep this agent online as much as possible

   .. figure:: setup/jenkins-gitlab/alternative_jenkins_node.png
      :align: center

5. Save the new node

6. Node should now be up and running

Installing remote build agents
""""""""""""""""""""""""""""""
You might want to run the builds on additional Jenkins agents, especially if a large amount of students should use
the system at the same time.
Jenkins supports remote build agents: The actual compilation of the students submissions happens on these
other machines but the whole process is transparent to Artemis.

This guide explains setting up a remote agent on an Ubuntu virtual machine that supports docker builds.

Prerequisites:
1. Install Docker on the remote machine: https://docs.docker.com/engine/install/ubuntu/

2. Add a new user to the remote machine that Jenkins will use: ``sudo adduser --disabled-password --gecos "" jenkins``

3. Add the jenkins user to the docker group (This allows the jenkins user to interact with docker):
   ``sudo usermod -a -G docker jenkins``

4. Generate a new SSH key locally (e.g. using ``ssh-keygen``) and add the public key to the ``.ssh/authorized_keys``
   file of the jenkins user on the agent VM.

5. Validate that you can connect to the build agent machine using SSH and the generated private key and validate that
   you can use docker (`docker ps` should not show an error)

6. Log in with your normal account on the build agent machine and install Java: ``sudo apt install default-jre``

7. Add a new secret in Jenkins, enter private key you just generated and add the passphrase, if set:

   .. figure:: setup/jenkins-gitlab/jenkins_ssh_credentials.png
      :align: center

      Jenkins SSH Credentials

8. Add a new node (select a name and select `Permanent Agent`):
   Set the number of executors so that it matches your machine's specs: This is the number of concurrent builds
   this agent can handle. It is recommended to match the number of cores of the machine,
   but you might want to adjust this later if needed.

   Set the remote root directory to ``/home/jenkins/remote_agent``.

   Set the usage to `Only build jobs with label expressions matching this node`.
   This ensures that only docker-jobs will be built on this agent, and not other jobs.

   Add a label ``docker`` to the agent.

   Set the launch method to `Launch via SSH` and add the host of the machine.
   Select the credentials you just created and select `Manually trusted key Verification Strategy`
   as Host key verification Strategy.
   Save it.


   .. figure:: setup/jenkins-gitlab/jenkins_node.png
      :align: center

      Add a Jenkins node

9. Wait for some moments while jenkins installs it's remote agent on the agent's machine.
   You can track the progress using the `Log` page when selecting the agent. System information should also be available.

10. Change the settings of the master node to be used only for specific jobs.
    This ensures that the docker tasks are not executed on the master agent but on the remote agent.


   .. figure:: setup/jenkins-gitlab/jenkins_master_node.png
      :align: center

      Adjust Jenkins master node settings

11. You are finished, the new agent should now also process builds.


Jenkins User Management
^^^^^^^^^^^^^^^^^^^^^^^

Artemis supports user management in Jenkins as of version 4.11.0. Creating an account in Artemis will also create an
account on Jenkins using the same password. This enables users to login and access Jenkins. Updating and/or deleting
users from Artemis will also lead to updating and/or deleting from Jenkins.

Unfortunately, Jenkins does not provide a Rest API for user management which present the following **caveats**:

 - The username of a user is treated as a unique identifier in Jenkins.
 - It's not possible to update an existing user with a single request.
   We update by deleting the user from Jenkins and recreating it with the updated data.
 - In Jenkins, users are created in an on-demand basis.
   For example, when a build is performed, its change log is computed and as a result commits from users
   who Jenkins has never seen may be discovered and created.
 - Since Jenkins users may be re-created automatically, issues may occur such as 1) creating a user, deleting it,
   and then re-creating it and 2) changing the username of the user and reverting back to the previous one.
 - Updating a user will re-create it in Jenkins and therefore remove any additionally saved Jenkins-specific
   user data such as API access tokens.


Jenkins Build Plan Access Control Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Artemis takes advantage of the Project-based Matrix Authorization Strategy plugin to support build plan
access control in Jenkins.
This enables specific Artemis users to access build plans and execute actions such as triggering a build.
This section explains the changes required in Jenkins in order to set up build plan access control:

1. Navigate to Manage Jenkins → Plugins → Installed plugins and make sure that you have the
   `Matrix Authorization Strategy <https://plugins.jenkins.io/matrix-auth/>`__ plugin installed

2. Navigate to Manage Jenkins → Security and navigate to the "Authorization" section

3. Select the "Project-based Matrix Authorization Strategy" option

4. In the table make sure that the "Read" permission under the "Overall" section is assigned to
   the "Authenticated Users" user group.

5. In the table make sure that all "Administer" permission is assigned to all administrators.

6. You are finished. If you want to fine-tune permissions assigned to teaching assistants and/or instructors,
   you can change them within the ``JenkinsJobPermission.java`` file.

.. figure:: setup/jenkins-gitlab/jenkins_authorization_permissions.png
    :align: center


Caching
^^^^^^^

You can configure caching for e.g. Maven repositories.
See :ref:`programming-exercises` for more details.


Separate NGINX Configurations
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are some placeholders in the following configurations. Replace
them with your setup specific values ### GitLab

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
"""""""

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
"""""""""""""""""""""""""""""""""

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
