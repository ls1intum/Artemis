.. _setup_distributed:

Multiple Artemis instances
--------------------------

Setup with one instance
^^^^^^^^^^^^^^^^^^^^^^^
Artemis usually runs with one instance of the application server:

   .. figure:: setup/distributed/deployment_before.drawio.png
      :align: center



Setup with multiple instances
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
There are certain scenarios, where a setup with multiple instances of the application server is required.
This can e.g. be due to special requirements regarding fault tolerance or performance.

Artemis also supports this setup (which is also used at the Chair for Applied Software Engineering at TUM).

Multiple instances of the application server are used to distribute the load:

   .. figure:: setup/distributed/deployment_after_simple.drawio.png
      :align: center

A load balancer (typically a reverse proxy such as nginx) is added, that distributes the requests to the different instances.

**Note:** This documentation focuses on the practical setup of this distributed setup.
More details regarding the theoretical aspects can be found in the Bachelor's Thesis `Securing and Scaling Artemis WebSocket Architecture`, which can be found here: :download:`pdf <distributed/thesis_securing_scaling_artemis.pdf>`.

Additional synchronization
^^^^^^^^^^^^^^^^^^^^^^^^^^
All instances of the application server use the same database, but other parts of the system also have to be synchronized:

1. Database cache
2. WebSocket messages
3. File system

Each of these three aspects is synchronized using a different solution

Database cache
^^^^^^^^^^^^^^
Artemis uses a cache provider that supports distributed caching: Hazelcast_.

.. _Hazelcast: https://hazelcast.com/

All instances of Artemis form a so-called cluster that allows them to synchronize their cache.
You can use the configuration argument ``spring.hazelcast.interface`` to configure the interface on which Hazelcast will listen.


   .. figure:: setup/distributed/deployment_hazelcast.drawio.png
      :align: center


One problem that arises with a distributed setup is that all instances have to know each other in order to create this cluster.
This is problematic if the instances change dynamically.
Artemis uses a discovery service to solve the issue (named `JHipster Registry
<https://www.jhipster.tech/jhipster-registry/>`_).

Discovery service
^^^^^^^^^^^^^^^^^
JHipster registry contains Eureka, the discovery service where all instances can register themselves and fetch the other registered instances.

Eureka can be configured like this within Artemis:

.. code:: yaml

    # Eureka configuration
    eureka:
        client:
            enabled: true
            service-url:
                defaultZone: {{ artemis_eureka_urls }}
    instance:
        prefer-ip-address: true
        ip-address: {{ artemis_ip_address }}
        appname: Artemis
        instanceId: Artemis:{{ artemis_eureka_instance_id }}

    logging:
        file:
            name: '/opt/artemis/artemis.log'

``{{ artemis_eureka_urls }}`` must be the URL where Eureka is reachable,
``{{ artemis_ip_address }}`` must be the IP under which this instance is reachable and
``{{ artemis_eureka_instance_id }}`` must be a unique identifier for this instance.
You also have to setup the value ``jhipster.registry.password`` to the password of the registry (which you will set later).

Note that Hazelcast (which requires Eureka) is by default binding to `127.0.0.1` to prevent other instances
to form a cluster without manual intervention.
If you set up the cluster on multiple machines (which you should do for a production setup),
you have to set the value ``spring.hazelcast.interface`` to the ip-address of the machine.
Hazelcast will then bind on this interface rather than `127.0.0.1`,
which allows other instances to establish connections to the instance.
This setting must be set for every instance, but you have to make sure to adjust the ip-address correspondingly.


Setup
^^^^^
**Installing**

1. Create the directory

.. code:: bash

    sudo mkdir /opt/registry/
    sudo mkdir /opt/registry/config-server

2. Download the application

Download the latest version of the jhipster-registry from GitHub, e.g. by using

.. code:: bash

    sudo wget -O /opt/registry/registry.jar https://github.com/jhipster/jhipster-registry/releases/download/v6.2.0/jhipster-registry-6.2.0.jar

**Service configuration**

1. ``sudo vim /etc/systemd/system/registry.service``

.. code:: bash

    [Unit]
    Description=Registry
    After=syslog.target

    [Service]
    User=artemis
    WorkingDirectory=/opt/registry
    ExecStart=/usr/bin/java \
        -Xmx256m \
        -jar registry.jar \
        --spring.profiles.active=prod,native
    SuccessExitStatus=143
    StandardOutput=/opt/registry/registry.log
    #StandardError=inherit

    [Install]
    WantedBy=multi-user.target

2. Set Permissions in Registry Folder

.. code:: bash

    sudo chown -R artemis:artemis /opt/registry
    sudo chmod g+rwx /opt/registry

3. Enable the service

