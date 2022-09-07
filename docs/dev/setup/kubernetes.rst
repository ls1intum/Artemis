Setup Kubernetes and Kubernetes Deployment
------------------------------------------

This section describes how to set up an environment deployed in Kubernetes.

**Prerequisites:**

Follow the links to install the tools which will be needed to proceed with the Kubernetes cluster setup.

* `Docker <https://docs.docker.com/get-docker/>`__ - v20.10.7
   Docker is a platform for developing, shipping and running applications.
   In our case, we will use it to build the images which we will deploy.
   It is also needed from k3d to create a cluster. The cluster nodes are deployed on Docker containers.

* `DockerHub Account <https://hub.docker.com/signup>`__
   Docker Hub is a service provided by Docker for finding and sharing container images.
   Account in DockerHub is needed to push the Artemis image which will be used by the Kubernetes deployment.

* `k3d <https://k3d.io/#installation>`__ - v4.4.7
   k3d is a lightweight wrapper to run k3s which is a lightweight Kubernetes distribution in Docker.
   k3d makes it very easy to create k3s clusters especially for local deployment on Kubernetes.

   Windows users can use ``choco`` to install it. More details can be found in the link under ``Other Installation Methods``

* `kubectl <https://kubernetes.io/docs/tasks/tools/#kubectl/>`__ - v1.21
   kubectl is the Kubernetes command-line tool, which allows you to run commands against Kubernetes clusters.
   It can be used to deploy applications, inspect and manage cluster resources, and view logs.

* `helm <https://helm.sh/docs/intro/install/>`__ - v3.6.3
   Helm is the package manager for Kubernetes. We will use it to install cert-manager and Rancher


.. contents:: Content of this section
    :local:
    :depth: 1

Setup Kubernetes Cluster
^^^^^^^^^^^^^^^^^^^^^^^^
To be able to deploy Artemis on Kubernetes, you need to set up a cluster.
A cluster is a set of nodes that run containerized applications.
Kubernetes clusters allow for applications to be more easily developed, moved and managed.

With the following commands, you will set up one cluster with three agents as well as Rancher
which is a platform for cluster management with an easy to use user interface.

**IMPORTANT: Before you continue make sure Docker has been started.**


1. Set environment variables

   The CLUSTER_NAME, RANCHER_SERVER_HOSTNAME and KUBECONFIG_FILE environment variables need to be set
   so that they can be used in the next commands.
   If you don't want to set environment variables you can replace their values in the commands.
   What you need to do is replace $CLUSTER_NAME with "k3d-rancher", $RANCHER_SERVER_HOSTNAME with "rancher.localhost"
   and $KUBECONFIG_FILE with "k3d-rancher.yml".

   **For macOS/Linux**:

   ::

      export CLUSTER_NAME="k3d-rancher"
      export RANCHER_SERVER_HOSTNAME="rancher.localhost"
      export KUBECONFIG_FILE="$CLUSTER_NAME.yaml"


   **For Windows**:

   ::

      $env:CLUSTER_NAME="k3d-rancher"
      $env:RANCHER_SERVER_HOSTNAME="rancher.localhost"
      $env:KUBECONFIG_FILE="${env:CLUSTER_NAME}.yaml"

2. Create the cluster


   With the help of the commands block below you can create a cluster with one server and three agents
   at a total of four nodes.
   Your deployments will be distributed almost equally among the 4 nodes.

   Using ``k3d cluster list`` you can see whether your cluster is created and how many of its nodes are running.

   Using ``kubectl get nodes`` you can see the status of each node of the newly created cluster.

   You should also write the cluster configuration into the KUBECONFIG_FILE.
   This configuration will be later needed when you are creating deployments.
   You can either set the path to the file as an environment variable or replace it with "<path-to-kubeconfig-file>" when needed.

   **For macOS/Linux**:

   ::

      k3d cluster create $CLUSTER_NAME --api-port 6550 --servers 1 --agents 3 --port 443:443@loadbalancer --wait
      k3d cluster list
      kubectl get nodes
      k3d kubeconfig get $CLUSTER_NAME > $KUBECONFIG_FILE
      export KUBECONFIG=$KUBECONFIG_FILE

   **For Windows**:

   ::

      k3d cluster create $env:CLUSTER_NAME --api-port 6550 --servers 1 --agents 3 --port 443:443@loadbalancer --wait
      k3d cluster list
      kubectl get nodes
      k3d kubeconfig get ${env:CLUSTER_NAME} > $env:KUBECONFIG_FILE
      $env:KUBECONFIG=($env:KUBECONFIG_FILE)

