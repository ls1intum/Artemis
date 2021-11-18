Setup for Programming Exercises with Bamboo, Bitbucket and Jira
===============================================================

This page describes how to set up a programming exercise environment
based on Bamboo, Bitbucket and Jira.

| Please note that this setup will create a deployment that is very
  similar to the one used in production but has one difference:
| In production, the builds are performed within Docker containers that
  are created by Bamboo (or its build agents). As we run Bamboo in a
  Docker container in this setup, creating new Docker containers within
  that container is not recommended (e.g. see `this
  article <https://itnext.io/docker-in-docker-521958d34efd>`__). There
  are some solution where one can pass the Docker socket to the Bamboo
  container, but none of these approaches work quite well here as Bamboo
  uses mounted directories that cause issues.

Therefore, a check is included within the BambooBuildPlanService that
ensures that builds are not started in Docker agents if the development
setup is present.

**Prerequisites:**

* `Docker <https://docs.docker.com/install>`__
* `Docker-Compose <https://docs.docker.com/compose/install/>`__


.. contents:: Content of this document
    :local:
    :depth: 1

Docker-Compose
--------------

Before you start the docker-compose, check if the bamboo version in the
``build.gradle`` (search for ``com.atlassian.bamboo:bamboo-specs``) is
equal to the bamboo version number in the Dockerfiles of bamboo stored in
``src/main/docker/bamboo`` (``src/main/docker/bamboo/Dockerfile``, ``src/main/docker/bamboo/swift/Dockerfile``, and ``src/main/docker/bamboo/c/Dockerfile``).
If the version number is not equal, adjust the version number in the Dockerfile.

In case you want to enable Swift or C programming exercises, you need to change
the specified Dockerfile in the docker-compose file ``atlassian.yml`` stored in ``src/main/docker``.
To use the Swift Dockerfile, change the following:

    ::

       bamboo:
               container_name: artemis_bamboo
               build: bamboo

to:

    ::

       bamboo:
               container_name: artemis_bamboo
               build: bamboo/swift

Execute the docker-compose file e.g. with
``docker-compose -f src/main/docker/atlassian.yml up -d``.
If you notice that the used image didn't change, append the flag ``--build``.

Error Handling: It can happen that there is an overload with other
docker networks
``ERROR: Pool overlaps with other one on this address space``. Use the
command ``docker network prune`` to resolve this issue.

Make sure that docker has enough memory (~ 6GB). To adapt it, go to ``Settings -> Resources``

Configure Bamboo, Bitbucket and Jira
------------------------------------

By default, the Jira instance is reachable under ``localhost:8081``, the
Bamboo instance under ``localhost:8085`` and the Bitbucket instance
under ``localhost:7990``.

**Get evaluation licenses for Atlassian products:** `Atlassian Licenses <https://my.atlassian.com/license/evaluation>`__

#. Get licenses for Bamboo, Bitbucket and Jira Service Management.

   - Bamboo: Select ``Bamboo (Data Center)`` and ``not installed yet``
   - Bitbucket: Select ``Bitbucket (Data Center)`` and ``not installed yet``
   - Jira: Select ``Jira Service Management (formerly Service Desk) (Data Center)`` and ``not installed yet``

#. Provide the just created license key during the setup and create an admin user with the same credentials in all 3 applications.
   Also, you can select the evaluation/internal/test/dev setups if you are asked.
   Follow the additional steps for Jira and Bitbucket.

   - Jira:

    - On startup select ``I'll set it up myself``
    - Select Build In Database Connection
    - Create a sample project

   - Bitbucket: Do not connect Bitbucket with Jira yet

#. Make sure that `xdg-utils <https://www.howtoinstall.me/ubuntu/18-04/xdg-utils/>`__ is installed before running the following script.

    .. raw:: html

       <details>
       <summary>xdg-utils for Windows users</summary>
       An easy way to use the xdg-utils on Windows would be to install them on the linux-subsystem, which should be activated anyways when running Docker on Windows.
       For the installation on the subsystem the above linked explanation can be used.
       <br>
       Make sure to execute the script from the subsystem.
       </details>

   Make sure that Jira, Bitbucket and Bamboo have finished starting up.

   Execute the shell script ``atlassian-setup.sh`` in the
   ``src/main/docker`` directory (e.g. with
   ``src/main/docker/./atlassian-setup.sh``). This script creates
   groups, users and disabled application links between the 3 applications.


