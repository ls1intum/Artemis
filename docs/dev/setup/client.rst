Client Setup
------------

You need to install Node and Npm on your local machine.

Using IntelliJ
^^^^^^^^^^^^^^

If you are using **IntelliJ** you can use the pre-configured ``Artemis (Client)``
run configuration that will be delivered with this repository:

* Choose ``Run | Edit Configurations...``
* Select the ``Artemis (Client)`` configuration from the ``npm section``
* Now you can run the configuration in the upper right corner of IntelliJ

.. _UsingTheCommandLine:

Using the command line
^^^^^^^^^^^^^^^^^^^^^^

You should be able to run the following
command to install development tools and dependencies. You will only
need to run this command when dependencies change in ``package.json``.

.. code:: bash

   npm install

To start the client application in the browser, use the following
command:

.. code:: bash

   npm run start

This compiles TypeScript code to JavaScript code, starts the live reloading feature
(i.e. whenever you change a TypeScript file and save, the client is automatically reloaded with the new code)
and will start the client application in your browser on
``http://localhost:9000``. If you have configured
``application-artemis.yml`` correctly, then you should be able to login
with your TUM Online account.

.. HINT::
   In case you encounter any problems regarding JavaScript heap memory leaks when executing ``npm run start`` or
   any other scripts from ``package.json``, you can adjust a
   `memory limit parameter <https://nodejs.org/docs/latest-v16.x/api/cli.html#--max-old-space-sizesize-in-megabytes>`__
   (``node-options=--max-old-space-size=6144``) which is set by default in the project-wide `.npmrc` file.

   If you still face the issue, you can try to set a lower/higher value than 6144 MB.
   Recommended values are 3072 (3GB), 4096 (4GB), 5120 (5GB) , 6144 (6GB), 7168 (7GB), and 8192 (8GB).

   You can override the project-wide `.npmrc` file by
   `using a per-user config file (~/.npmrc) <https://docs.npmjs.com/cli/v8/configuring-npm/npmrc>`__.

   Make sure to **not commit changes** in the project-wide ``.npmrc`` unless the Github build also needs these settings.


For more information, review `Working with
Angular <https://www.jhipster.tech/development/#working-with-angular>`__.
For further instructions on how to develop with JHipster, have a look at
`Using JHipster in
development <http://www.jhipster.tech/development>`__.