3. Install cert-manager

   cert-manager is used to add certificates and certificate issuers as resource types in Kubernetes clusters.
   It simplifies the process of obtaining, renewing and using those certificates.
   It can issue certificates from a variety of supported sources, e.g. Let’s Encrypt, HashiCorp Vault, Venafi.

   In our case, it will issue self-signed certificates to our Kubernetes deployments to secure the communication
   between the different deployments.

   Before the installation, you need to add the Jetstack repository and update the local Helm chart repository cache.
   cert-manager has to be installed in a separate namespace called ``cert-manager`` so one should be created as well.
   After the installation, you can check the status of the installation.

   ::

      helm repo add jetstack https://charts.jetstack.io
      helm repo update
      kubectl create namespace cert-manager
      helm install cert-manager jetstack/cert-manager --namespace cert-manager --version v1.0.4 --set installCRDs=true --wait
      kubectl -n cert-manager rollout status deploy/cert-manager

4. Install Rancher

   Rancher is a Kubernetes management tool that allows you to create and manage Kubernetes deployments
   more easily than with the CLI tools.

   You can install Rancher using Helm - the package manager for Kubernetes.
   It has to be installed in a namespace called ``cattle-system`` and
   we should create such a namespace before the installation itself.
   During the installation, we set the namespace and the hostname on which Rancher will be accessible.
   Then we can check the installation status.

   **For macOS/Linux**:

   ::

      helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
      helm repo update
      kubectl create namespace cattle-system
      helm install rancher rancher-stable/rancher --namespace cattle-system --set hostname=$RANCHER_SERVER_HOSTNAME --version 2.5.9 --wait
      kubectl -n cattle-system rollout status deploy/rancher

   **For Windows**:

   ::

      helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
      helm repo update
      kubectl create namespace cattle-system
      helm install rancher rancher-stable/rancher --namespace cattle-system --set hostname=${env:RANCHER_SERVER_HOSTNAME} --version 2.5.9 --wait
      kubectl -n cattle-system rollout status deploy/rancher

5. Open Rancher and update the password

Open Rancher on `<https://rancher.localhost/>`__.

You will be notified that the connection is not private.
The reason for that is that the Rancher deployment uses a self-signed certificate by an unknown authority 'dynamiclistener-ca'.
It is used for secure communication between internal components.
Since it's your local environment this is not an issue and you can proceed to the website.
If you can't continue using the Chrome browser, you can try with another browser, e.g. Firefox.

You will be prompted to set a password which later will be used to log in to Rancher.
The password will often be used, so you shouldn't forget it.

.. figure:: setup/kubernetes/rancher_password.png
   :align: center

Then you should save the Rancher Server URL, please use the predefined name.

.. figure:: setup/kubernetes/rancher_url.png
   :align: center

After saving, you will be redirected to the main page of Rancher, where you see your clusters.
There will be one local cluster.

.. figure:: setup/kubernetes/rancher_cluster.png
   :align: center

You can open the workloads using the menu, there will be no workloads deployed at the moment.

.. figure:: setup/kubernetes/rancher_nav_workloads.png
   :align: center


.. figure:: setup/kubernetes/rancher_empty_workloads.png
   :align: center

6. Create a new namespace in Rancher

Namespaces are virtual clusters backed by the same physical cluster. Namespaces provide a scope for names.
Names of resources need to be unique within a namespace, but not across namespaces.
Usually, different namespaces are created to separate environments deployments e.g. development, staging, production.

For our development purposes, we will create a namespace called artemis.
It can be done easily using Rancher.

a. Navigate to Namespaces using the top menu of Rancher