#. Enable the created `application
   links <https://confluence.atlassian.com/doc/linking-to-another-application-360677690.html>`__
   between all 3 application (OAuth Impersonate). The links should open automatically after the shell script
   has finished. If not open them manually:

   - Bitbucket: http://localhost:7990/plugins/servlet/applinks/listApplicationLinks
   - Bamboo: http://localhost:8085/plugins/servlet/applinks/listApplicationLinks
   - Jira: http://localhost:8081/plugins/servlet/applinks/listApplicationLinks

     **You manually have to adjust the Display URL for the Bamboo → Bitbucket AND
     Bitbucket → Bamboo URl to** ``http://localhost:7990`` **and**
     ``http://localhost:8085`` **.**

        **Bamboo:**

        .. figure:: bamboo-bitbucket-jira/bamboo_bitbucket_applicationLink.png
           :align: center

           Bamboo → Bitbucket

        .. figure:: bamboo-bitbucket-jira/bamboo_jira_applicationLink.png
           :align: center

           Bamboo → Jira


        **Bitbucket:**

        .. figure:: bamboo-bitbucket-jira/bitbucket_bamboo_applicationLink.png
           :align: center

           Bitbucket → Bamboo

        .. figure:: bamboo-bitbucket-jira/bitbucket_jira_applicationLink.png
           :align: center

           Bitbucket → Jira

        **Jira:**

        .. figure:: bamboo-bitbucket-jira/jira_bamboo_applicationLink.png
           :align: center

           Jira → Bamboo

        .. figure:: bamboo-bitbucket-jira/jira_bitbucket_applicationLink.png
           :align: center

           Jira → Bitbucket

#. The script *(step 3)* has already created users and groups but you need to
   manually assign the users into their respective group in Jira. In our
   test setup, users 1-5 are students, 6-10 are tutors, 11-15 are
   editors and 16-20 are instructors. The usernames are \artemis_test_user_{1-20}
   and the password is again the username. When you create a course in artemis
   you have to manually choose the created groups (students, tutors, editors,
   instructors).

#. Use the `user directories in
   Jira <https://confluence.atlassian.com/adminjiraserver/allowing-connections-to-jira-for-user-management-938847045.html>`__
   to synchronize the users in bitbucket and bamboo:

   -  Go to Jira → User management → Jira user server → Add application →
      Create one application for bitbucket and one for bamboo → add the
      IP-address ``0.0.0.0/0`` to IP Addresses

    .. list-table::

        * - .. figure:: bamboo-bitbucket-jira/jira_add_application_bitbucket.png

          - .. figure:: bamboo-bitbucket-jira/jira_add_application_bamboo.png

   -  Go to Bitbucket and Bamboo → User Directories → Add Directories →
      Atlassian Crowd → use the URL ``http://jira:8080`` as Server URL →
      use the application name and password which you used in the previous
      step. Also, you should decrease the synchronisation period (e.g. to 2
      minutes). Press synchronise after adding the directory, the users and
      groups should now be available.

    .. list-table::

        * - .. figure:: bamboo-bitbucket-jira/user_directories_bitbucket.png

                Adding Crowd Server in **Bitbucket**

          - .. figure:: bamboo-bitbucket-jira/user_directories_bamboo.png

                Adding Crowd Server in **Bamboo**


#. In Bamboo create a global variable named
   SERVER_PLUGIN_SECRET_PASSWORD, the value of this variable will be used
   as the secret. The value of this variable should be then stored in
   ``src/main/resources/config/application-artemis.yml`` as the value of
   ``artemis-authentication-token-value``.

#. Download the
   `bamboo-server-notification-plugin <https://github.com/ls1intum/bamboo-server-notification-plugin/releases>`__
   and add it to bamboo. Go to Bamboo → Manage apps → Upload app → select
   the downloaded .jar file → Upload

#. Add Maven and JDK:

   -  Go to Bamboo → Server capabilities → Add capabilities menu →
      Capability type ``Executable`` → select type ``Maven 3.x`` → insert
      ``Maven 3`` as executable label → insert ``/artemis`` as path.

   -  Add capabilities menu → Capability type ``JDK`` → insert ``JDK17``
      as JDK label → insert ``/usr/lib/jvm/java-17-oracle`` as Java home.

#. Generate a personal access token

   While username and password can still be used as a fallback, this option is already marked as deprecated and will
   be removed in the future.

   #. Personal access token for Bamboo.

      - Log in as the admin user and go to Bamboo -> Profile (top right corner) -> Personal access tokens -> Create token

          .. figure:: bamboo-bitbucket-jira/bamboo-create-token.png
             :align: center

      - Insert the generated token into the file ``application-artemis.yml`` in the section ``continuous-integration``:

      .. code:: yaml

          artemis:
              continuous-integration:
                  user: <username>
                  password: <password>
                  token: #insert the token here

   # Personal access token for Bitbucket.

      - Log in as the admin user and go to Bitbucket -> View Profile (top right corner) -> Manage account -> Personal access tokens -> Create token

          .. figure:: bamboo-bitbucket-jira/bitbucket-create-token.png
             :align: center

      - Insert the generated token into the file ``application-artemis.yml`` in the section ``version-control``:

      .. code:: yaml

          artemis:
              version-control:
                  user: <username>
                  password: <password>
                  token: #insert the token here

