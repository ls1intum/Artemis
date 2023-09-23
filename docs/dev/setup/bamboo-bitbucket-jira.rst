.. _Bamboo Bitbucket and Jira Setup:

Bamboo, Bitbucket and Jira Setup
--------------------------------

This section describes how to set up a programming exercise environment
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


.. contents:: Content of this section
    :local:
    :depth: 1

Docker-Compose
^^^^^^^^^^^^^^

Before you start the *docker compose*, check if the bamboo version in the
``build.gradle`` (search for ``com.atlassian.bamboo:bamboo-specs``) is
equal to the bamboo version number in the docker compose in
``docker/atlassian.yml``
If the version number is not equal, adjust the version number.
Further details about the *docker compose* setup can be found in ``docker``

Execute the *docker compose* file e.g. with
``docker compose -f docker/atlassian.yml up -d``.

Error Handling: It can happen that there is an overload with other
docker networks
``ERROR: Pool overlaps with other one on this address space``. Use the
command ``docker network prune`` to resolve this issue.

Make sure that docker has enough memory (~ 6GB). To adapt it, go to ``Settings → Resources``


In case you want to enable Swift or C programming exercises, refer to the readme in
``docker``


Configure Bamboo, Bitbucket and Jira
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, the Jira instance is reachable under ``localhost:8081``, the
Bamboo instance under ``localhost:8085`` and the Bitbucket instance
under ``localhost:7990``.

**Get evaluation licenses for Atlassian products:** `Atlassian Licenses <https://my.atlassian.com/license/evaluation>`__

#. Get licenses for Bamboo, Bitbucket and Jira Service Management.

   - Bamboo: Select ``Bamboo (Data Center)`` and ``not installed yet``
   - Bitbucket: Select ``Bitbucket (Data Center)`` and ``not installed yet``
   - Jira: Select ``Jira Service Management (formerly Service Desk) (Data Center)`` and ``not installed yet``

#. Provide the just created license key during the setup and create an admin user with the same credentials
   in all 3 applications.
   - Bamboo:

    - Choose the H2 database
    - Select the evaluation/internal/test/dev setups if you are asked
    - Put the admin username and password into ``application-local.yml`` at ``artemis.version-control.user``
      and ``artemis.continuous-integration.user``.

   - Jira:

    - On startup select ``I'll set it up myself``
    - Select Build In Database Connection
    - Create a sample project

   - Bitbucket: Do not connect Bitbucket with Jira yet

#. Make sure that Jira, Bitbucket and Bamboo have finished starting up.

    (Only Linux & Windows) Make sure that `xdg-utils <https://www.howtoinstall.me/ubuntu/18-04/xdg-utils/>`__
    is installed before running the following script.

    .. raw:: html

       <details>
       <summary>xdg-utils for Windows users</summary>
       An easy way to use the xdg-utils on Windows would be to install them on the linux-subsystem,
       which should be activated anyways when running Docker on Windows.
       For the installation on the subsystem the above linked explanation can be used.
       <br>
       Make sure to execute the script from the subsystem.
       </details>


   Execute the shell script ``atlassian-setup.sh`` in the
   ``docker/atlassian`` directory (e.g. with
   ``./docker/atlassian/atlassian-setup.sh``). This script creates
   groups, users and assigns the user to their respective group.
   In addition, it configures disabled application links between the 3 applications.


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

        .. list-table::
           :widths: 33 33 33
           :header-rows: 1

           * - **Bamboo:**
             - **Bitbucket:**
             - **Jira:**
           * - .. figure:: setup/bamboo-bitbucket-jira/bamboo_bitbucket_applicationLink.png
                  :align: center
                  :target: ../_images/bamboo_bitbucket_applicationLink.png

                  Bamboo → Bitbucket
             - .. figure:: setup/bamboo-bitbucket-jira/bitbucket_bamboo_applicationLink.png
                  :align: center
                  :target: ../_images/bitbucket_bamboo_applicationLink.png

                  Bitbucket → Bamboo
             - .. figure:: setup/bamboo-bitbucket-jira/jira_bamboo_applicationLink.png
                  :align: center
                  :target: ../_images/jira_bamboo_applicationLink.png

                  Jira → Bamboo
           * - .. figure:: setup/bamboo-bitbucket-jira/bamboo_jira_applicationLink.png
                  :align: center
                  :target: ../_images/bamboo_jira_applicationLink.png

                  Bamboo → Jira
             - .. figure:: setup/bamboo-bitbucket-jira/bitbucket_jira_applicationLink.png
                  :align: center
                  :target: ../_images/bitbucket_jira_applicationLink.png

                  Bitbucket → Jira
             - .. figure:: setup/bamboo-bitbucket-jira/jira_bitbucket_applicationLink.png
                  :align: center
                  :target: ../_images/jira_bitbucket_applicationLink.png

                  Jira → Bitbucket