.. code:: bash

    sudo systemctl daemon-reload
    sudo systemctl enable registry.service

4. Start Service (only after performing steps 1-3 of the configuration)

.. code:: bash

    sudo systemctl start registry

5. Logging

.. code:: bash

    sudo journalctl -f -n 1000 -u registry

**Configuration**

1. ``sudo vim /opt/registry/application-prod.yml``

.. code:: yaml

    logging:
        file:
            name: '/opt/registry/registry.log'

    jhipster:
        security:
            authentication:
            jwt:
                base64-secret: THE-SAME-TOKEN-THAT-IS-USED-ON-THE-ARTEMIS-INSTANCES
        registry:
            password: AN-ADMIN-PASSWORD-THAT-MUST-BE-CHANGED
    spring:
        security:
            user:
                password: AN-ADMIN-PASSWORD-THAT-MUST-BE-CHANGED

2. ``sudo vim /opt/registry/bootstrap-prod.yml``

.. code:: yaml

    jhipster:
        security:
            authentication:
            jwt:
                base64-secret: THE-SAME-TOKEN-THAT-IS-USED-ON-THE-ARTEMIS-INSTANCES
                secret: ''

    spring:
        cloud:
            config:
            server:
                bootstrap: true
                composite:
                - type: native
                  search-locations: file:./config-server


3. ``sudo vim /opt/registry/config-server/application.yml``

  .. code:: yaml

    # Common configuration shared between all applications
    configserver:
        name: Artemis JHipster Registry
        status: Connected to the Artemis JHipster Registry

    jhipster:
        security:
            authentication:
            jwt:
                secret: ''
                base64-secret: THE-SAME-TOKEN-THAT-IS-USED-ON-THE-ARTEMIS-INSTANCES

    eureka:
        client:
            service-url:
                defaultZone: http://admin:${jhipster.registry.password}@localhost:8761/eureka/

**nginx config**

You still have to make the registry available:

1. ``sudo vim /etc/nginx/sites-available/registry.conf``

  .. code::

    server {
        listen 443 ssl http2;
        server_name REGISTRY_FQDN;
        ssl_session_cache shared:RegistrySSL:10m;
        include /etc/nginx/common/common_ssl.conf;
        add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload";
        add_header X-Frame-Options DENY;
        add_header Referrer-Policy same-origin;
        client_max_body_size 10m;
        client_body_buffer_size 1m;

        location / {
            proxy_pass              http://localhost:8761;
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

2. ``sudo ln -s /etc/nginx/sites-available/registry.conf /etc/nginx/sites-enabled/``

This enables the registry in nginx

3. ``sudo service nginx restart``

This will apply the config changes and the registry will be reachable.


WebSockets
^^^^^^^^^^

WebSockets should also be synchronized (so that a user connected to one instance can perform an action
which causes an update to users on different instances, without having to reload the page - such as quiz starts).
We use a so-called broker for this (named `Apache ActiveMQ Artemis
<https://activemq.apache.org/components/artemis/>`_).


It relays message between instances:

   .. figure:: setup/distributed/deployment_broker.drawio.png
      :align: center

**Setup**

1. Create a folder to store ActiveMQ

  .. code:: bash

        sudo mkdir /opt/activemq-distribution

2. Download ActiveMQ here: http://activemq.apache.org/components/artemis/download/

  .. code:: bash

        sudo wget -O /opt/activemq-distribution/activemq.tar.gz https://downloads.apache.org/activemq/activemq-artemis/2.13.0/apache-artemis-2.13.0-bin.tar.gz

3. Extract the downloaded contents

  .. code:: bash

        cd /opt/activemq-distribution
        sudo tar -xf activemq.tar.gz

4. Navigate to the folder with the CLI

  .. code:: bash

        cd /opt/activemq-distribution/apache-artemis-2.13.0/bin

5. Create a broker in the /opt/broker/broker1 directory, replace USERNAME and PASSWORD accordingly

  .. code:: bash

        sudo ./artemis create --user USERNAME --password PASSWORD --require-login /opt/broker/broker1

6. Adjust the permissions

  .. code:: bash

        sudo chown -R artemis:artemis /opt/broker
        sudo chmod g+rwx /opt/broker

7. Adjust the configuration of the broker: ``sudo vim /opt/broker/broker1/etc/broker.xml``

  .. code:: xml

    <?xml version='1.0'?>
    <configuration xmlns="urn:activemq"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:xi="http://www.w3.org/2001/XInclude"
                xsi:schemaLocation="urn:activemq /schema/artemis-configuration.xsd">

    <core xmlns="urn:activemq:core" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="urn:activemq:core ">

        <name>0.0.0.0</name>

        <journal-pool-files>10</journal-pool-files>

        <acceptors>
            <!-- STOMP Acceptor. -->
            <acceptor name="stomp">tcp://0.0.0.0:61613?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;protocols=STOMP;useEpoll=true;heartBeatToConnectionTtlModifier=6</acceptor>
        </acceptors>

        <connectors>
            <connector name="netty-connector">tcp://localhost:61616</connector>
        </connectors>

        <security-settings>
            <security-setting match="#">
                <permission type="createNonDurableQueue" roles="amq"/>
                <permission type="deleteNonDurableQueue" roles="amq"/>
                <permission type="createDurableQueue" roles="amq"/>
                <permission type="deleteDurableQueue" roles="amq"/>
                <permission type="createAddress" roles="amq"/>
                <permission type="deleteAddress" roles="amq"/>
                <permission type="consume" roles="amq"/>
                <permission type="browse" roles="amq"/>
                <permission type="send" roles="amq"/>
                <!-- we need this otherwise ./artemis data imp wouldn't work -->
                <permission type="manage" roles="amq"/>
            </security-setting>
        </security-settings>

        <address-settings>
            <!--default for catch all-->
            <address-setting match="#">
                <dead-letter-address>DLQ</dead-letter-address>
                <expiry-address>ExpiryQueue</expiry-address>
                <redelivery-delay>0</redelivery-delay>
                <!-- with -1 only the global-max-size is in use for limiting -->
                <max-size-bytes>-1</max-size-bytes>
                <message-counter-history-day-limit>10</message-counter-history-day-limit>
                <address-full-policy>PAGE</address-full-policy>
                <auto-create-queues>true</auto-create-queues>
                <auto-create-addresses>true</auto-create-addresses>
                <auto-create-jms-queues>true</auto-create-jms-queues>
                <auto-create-jms-topics>true</auto-create-jms-topics>
            </address-setting>
        </address-settings>
    </core>
    </configuration>

8. Service configuration: ``sudo vim /etc/systemd/system/broker1.service``

  .. code:: bash

    [Unit]
    Description=ActiveMQ-Broker
    After=network.target

    [Service]
    User=artemis
    WorkingDirectory=/opt/broker/broker1
    ExecStart=/opt/broker/broker1/bin/artemis run


    [Install]
    WantedBy=multi-user.target

9. Enable the service

  .. code:: bash

    sudo systemctl daemon-reload
    sudo systemctl enable broker1
    sudo systemctl start broker1

**Configuration of Artemis**

Add the following values to your Artemis config:

  .. code:: yaml

    spring:
        websocket:
            broker:
                username: USERNAME
                password: PASSWORD
                addresses: "localhost:61613"

``USERNAME`` and ``PASSWORD`` are the values used in step 5. Replace localhost if the broker runs on a separate machine.


File system
^^^^^^^^^^^

The last (and also easiest) part to configure is the file system:
You have to provide a folder that is shared between all instances of the application server (e.g. by using NFS).

You then have to set the following values in the application config:

  .. code:: yaml

    artemis:
        repo-clone-path: {{ artemis_repo_basepath }}/repos/
        repo-download-clone-path: {{ artemis_repo_basepath }}/repos-download/
        file-upload-path: {{ artemis_repo_basepath }}/uploads
        submission-export-path: {{ artemis_repo_basepath }}/exports

Where ``{{ artemis_repo_basepath }}`` is the path to the shared folder


The file system stores (as its names suggests) files, these are e.g. submissions to file upload exercises,
repositories that are checked out for the online editor, course icons, etc.


Scheduling
^^^^^^^^^^
Artemis uses scheduled tasks in various scenarios: e.g. to lock repositories on due date, clean up unused resources, etc.
As we now run multiple instances of Artemis, we have to ensure that the scheduled tasks are not executed multiple times.
Artemis uses to approaches for this:

1. Tasks for quizzes (e.g. evaluation once the quiz is due) are automatically distributed (using Hazelcast)

2. Tasks for other exercises are only scheduled on one instance:

You must add the ``Scheduling`` profile to **exactly one** instance of your cluster.
This instance will then perform scheduled tasks whereas the other instances will not.


nginx configuration
^^^^^^^^^^^^^^^^^^^
You have to change the nginx configuration (of Artemis) to ensure that the load is distributed between all instances.
This can be done by defining an upstream (containing all instances) and forwarding all requests to this upstream.

  .. code:: bash

    upstream artemis {
        server instance1:8080;
        server instance2:8080;
    }

Overview
^^^^^^^^

All instances can now communicate with each other on 3 different layers:

- Database cache
- WebSockets
- File system


You can see the state of all connected instances within the registry:

It relays message between instances:

   .. figure:: setup/distributed/registry.png
      :align: center