b. Select ``Add Namespace`` to open the form for namespace creation

   .. figure:: setup/kubernetes/rancher_namespaces.png
      :align: center

c. Put ``artemis`` as namespace's name and select the ``Create`` button

   .. figure:: setup/kubernetes/rancher_create_namespace.png
      :align: center



Create DockerHub Repository
^^^^^^^^^^^^^^^^^^^^^^^^^^^
The Artemis image will be stored and managed in DockerHub. Kubernetes will pull it from there and deploy it afterwards.

After you log in to your `DockerHub <https://hub.docker.com/>`__ account you can create as many public repositories as you want.
To create a repository you need to select the ``Create repository`` button.


**DockerHub:**

.. figure:: setup/kubernetes/dockerhub.png
   :align: center

Then fill in the repository name with ``artemis``. Then use the ``Create`` button to create your repository.

.. figure:: setup/kubernetes/dockerhub_create_repository.png
   :align: center

Configure Docker ID (username)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The username in DockerHub is called Docker ID.
You need to set your Docker ID in the ``artemis-deployment.yml`` resource so that Kubernetes knows where to pull the image from.
Open the ``src/main/kubernetes/artemis/deployment/artemis-deployment.yml`` file and edit

   ::

      template:
         spec:
         containers:
            image: <DockerId>/artemis

and replace <DockerId> with your docker ID in DockerHub

e.g. it will look like this:

   ::

      template:
         spec:
         containers:
            image: mmehmed/artemis



Configure Artemis Resources
^^^^^^^^^^^^^^^^^^^^^^^^^^^
To run Artemis, you need to configure the Artemis' User Management, Version Control and Continuous Integration.
You can either run it with Jira, Bitbucket, Bamboo or Jenkins, GitLab.
Make sure to configure the ``src/main/resources/config/application-artemis.yml`` file with the proper configuration
for User Management, Version Control and Continuous Integration.

You should skip setting the passwords and token since the Docker image that we are going to build is going to include those secrets.
You can refer to chapter ``Add/Edit Secrets`` for setting those values.

If you want to configure Artemis with ``Bitbucket, Jira, Bamboo`` you can set a connection to existing staging or production deployments.
If you want to configure Artemis with local user management and no programming exercises continue with ``Configure Local User Management``.

Configure Local User Management
"""""""""""""""""""""""""""""""

If you want to run with local user management and no programming exercises setup follow the steps:

1. Go to the ``src/main/resources/config/application-artemis.yml`` file, and set use-external in the user-management section to false.
If you have created an additional ``application-local.yml`` file as it is described in the
`Setup documentation <https://artemis-platform.readthedocs.io/en/latest/dev/setup/#server-setup>`__, make sure to edit this one.

   Another possibility is to add the variable directly in ``src/main/kubernetes/artemis/configmap/artemis-configmap.yml``.

   ::

      data:
         artemis.user-management.use-external: "false"


2. Remove the jira profile from the ``SPRING_PROFILES_ACTIVE`` field in the ConfigMap found at
``src/main/kubernetes/artemis/configmap/artemis-configmap.yml``

Now you can continue with the next step ``Build Artemis``


Build Artemis
^^^^^^^^^^^^^
Build the Artemis application war file using the following command:

::

   ./gradlew -Pprod -Pwar clean bootWar

Run Docker Build
^^^^^^^^^^^^^^^^
Run Docker build and prepare the Artemis image to be pushed in DockerHub using the following command:

::

   docker build  -t <DockerId>/artemis -f src/main/docker/Dockerfile .

This will create the Docker image by copying the war file which was generated by the previous command.

Push to Docker
^^^^^^^^^^^^^^
Push the image to DockerHub from where it will be pulled during the deployment:

::

   docker push <DockerId>/artemis

In case that you get an "Access denied" error during the push, first execute

::

   docker login

and then try again the ``docker push`` command.


Configure Spring Profiles
^^^^^^^^^^^^^^^^^^^^^^^^^
ConfigMaps are used to store configuration data in key-value pairs.