#. The script *(step 3)* has already created the required users and assigned them to their respective group in Jira.
   Now, make sure that they are assigned correctly according to the following test setup:
   users 1-5 are students, 6-10 are tutors, 11-15 are
   editors and 16-20 are instructors. The usernames are \artemis_test_user_{1-20}
   and the password is again the username. When you create a course in artemis
   you have to manually choose the created groups (students, tutors, editors,
   instructors).

#. Use the `user directories in
   Jira <https://confluence.atlassian.com/adminjiraserver/allowing-connections-to-jira-for-user-management-938847045.html>`__
   to synchronize the users in bitbucket and bamboo:

   -  Go to `Jira → User management → Jira user server <http://localhost:8081/secure/admin/ConfigureCrowdServer.jspa>`__
      → Add application → Create one application for bitbucket and one for bamboo → add the
      IP-address ``0.0.0.0/0`` to IP Addresses

    .. list-table::

        * - .. figure:: setup/bamboo-bitbucket-jira/jira_add_application_bitbucket.png

          - .. figure:: setup/bamboo-bitbucket-jira/jira_add_application_bamboo.png

   -  Go to `Bitbucket → User Directories <http://localhost:7990/plugins/servlet/embedded-crowd/directories/list>`__
      and `Bamboo → User Directories <http://localhost:8085/plugins/servlet/embedded-crowd/directories/list>`__
      → Add Directories → Atlassian Crowd → use the URL ``http://jira:8080`` as Server URL →
      use the application name and password which you used in the previous
      step. Also, you should decrease the synchronisation period (e.g. to 2
      minutes).

    .. list-table::

        * - .. figure:: setup/bamboo-bitbucket-jira/user_directories_bitbucket.png

                Adding Crowd Server in **Bitbucket**

          - .. figure:: setup/bamboo-bitbucket-jira/user_directories_bamboo.png

                Adding Crowd Server in **Bamboo**

   - Press synchronise after adding the directory, the users and groups should now be available.

#. Give the test users User access on Bitbucket: `On the Administration interface (settings cogwheel on the top),
   go to the Global permissions <http://localhost:7990/admin/permissions>`__. Type the names of all test users in
   the search field ("Add Users") and give them the "Bitbucket User" permission. If you skip this step, the users
   will not be able to log in to Bitbucket or clone repositories.

#. In Bamboo create a global variable named
   SERVER_PLUGIN_SECRET_PASSWORD, the value of this variable will be used
   as the secret. The value of this variable should be then stored in
   ``src/main/resources/config/application-local.yml`` as the value of
   ``artemis-authentication-token-value``.
   You can create a global variable from settings on Bamboo.

#. In Bamboo create a shared username and password credential where the username and password should
   be the same as the ones you used to create the Bitbucket admin user. The name of the shared credential
   must be equal to the value set in ``artemis.version-control.user``.

   The shared user can be created via `Bamboo → Bamboo Administration → Shared credentials <http://localhost:8085/admin/credentials/configureSharedCredentials.action>`__
   → Add new credentials → Username and password

#. Download the
   `bamboo-server-notification-plugin <https://github.com/ls1intum/bamboo-server-notification-plugin/releases>`__
   and add it to bamboo. Go to `Bamboo → Manage apps <http://localhost:8085/plugins/servlet/upm>`__ → Upload app → select
   the downloaded .jar file → Upload

#. Authorize the Bamboo agent. `Bamboo Administration → Agents <http://localhost:8085/admin/agent/configureAgents!doDefault.action>`__
   → Remote agents → Agent authentication

   Approve the agent and edit the IP address in a development setup to ``*.*.*.*`` as the Docker container doesn't
   have a static IP address.

   .. figure:: setup/bamboo-bitbucket-jira/bamboo_agent_configuration.png
      :target: ../_images/bamboo_agent_configuration.png
      :align: center

#. Generate a personal access token

   While username and password can still be used as a fallback, this option is already marked as deprecated and will
   be removed in the future.

   #. Personal access token for Bamboo:

      - Log in as the admin user and go to `Bamboo → Profile (top right corner) → Personal access tokens →
        Create token <http://localhost:8085/profile/createAccessToken.action>`__

          .. figure:: setup/bamboo-bitbucket-jira/bamboo-create-token.png
             :align: center

      - Insert the generated token into the file ``application-local.yml`` in the section ``continuous-integration``:

      .. code:: yaml

          artemis:
              continuous-integration:
                  user: <username>
                  password: <password>
                  token: #insert the token here

   #. Personal access token for Bitbucket:

      - Log in as the admin user and go to `Bitbucket → Your profile image (top right corner) → Manage account
        <http://localhost:7990/account>`__ → HTTP access tokens → Create token

          .. figure:: setup/bamboo-bitbucket-jira/bitbucket_create_token.png
             :align: center

      - Insert the generated token into the file ``application-local.yml`` in the section ``version-control``:

      .. code:: yaml

          artemis:
              version-control:
                  user: <username>
                  password: <password>
                  token: #insert the token here

