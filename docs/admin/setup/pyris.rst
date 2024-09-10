.. _pyris-setup:

Pyris Setup Guide
-----------------

.. contents::

Prerequisites
^^^^^^^^^^^^^

- A server/VM or local machine
- Docker installed on the machine (Needed for Docker Setup)

Installation Steps
^^^^^^^^^^^^^^^^^^

Clone the Pyris Repository
""""""""""""""""""""""""""

To get started with Pyris, you need to clone the Pyris repository (``https://github.com/ls1intum/Pyris``) into a
directory on your machine. For example, you can clone the repository into a folder called ``Pyris`` in the directory
you created in the previous step.
E.g.: ``git clone https://github.com/ls1intum/Pyris.git Pyris``

Create an Application Configuration File
""""""""""""""""""""""""""""""""""""""""

Create an ``application.local.yml`` file using the provided ``application.example.yml`` in the Pyris repository as a base.
E.g.: ``cp Pyris/application.example.yml application.local.yml``

Now you need to configure the ``application.local.yml`` file. Here is an example configuration:

.. code-block:: yaml

    api_keys:
      - token: "secret"

    weaviate:
      host: "localhost"
      port: "8001"
      grpc_port: "50051"

    env_vars:
        test: "test"


Create LLM Config File
"""""""""""""""""""""""""""""""""
Create an ``llm_config.local.yml`` file using the provided ``llm_config.example.yml`` in the Pyris repository as a base.
E.g.: ``cp Pyris/llm_config.example.yml llm_config.local.yml``

This file contains the configuration for the Large Language Model (LLM) that can be used by the pipelines in Pyris.

The capability system in Pyris will use this configuration to determine which model to use for a pipeline.

Now you need to configure the ``llm_config.local.yml`` file.

Example OpenAI
**************
.. code-block:: yaml

    - api_key: <your_openai_api_key>
      capabilities:
        context_length: 16385
        gpt_version_equivalent: 3.5
        image_recognition: false
        input_cost: 0.5
        json_mode: true
        output_cost: 1.5
        privacy_compliance: false
        self_hosted: false
        vendor: OpenAI
      description: GPT 3.5 16k
      id: oai-gpt-35-turbo
      model: gpt-3.5-turbo
      name: GPT 3.5 Turbo
      type: openai_chat

Example Azure OpenAI
********************
.. code-block:: yaml

      api_key: "<your_azure_api_key>"
      tools: []
      capabilities:
        input_cost: 6
        output_cost: 16
        gpt_version_equivalent: 4.5 # This is the equivalent GPT version of the model. We use 4.5 for GPT 4 Omni model.
        context_length: 128000
        vendor: "OpenAI"
        privacy_compliance: True
        self_hosted: False
        image_recognition: True
        json_mode: True
      description: "GPT 4 Omni on Azure"
      id: "azure-gpt-4-omni"
      name: "GPT 4 Omni"
      type: "azure_chat"
      endpoint: <your_azure_model_endpoint>
      api_version: "2024-02-15-preview"
      azure_deployment: "gpt4o"
      model: "gpt4o"

Start Pyris
"""""""""""
Using local environment
***********************

.. warning::
    For local Weaviate vector database setup, please refer to `Weaviate Docs <https://weaviate.io/developers/weaviate/quickstart>`_.

=============
Prerequisites
=============
- Clone the Pyris repository to your local machine.
- Ensure you correctly configured ``llm_config.local.yml`` file.
- Ensure you correctly configured ``application.local.yml`` file.

=======================================================
Setup instructions
=======================================================
Follow the following steps for the local Pyris setup:

1. **Check python version:** python --version (should be 3.12)
2. **Install packages:** pip install -r requirements.txt
3. **Start Pyris** using the following command:
    .. code-block:: bash

      APPLICATION_YML_PATH=<path-to-your-application-yml-file> LLM_CONFIG_PATH=<path-to-your-llm-config-yml> uvicorn app.main:app --reload

4. **You can now access the API docs under the following link:** http://localhost:8000/docs

This setup should help you run the Pyris application on your local machine.
Ensure you modify the configuration files as per your specific requirements before deploying.

Using Docker
************

You can run Pyris in different environments: ``development`` or ``production``. Docker Compose is used to orchestrate the different services, including ``Pyris``, ``Weaviate``, and ``Nginx``.

=============
Prerequisites
=============

-  Ensure Docker and Docker Compose are installed on your machine.
-  Clone the Pyris repository to your local machine.

=======================================================
Setup Instructions
=======================================================
1. **Build and Run the Containers**
   You can run Pyris in different environments: development or
   production. Docker Compose is used to orchestrate the different
   services, including Pyris, Weaviate, and Nginx.
   -  **For Development:**

      Use the following command to start the development environment:

      .. code:: bash

         docker-compose -f docker-compose/pyris-dev.yml up --build

      This command will:

      -  Build the Pyris application from the Dockerfile.
      -  Start the Pyris application along with Weaviate in development
         mode.
      -  Mount the local configuration files (``application.local.yml``
         and ``llm-config.local.yml``) for easy modification.

      The application will be available at ``http://localhost:8000``.

   -  **For Production:**

      Use the following command to start the production environment:

      .. code:: bash

         docker-compose -f docker-compose/pyris-production.yml up -d

      This command will:

      -  Pull the latest Pyris image from the GitHub Container Registry.
      -  Start the Pyris application along with Weaviate and Nginx in
         production mode.
      -  Nginx will serve as a reverse proxy, handling SSL termination
         if certificates are provided.

      The application will be available at ``https://``.

2. **Configuration**

   -  **Weaviate**: Weaviate is configured via the ``weaviate.yml``
      file. By default, it runs on port 8001.
   -  **Pyris Application**: The Pyris application configuration is
      handled through environment variables and mounted YAML
      configuration files.
   -  **Nginx**: Nginx is used for handling requests in a production
      environment and is configured via ``nginx.yml``.

3. **Accessing the Application**

   -  For development, access the API documentation at:
      ``http://localhost:8000/docs``
   -  For production, access the application at your domain (e.g.,
      ``https://``).

4. **Stopping the Containers**

   To stop the running containers, use:

   .. code:: bash

      docker-compose -f docker-compose/pyris-dev.yml down

   or

   .. code:: bash

      docker-compose -f docker-compose/pyris-production.yml down
5. **Logs and Debugging**
   -  View the logs for a specific service, e.g., Pyris:

      .. code:: bash

         docker-compose -f docker-compose/pyris-dev.yml logs pyris-app

   -  For production, ensure that Nginx and Weaviate services are
      running smoothly and check their respective logs if needed.


This setup should help you run the Pyris application in both development
and production environments with Docker. Ensure you modify the
configuration files as per your specific requirements before deploying.

---------------

That's it! You've successfully installed and configured Pyris.