You can change the current Spring profiles used for running Artemis in the
``src/main/kubernetes/artemis/configmap/artemis-configmap.yml`` file by changing ``SPRING_PROFILES_ACTIVE``.
The current ones are set to use Bitbucket, Jira and Bamboo.
If you want to use Jenkins and GitLab please replace ``bamboo,bitbucket,jira`` with ``jenkins,gitlab``.
You can also change ``prod`` to ``dev`` if you want to run in development profile.


Deploy Kubernetes Resources
^^^^^^^^^^^^^^^^^^^^^^^^^^
Kustomization files declare the resources that will be deployed in one place and with their help we can do
the deployment with only one command.

Once you have your Artemis image pushed to Docker you can use the ``kustomization.yml`` file in ``src/main/kubernetes``
to deploy all the Kubernetes resources.
You can do it by executing the following command:

::

   kubectl apply -k src/main/kubernetes/artemis --kubeconfig <path-to-kubeconfig-file>

<path-to-kubeconfig-file> is the path where you created the KUBECONFIG_FILE.


In the console, you will see that the resources are created.
It will take a little bit of time when you are doing this for the first time. Be patient!

.. figure:: setup/kubernetes/kubectl_kustomization.png
   :align: center

Add/Edit Secrets
^^^^^^^^^^^^^^^^
Once you have deployed Artemis you need to add/edit the secrets so that it can run successfully.

Open Rancher using `<https://rancher.localhost/>`__ and navigate to your cluster.

Then navigate to ``Secrets`` like shown below:

.. figure:: setup/kubernetes/rancher_secrets_menu.png
   :align: center

You will see list of all defined secret files

.. figure:: setup/kubernetes/rancher_secrets_list.png
   :align: center

Continue with ``artemis-secrets`` and you will see the values in the secret file. Then navigate to the edit page.

.. figure:: setup/kubernetes/rancher_secrets_edit.png
   :align: center

You can edit each secret you want or add more secrets.
Once you select any value box the value itself will be shown and you can edit it.

.. figure:: setup/kubernetes/rancher_secrets_edit_page.png
   :align: center

After you are done you can save your changes and redeploy the Artemis workload.

Check the Deployments in Rancher
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Open Rancher using `<https://rancher.localhost/>`__ and navigate to your cluster.

It may take some time but in the end, you should see that all the workloads have Active status.
In case there is a problem with some workloads you can check the logs to see what the issue is.

.. figure:: setup/kubernetes/rancher_workloads.png
   :align: center

You can open the Artemis application using the link `<https://artemis-app.artemis.rancher.localhost/>`__

You will get the same "Connection is not private" issue as you did when opening `<https://rancher.localhost/>`__.
As said before this is because a self-signed certificate is used and it is safe to proceed.

It takes several minutes for the application to start.
If you get a "Bad Gateway" error it may happen that the application has not been started yet.
Wait several minutes and if you still have this issue or another one you can check out the pod logs (described in the next chapter).

Check out the Logs
^^^^^^^^^^^^^^^^^^
Open the workload which logs you need to check.
There is a list of pods. Open the menu for one of the pods and select ``View Logs``. A pop-up with the logs will be opened.

.. figure:: setup/kubernetes/rancher_logs.png
   :align: center

Troubleshooting
^^^^^^^^^^^^^^^
If the Artemis application is successfully deployed but there is an error while trying to run the application,
the reason is most likely related to the Artemis yml configuration files.
One of the common errors is related to missing ``server.url`` variable.
You can fix it by adding it as an environment variable to the Artemis deployment.

Set Additional Environment Variables
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This chapter explains how you can set environment variables for your deployment in case you need it.

Open the Workloads view on Rancher

.. figure:: setup/kubernetes/rancher_workloads.png
   :align: center

Enter the details page of the Artemis workload and then select Edit in the three-dot menu

.. figure:: setup/kubernetes/workload_edit.png
   :align: center

Expand the ``Environment Variables`` menu.
After pressing the ``Add Variable`` button two fields will appear where you can add the variable key and the value.

.. figure:: setup/kubernetes/workload_set_environment_variable.png
   :align: center

You can add as many variables as you want.
Once you are done you can save your changes which will trigger the Redeploy of the application.
