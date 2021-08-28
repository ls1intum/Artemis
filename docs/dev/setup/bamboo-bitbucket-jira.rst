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
equal to the bamboo version number in the Dockerfile of bamboo stored in
``src/main/docker/bamboo/Dockerfile`` or ``src/main/docker/bamboo/swift/Dockerfile``.
If the version number is not equal adjust the version number in the Dockerfile.

In case you want to enable Swift programming exercises, you need to change
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

Execute the docker-compose file e.g. with
``docker-compose -f src/main/docker/atlassian.yml up -d``

Error Handling: It can happen that there is an overload with other
docker networks
``ERROR: Pool overlaps with other one on this address space``. Use the
command ``docker network prune`` to resolve this issue.

Also make sure that docker has enough memory (~ 6GB). To adapt it, go to ``Preferences -> Resources``

Configure Bamboo, Bitbucket and Jira
------------------------------------

By default, the Jira instance is reachable under ``localhost:8081``, the
Bamboo instance under ``localhost:8085`` and the Bitbucket instance
under ``localhost:7990``.

**Get evaluation licenses for Atlassian products:** `Atlassian Licenses <https://my.atlassian.com/license/evaluation>`__

1. Get licenses for Bamboo, Bitbucket and Jira Service Management.

   - Bamboo: Select Bamboo (Server) and ``not installed yet``
   - Bitbucket: Select Bitbucket (Data Center) and ``not installed yet``
   - Jira: Select Jira Service Management (Data Center) and ``not installed yet``

2. Provide the just created license key during the setup and create an admin user with the same credentials in all 3 applications.
   Also, you can select the evaluation/internal/test/dev setups if you are asked.
   Follow the additional steps for Jira and Bitbucket.

   - Jira:

    - On startup select ``I'll set it up myself``
    - Select Build In Database Connection
    - Create a sample project

   - Bitbucket: Do not connect Bitbucket with Jira yet

3. Execute the shell script ``atlassian-setup.sh`` (or ``atlassian-setup-k8s.sh`` if you are running on Kubernetes) in the
   ``src/main/docker`` directory (e.g. with
   ``src/main/docker/./atlassian-setup.sh``). This script creates
   groups, users and disabled application links between the 3 applications.
   Make sure that `xdg-utils <https://www.howtoinstall.me/ubuntu/18-04/xdg-utils/>`__ is installed before running the script.

4. Enable the created `application
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

5. The script has already created users and groups but you need to
   manually assign the users into their respective group in Jira. In our
   test setup, users 1-5 are students, 6-10 are tutors, 11-15 are
   editors and 16-20 are instructors. The usernames are artemis_test_user_{1-20}
   and the password is again the username. When you create a course in artemis
   you have to manually choose the created groups (students, tutors, editors,
   instructors).

6. Use the `user directories in
   Jira <https://confluence.atlassian.com/adminjiraserver/allowing-connections-to-jira-for-user-management-938847045.html>`__
   to synchronize the users in bitbucket and bamboo:

   -  Go to Jira → User management → Jira user server → Add application →
      Create one application for bitbucket and one for bamboo → add the
      IP-address ``0.0.0.0/0`` to IP Addresses

       .. figure:: bamboo-bitbucket-jira/jira_add_application.png
          :align: center


   -  Go to Bitbucket and Bamboo → User Directories → Add Directories →
      Atlassian Crowd → use the URL ``http://jira:8080`` as Server URL →
      use the application name and password which you used in the previous
      step. Also, you should decrease the synchronisation period (e.g. to 2
      minutes). Press synchronise after adding the directory, the users and
      groups should now be available.

       .. figure:: bamboo-bitbucket-jira/user_directories.png
          :align: center

7. In Bamboo create a global variable named
   SERVER_PLUGIN_SECRET_PASSWORD, the value of this variable will be used
   as the secret. The value of this variable should be then stored in
   ``src/main/resources/config/application-artemis.yml`` as the value of
   ``artemis-authentication-token-value``.

8. Download the
   `bamboo-server-notification-plugin <https://github.com/ls1intum/bamboo-server-notification-plugin/releases>`__
   and add it to bamboo. Go to Bamboo → Manage apps → Upload app → select
   the downloaded .jar file → Upload

9. Add Maven and JDK:

   -  Go to Bamboo → Server capabilities → Add capabilities menu →
      Capability type ``Executable`` → select type ``Maven 3.x`` → insert
      ``Maven 3`` as executable label → insert ``/artemis`` as path.

   -  Add capabilities menu → Capability type ``JDK`` → insert ``JDK16``
      as JDK label → insert ``/usr/lib/jvm/java-16-oracle`` as Java home.