#. Add a SSH key for the admin user

    Artemis can clone/push the repositories during setup and for the online code editor using SSH.
    If the SSH key is not present, the username + token will be used as fallback
    (and all git operations will use HTTP(S) instead of SSH).
    If the token is also not present, the username + password will be used as fallback (again, using HTTP(S)).

    You first have to create a SSH key (locally), e.g. using ``ssh-keygen``
    (more information on how to create a SSH key can be found e.g. at `ssh.com <https://www.ssh.com/ssh/keygen/>`__
    or at `atlassian.com <https://confluence.atlassian.com/bitbucketserver076/creating-ssh-keys-1026534841.html>`__).

    The list of supported ciphers can be found at `Apache Mina <https://github.com/apache/mina-sshd>`__.

    It is recommended to use a password to secure the private key, but it is not mandatory.

    Please note that the private key file **must** be named ``id_rsa``, ``id_dsa``, ``id_ecdsa`` or ``id_ed25519``,
    depending on the ciphers used.

    You now have to extract the public key and add it to Bitbucket.
    Open the public key file (usually called ``id_rsa.pub`` (when using RSA)) and copy it's content
    (you can also use ``cat id_rsa.pub`` to show the public key).

    Navigate to ``BITBUCKET-URL/plugins/servlet/ssh/account/keys`` and add the SSH key by pasting the content of
    the public key.

    ``<ssh-private-key-folder-path>`` is the path to the folder containing the ``id_rsa`` file (but without the filename).
    It will be used in the configuration of Artemis to specify where Artemis should look for the key and
    store the ``known_hosts`` file.

    ``<ssh-private-key-password>`` is the password used to secure the private key.
    It is also needed for the configuration of Artemis, but can be omitted if no password was set
    (e.g. for development environments).

Configure Artemis
^^^^^^^^^^^^^^^^^

#. Modify ``src/main/resources/config/application-local.yml`` to include the correct URLs and credentials:

   .. code:: yaml

           repo-clone-path: ./repos/
           repo-download-clone-path: ./repos-download/
           bcrypt-salt-rounds: 11   # The number of salt rounds for the bcrypt password hashing. Lower numbers make it faster but more unsecure and vice versa.
                                    # Please use the bcrypt benchmark tool to determine the best number of rounds for your system. https://github.com/ls1intum/bcrypt-Benchmark
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
               artemis-authentication-token-value: <artemis-authentication-token-value>   # step 7

  If you run the Atlassian suite in containers and Artemis on your host machine, you may have to set internal urls for bamboo,
  so that the CI and VCS servers are reachable from each other. If Artemis is executed in a container in the same network,
  you won't need to specify internal URLs, as Artemis can then communicate with Bamboo and Bitbucket and Bamboo and Bitbucket
  can communicate with each other using the same url. If you use the default docker-compose setup, you can use the following
  configuration:

   .. code:: yaml

    bamboo:
        internal-urls:
            ci-url: http://bamboo:8085
            vcs-url: http://bitbucket:7990



#. Also, set the server URL in ``src/main/resources/config/application-local.yml``:

   .. code:: yaml

      server:
          port: 8080                                         # The port of artemis
          url: http://172.20.0.1:8080                        # needs to be an ip
          # url: http://docker.for.mac.host.internal:8080   # If the above one does not work for mac try this one
          # url: http://host.docker.internal:8080           # If the above one does not work for windows try this one

In addition, you have to start Artemis with the profiles ``bamboo``,
``bitbucket`` and ``jira`` so that the correct adapters will be used,
e.g.:

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling,local

All of these profiles are enabled by default when using one of the run configurations in IntelliJ.
Please read :ref:`Server Setup` for more details.

How to verify the connection works?
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Artemis → Jira
""""""""""""""

You can login to Artemis with the admin user you created in Jira

Artemis → Bitbucket
"""""""""""""""""""
You can create a programming exercise

Artemis → Bamboo
""""""""""""""""
You can create a programming exercise

Bitbucket → Bamboo
""""""""""""""""""
The build of the students repository gets started after pushing to it

Bitbucket → Artemis
"""""""""""""""""""
When using the code editor, after clicking on *Submit*, the text *Building and testing...* should appear.

Bamboo → Artemis
""""""""""""""""
The build result is displayed in the code editor.
