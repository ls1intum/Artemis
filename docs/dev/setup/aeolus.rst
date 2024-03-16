.. _Aeolus Setup:

Aeolus Setup
------------

This section describes how to set up the external service Aeolus for custom build plans for Jenkins and Integrated code lifecycle.

If you are setting Artemis up for the first time, these are the steps you should follow:

- Install and run Docker: https://docs.docker.com/get-docker
- :ref:`Start Aeolus`
- :ref:`configure artemis for aeolus`
- :ref:`Start Artemis with Aeolus`

.. _Start Aeolus:

Start Aeolus
^^^^^^^^^^^^

Start Aeolus with the following docker compose command:

.. code-block:: bash

   docker compose -f docker/aeolus.yml up -d


Check if Aeolus is running by visiting the following URL in your browser: http://localhost:8090/docs 
It should display the Aeolus API documentation.

.. _configure artemis for aeolus:

Configure Artemis to use Aeolus
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Append the following lines to your configuration file ``src/main/resources/config/application-local.yml``:

.. code-block:: yaml

       aeolus:
           url: http://localhost:8090


The server URL is the URL of the Aeolus server. The default port is 8090. This is enough for Aeolus to work with Artemis in a local environment.

API Key
"""""""

Without an API key, Artemis will send the location and credentials of the CI system with each request to Aeolus, which is fine
for a local environment. However, in a production environment, it is recommended to use an API key to authenticate the requests.

To use an API key, simply generate a random string and add it to the configuration file:

.. code-block:: yaml

       aeolus:
           url: http://localhost:8090
           token: <your-api-key>

Now, Aeolus will use this token to authenticate the requests. Make sure to keep this token secret and do not share it with anyone.
Furthermore, add the following environment variables to your Aeolus deployment:

.. code-block:: bash

   AEOLUS_API_KEYS=<your-api-key>
   # if you want to use jenkins as ci system
   JENKINS_URL=<jenkins-url>
   JENKINS_USERNAME=<jenkins-username>
   JENKINS_TOKEN=<jenkins-password-of-the-user>

.. _Start Artemis with Aeolus:

Start Artemis
^^^^^^^^^^^^^

Start Artemis with the profile ``aeolus`` so that the correct build script generator will be used,
e.g.:

::

   --spring.profiles.active=dev,localci,localvc,aeolus,artemis,scheduling,local


More information on how Aeolus works can be found on `GitHub <https://github.com/ls1intum/Aeolus>`_ or in the `Aeolus documentation <https://ls1intum.github.io/Aeolus/>`_.