10. Generate a personal access token

   While username and password can still be used as a fallback, this option is already marked as deprecated and will
   be removed in the future.

   10.1 Personal access token for Bamboo.

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

   10.2 Personal access token for Bitbucket.

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

11. Disable XSRF checking
    Although XSRF checking is highly recommended, we currently have to disable it as Artemis does not yet support
    sending the required headers.

    - Log in as the admin user go to Bamboo -> Overview -> Security Settings

       Edit the settings and disable XSRF checking:

        .. figure:: bamboo-bitbucket-jira/bamboo_xsrf_disable.png
           :align: center

12. Add a SSH key for the admin user

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

1. Modify ``src/main/resources/config/application-artemis.yml``

   For Docker setup: 

   .. code:: yaml

           repo-clone-path: ./repos/
           repo-download-clone-path: ./repos-download/
           encryption-password: artemis-encrypt     # arbitrary password for encrypting database values
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
               token: <bitbucket-admin-token>
               ssh-private-key-folder-path: <ssh-private-key-folder-path>
               ssh-private-key-password: <ssh-private-key-password>
           continuous-integration:
               url: http://localhost:8085
               user:  <bamboo-admin-user>
               password: <bamboo-admin-password>
               token: <bamboo-admin-token>
               vcs-application-link-name: LS1 Bitbucket Server
               empty-commit-necessary: true
               artemis-authentication-token-value: <artemis-authentication-token-value>

   For Kubernetes setup you shoud use the same URLs you used for the atlassian-setup-k8s.sh file: 

   .. code:: yaml

           repo-clone-path: ./repos/
           repo-download-clone-path: ./repos-download/
           encryption-password: artemis-encrypt     # arbitrary password for encrypting database values
           user-management:
               use-external: true
               external:
                   url: http://jira:8080
                   user:  <jira-admin-user>
                   password: <jira-admin-password>
                   admin-group-name: instructors
               internal-admin:
                   username: artemis_admin
                   password: artemis_admin
           version-control:
               url: http://bitbucket:7990
               user:  <bitbucket-admin-user>
               password: <bitbucket-admin-password>
               token: <bitbucket-admin-token>
               ssh-private-key-folder-path: <ssh-private-key-folder-path>
               ssh-private-key-password: <ssh-private-key-password>
           continuous-integration:
               url: http://bamboo:8085
               user:  <bamboo-admin-user>
               password: <bamboo-admin-password>
               token: <bamboo-admin-token>
               vcs-application-link-name: LS1 Bitbucket Server
               empty-commit-necessary: true
               artemis-authentication-token-value: <artemis-authentication-token-value>


2. Modify the application-dev.yml

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

Deployment on Kubernetes
------------------------

This part of the documentation assumes you have a Kubernetes cluster running, as well as DockerHub account, if not you can find how to set it up here # TO DO `<>`__ The link will be added when the URL to the Kubernetes documentation is available

1. Create artemis-bamboo repository in DockerHub
2. Build the preconfigured Bamboo

::

   docker build  -t <DockerId>/artemis-bamboo -f src/main/docker/bamboo/Dockerfile .

3. Push the image to DockerHub
   
::

   docker push <DockerId>/artemis-bamboo

4. Add your DockerHub username in ``src/main/kubernetes/atlassian/bamboo.yml``
   Replace <DockerId> in:

::

   - image: <DockerId>/bamboo

5. Deploy 

::

   kubectl apply -k src/main/kubernetes/atlassian --kubeconfig <path-to-kubeconfig-file>

<path-to-kubeconfig-file> is the path where the KUBECONFIG_FILE is located.

1. Modify the ``/src/main/docker/atlassian-setup-k8s.sh`` script

   1. Open Rancher to get the URLs of Jira, Bamboo and Bitbucket
   2. Go to workloads
   3. You can see the three application deployed
   
   
   .. figure:: bamboo-bitbucket-jira/rancher_atlassian.png
      :align: center


   4. Click on each of the <port>/tcp links and new tab will be opened with the corresponding application where you can copy the URL from.
   5. Open ``/src/main/docker/atlassian-setup-k8s.sh`` and add the URL values to the script

   ::

      jira_url=<jira_url>
      bamboo_url=<bamboo_url>
      bitbucket_url=<bitbucket_url>

   i.e.

   ::
   
      jira_url=http://172.18.0.3:32149
      bamboo_url=http://172.18.0.3:31449
      bitbucket_url=http://172.18.0.3:31463
After successful deployment you can continue with the configuration described here: `Configure Bamboo, Bitbucket and Jira <https://docs.artemis.ase.in.tum.de/dev/setup/bamboo-bitbucket-jira/#configure-bamboo-bitbucket-and-jira>`__