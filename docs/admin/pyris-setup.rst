.. _pyris-setup:

Pyris Setup Guide
=================

.. contents::

Prerequisites
---------------------

- A server/VM or local machine
- Docker installed on the machine

Installation Steps
-------------------------

Create a Directory for Pyris Deployment
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Create a directory where you will deploy Pyris. For example, you can create a directory at ``/opt/pyris`` on the machine.
For local development, you can create a directory at ``~/pyris``.

Clone the Pyris Repository
^^^^^^^^^^^^^^^^^^^^^^^^^^

To get started with Pyris, you need to clone the Pyris repository (``https://github.com/ls1intum/Pyris``) into a
directory on your machine. For example, you can clone the repository into a folder called ``Pyris`` in the directory
you created in the previous step.
E.g.: ``git clone https://github.com/ls1intum/Pyris.git Pyris``

Create an Application Configuration File
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Create an ``application.yml`` file using the provided ``application.example.yml`` in the Pyris repository as a base.
E.g.: ``cp Pyris/application.example.yml application.yml``

Now you need to configure the ``application.yml`` file. Here is an example configuration:

.. code-block:: yaml

    pyris:
        api_keys:
          - comment: Artemis
            llm_access: [AZURE_GPT35_TURBO_16K_0613, OPENAI_GPT35_TURBO_16K_0613]
            token: super-secret
        cache:
            hazelcast:
                host: cache
                port: 5701
        llms:
            AZURE_GPT35_TURBO_16K_0613:
                description: GPT-3.5 on Azure model from 2023-06-13 with 16k context length
                name: GPT-3.5 (16k, Azure)
                llm_credentials:
                    api_type: azure
                    api_base: 'https://<your-url>.azure.com/'
                    api_version: 2023-03-15-preview
                    deployment_id: gpt-35-16k
                    model: gpt-3.5-turbo
                    token: <token>
            OPENAI_GPT35_TURBO_16K_0613:
                description: GPT-3.5 on OpenAI from 2023-06-13 with 16k context length
                name: GPT-3.5 Turbo (16k, OpenAI)
                llm_credentials:
                    chat_mode: 'True'
                    model: gpt-3.5-turbo-16k-0613
                    token: <token>

Create Environment Variables File
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Create a ``.env`` file containing the following variables:

- ``PYRIS_DOCKER_TAG``: The Docker tag to use. Ideally use ``latest`` here.
- ``PYRIS_APPLICATION_YML_FILE``: The absolute path to the ``application.yml`` file that you created. E.g.: ``/opt/pyris/application.yml``
- ``NGINX_PROXY_SSL_CERTIFICATE_PATH``: The absolute path to the SSL certificate to use. Defaults to self-signed certificates. For production, you should use a valid SSL certificate. For local development, you an ignore this variable.
- ``NGINX_PROXY_SSL_CERTIFICATE_KEY_PATH``: The absolute path to the SSL certificate key to use. Defaults to self-signed certificates. For production, you should use a valid SSL certificate. For local development, you an ignore this variable.

Start Pyris
^^^^^^^^^^^

You can now start Pyris using the following command:

.. code-block:: bash

   docker compose --project-directory "$PROJECT_DIR" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --pull always --no-build

- ``$PROJECT_DIR`` should point to the Pyris directory you created.
- ``$COMPOSE_FILE`` should point to the ``docker/pyris-production.yml`` file in the Pyris repository.
- ``$ENV_FILE`` should point to the ``.env`` file you just created.

E.g.:

.. code-block:: bash

   docker compose --project-directory "/opt/pyris" -f "/opt/pyris/Pyris/docker/pyris-production.yml" --env-file "/opt/pyris/.env" up -d --pull always --no-build

This will start the Pyris application on your server/VM.

That's it! You've successfully installed and configured Pyris.