#. Disable XSRF checking
    Although XSRF checking is highly recommended, we currently have to disable it as Artemis does not yet support
    sending the required headers.

    - Log in as the admin user go to Bamboo -> Overview -> Security Settings

       Edit the settings and disable XSRF checking:

        .. figure:: bamboo-bitbucket-jira/bamboo_xsrf_disable.png
           :align: center

#. Add a SSH key for the admin user

    Artemis can clone/push the repositories during setup and for the online code editor using SSH.
    If the SSH key is not present, the username + token will be used as fallback (and all git operations will use HTTP(S) instead of SSH).
    If the token is also not present, the username + password will be used as fallback (again, using HTTP(S)).

    You first have to create a SSH key (locally), e.g. using ``ssh-keygen`` (more information on how to create a SSH key can be found e.g. at `ssh.com <https://www.ssh.com/ssh/keygen/>`__ or at `atlassian.com <https://confluence.atlassian.com/bitbucketserver076/creating-ssh-keys-1026534841.html>`__).

    The list of supported ciphers can be found at `Apache Mina <https://github.com/apache/mina-sshd>`__.

    It is recommended to use a password to secure the private key, but it is not mandatory.

    Please note that the private key file **must** be named ``id_rsa``, ``id_dsa``, ``id_ecdsa`` or ``id_ed25519``, depending on the ciphers used.

    You now have to extract the public key and add it to Bitbucket.
    Open the public key file (usually called ``id_rsa.pub`` (when using RSA)) and copy it's content (you can also use ``cat id_rsa.pub`` to show the public key).

    Navigate to ``BITBUCKET-URL/plugins/servlet/ssh/account/keys`` and add the SSH key by pasting the content of the public key.

    ``<ssh-key-path>`` is the path to the folder containing the ``id_rsa`` file (but without the filename). It will be used in the configuration of Artemis to specify where Artemis should look for the key and store the ``known_hosts`` file.

    ``<ssh-private-key-password>`` is the password used to secure the private key. It is also needed for the configuration of Artemis, but can be omitted if no password was set (e.g. for development environments).

Configure Artemis
-----------------

#. Modify ``src/main/resources/config/application-artemis.yml``

   .. code:: yaml

           repo-clone-path: ./repos/
           repo-download-clone-path: ./repos-download/
           encryption-password: artemis-encrypt   # arbitrary password for encrypting database values
           user-management:
               use-external: true
               external:
                   url: http://localhost:8081
                   user:  <jira-admin-user>
                   password: <jira-admin-password>
                   admin-group-name: instructors
               internal-admin:
                   username: artemis_admin
                   password: artemis_admin
           version-control:
               url: http://localhost:7990
               user:  <bitbucket-admin-user>
               password: <bitbucket-admin-password>
               token: <bitbucket-admin-token>   # step 10.2
               ssh-private-key-folder-path: <ssh-private-key-folder-path>
               ssh-private-key-password: <ssh-private-key-password>
           continuous-integration:
               url: http://localhost:8085
               user:  <bamboo-admin-user>
               password: <bamboo-admin-password>
               token: <bamboo-admin-token>   # step 10.1
               vcs-application-link-name: LS1 Bitbucket Server
               empty-commit-necessary: true
               artemis-authentication-token-value: <artemis-authentication-token-value>   # step 7

#. Modify the application-dev.yml

   .. code:: yaml

      server:
          port: 8080                                         # The port of artemis
          url: http://172.20.0.1:8080                        # needs to be an ip
          // url: http://docker.for.mac.host.internal:8080   # If the above one does not work for mac try this one
          // url: http://host.docker.internal:8080           # If the above one does not work for windows try this one

In addition, you have to start Artemis with the profiles ``bamboo``,
``bitbucket`` and ``jira`` so that the correct adapters will be used,
e.g.:

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling

Please read :doc:`../setup` for more details.

How to verify the connection works?
-----------------------------------

Artemis → Jira
^^^^^^^^^^^^^^^

You can login to Artemis with the admin user you created in Jira

Artemis → Bitbucket
^^^^^^^^^^^^^^^^^^^^
You can create a programming exercise

Artemis → Bamboo
^^^^^^^^^^^^^^^^^
You can create a programming exercise

Bitbucket → Bamboo
^^^^^^^^^^^^^^^^^^^
The build of the students repository gets started after pushing to it

Bitbucket → Artemis
^^^^^^^^^^^^^^^^^^^^
When using the code editor, after clicking on *Submit*, the text *Building and testing...* should appear.

Bamboo → Artemis
^^^^^^^^^^^^^^^^^
The build result is displayed in the code editor.
