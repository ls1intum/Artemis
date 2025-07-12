.. _docker_debugging:

Debugging Artemis in Docker container
---------------------------------------------

Sometimes it is useful to debug Artemis while it is running in a Docker container as some bugs can be most easily reproduced in a Docker setup (e.g., for multi-node setups).

Follow these steps to set up debugging:

1. Adjust the docker setup to start the JVM with the correct arguments and to expose a debug port.

This can be done by adding the following lines to the relevant docker compose file and setting the debug port for each `artemis` service:

.. code-block:: yaml

   environment:
     - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:<debug-port>
   ports:
     - "<debug-port>:<host-port>"


This will start the JVM with the Java Debug Wire Protocol (JDWP) agent, allowing you to connect a debugger to port `<host-port>`.
If you use a multi-node setup, you need to add this to all nodes that you want to debug. Make sure that all use different host ports.


2. If you want to debug your own PR, replace the image name with your own PR image `ghcr.io/ls1intum/artemis:pr-<pr-number>`.

3. Use the `Remote Java Debugging for Docker` run configuration

  - This configuration is set up to connect to port 5005 as the default debug port.
  - If you used another port for the debugger port on the host, you need to change the port in the run configuration accordingly.
  - You can find the run configuration in the `Run/Debug Configurations` dialog in IntelliJ under `Remote JVM Debug -> Remote Java Debugging for Docker`.
  - For each Artemis node you want to debug, you need to create a separate run configuration with the correct port.



4. Start the relevant docker compose file with `docker compose -f <docker compose file name> up -d`.

5. If the server start fails with Liquibase errors, you can run the following command to reset the database:

.. attention::

   This command **deletes all data** in the `artemis` database by dropping it.
   Make sure you have a backup if you need to preserve existing data.

.. code-block:: bash

    docker exec -i artemis-mysql mysql -u root -p'' -e "DROP DATABASE artemis;"


Afterward, you can start the server again with the same command as in step 4.


6. Start the Remote JVM Debug configuration in IntelliJ.

7. Set breakpoints in the code you want to debug.

