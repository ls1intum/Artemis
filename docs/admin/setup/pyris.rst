.. _pyris-setup:

Pyris Setup Guide
=================

.. contents::

Prerequisites
-------------

- A server/VM or local machine
- Docker installed on the machine (Required for Docker Setup)

Installation Steps
------------------

Clone the Pyris Repository
~~~~~~~~~~~~~~~~~~~~~~~~~~

To get started with Pyris development, you need to clone the Pyris repository (`https://github.com/ls1intum/Pyris`) into a directory on your machine. For example, you can clone the repository into a folder called ``Pyris``.

Example command:

.. code-block:: bash

   git clone https://github.com/ls1intum/Pyris.git Pyris

Create an Application Configuration File
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Create an ``application.local.yml`` file using the provided ``application.example.yml`` in the Pyris repository as a base.

Example command:

.. code-block:: bash

   cp Pyris/application.example.yml application.local.yml

Now you need to configure the ``application.local.yml`` file. Here is an example configuration:

.. code-block:: yaml

   api_keys:
     - token: "secret"

   weaviate:
     host: "localhost"
     port: "8001"
     grpc_port: "50051"

   env_vars:

Create LLM Config File
~~~~~~~~~~~~~~~~~~~~~~

Create an ``llm_config.local.yml`` file using the provided ``llm_config.example.yml`` in the Pyris repository as a base.

Example command:

.. code-block:: bash

   cp Pyris/llm_config.example.yml llm_config.local.yml

This file contains the configuration for the Large Language Model (LLM) that can be used by the pipelines in Pyris. The capability system in Pyris will use this configuration to determine which model to use for a pipeline.

Now you need to configure the ``llm_config.local.yml`` file.

Example OpenAI Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Here is an example configuration for OpenAI:

.. code-block:: yaml

   - api_key: "<your_openai_api_key>"
     tools: []
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

Example Azure OpenAI Configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Here is an example configuration for Azure OpenAI:

.. code-block:: yaml

   - api_key: "<your_azure_api_key>"
     tools: []
     capabilities:
       input_cost: 6
       output_cost: 16
       gpt_version_equivalent: 4.5  # Equivalent GPT version of the model
       context_length: 128000
       vendor: "OpenAI"
       privacy_compliance: true
       self_hosted: false
       image_recognition: true
       json_mode: true
     description: "GPT 4 Omni on Azure"
     id: "azure-gpt-4-omni"
     name: "GPT 4 Omni"
     type: "azure_chat"
     endpoint: "<your_azure_model_endpoint>"
     api_version: "2024-02-15-preview"
     azure_deployment: "gpt4o"
     model: "gpt4o"

Explanation of Configuration Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The configuration parameters are utilized through the capability system by pipelines in Pyris to select the appropriate model for a task.

**Parameter Descriptions:**

- ``api_key``: The API key for the model.
- ``capabilities``: The capabilities of the model.
  - ``context_length``: The maximum number of tokens the model can process in a single request.
  - ``gpt_version_equivalent``: The equivalent GPT version of the model in terms of overall capabilities (e.g., Llama 3.1 ≈ GPT-4).
  - ``image_recognition``: Whether the model supports image recognition (for multimodal models like GPT-4 Omni).
  - ``input_cost``: The cost of input tokens for the model.
  - ``output_cost``: The cost of output tokens for the model.
  - ``json_mode``: Whether the model supports structured JSON output mode.
  - ``privacy_compliance``: Whether the model complies with privacy regulations.
  - ``self_hosted``: Whether the model is self-hosted.
  - ``vendor``: The provider of the model (e.g., OpenAI or other vendors).
  - ``speed``: The model's processing speed.
- ``description``: Additional information about the model.
- ``id``: Unique identifier for the model across all models.
- ``model``: The official name of the model as used by the vendor.
- ``name``: A custom, human-readable name for the model.
- ``type``: The model type, used to select the appropriate client (e.g., ``openai_chat``, ``azure_chat``, ``ollama``).
- ``endpoint``: The URL to connect to the model.
- ``api_version``: The API version to use with the model.
- ``azure_deployment``: The deployment name of the model on Azure.
- ``tools``: The tools supported by the model.

**Notes on ``gpt_version_equivalent``:**

The ``gpt_version_equivalent`` field is subjective and used to compare capabilities of different models using GPT models as a reference. For example:

- GPT-4 Omni equivalent: 4.5
- GPT-4 Omni Mini equivalent: 4.25

.. warning::

   Most existing pipelines in Pyris require a model with a ``gpt_version_equivalent`` of 4.5 or higher. It is advised to define models in the ``llm_config.local.yml`` file with a ``gpt_version_equivalent`` of 4.5 or higher.

Start Pyris
-----------

Using Local Environment (Suitable for Development)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. warning::

   For local Weaviate vector database setup, please refer to `Weaviate Docs <https://weaviate.io/developers/weaviate/quickstart>`_.

**Prerequisites**

- Clone the Pyris repository to your local machine.
- Ensure you have correctly configured the ``llm_config.local.yml`` file.
- Ensure you have correctly configured the ``application.local.yml`` file.

**Setup Instructions**

1. **Check Python version:**

   .. code-block:: bash

      python --version

   (Should be 3.12)

2. **Install packages:**

   .. code-block:: bash

      pip install -r requirements.txt

3. **Start Pyris** using the following command:

   .. code-block:: bash

      APPLICATION_YML_PATH=<path-to-your-application-yml-file> \
      LLM_CONFIG_PATH=<path-to-your-llm-config-yml> \
      uvicorn app.main:app --reload

4. **Access the API docs at:**

   `http://localhost:8000/docs`

This setup should help you run the Pyris application on your local machine. Ensure you modify the configuration files as per your specific requirements before deploying.

Using Docker
------------

You can run Pyris in different environments: ``development`` or ``production``. Docker Compose is used to orchestrate the different services, including ``Pyris``, ``Weaviate``, and ``Nginx``.

**Prerequisites**

- Ensure Docker and Docker Compose are installed on your machine.
- Clone the Pyris repository to your local machine.

**Setup Instructions**

1. **Build and Run the Containers**

   You can run Pyris in different environments: development or production.

   - **For Development:**

     Use the following command to start the development environment:

     .. code-block:: bash

        docker compose -f docker-compose/pyris-dev.yml up --build

     This command will:

     - Build the Pyris application from the Dockerfile.
     - Start the Pyris application along with Weaviate in development mode.
     - Mount the local configuration files for easy modification.

     The application will be available at `http://localhost:8000`.

   - **For Production:**

     Use the following command to start the production environment:

     .. code-block:: bash

        docker compose -f docker-compose/pyris-production.yml up -d

     This command will:

     - Pull the latest Pyris image from the GitHub Container Registry.
     - Start the Pyris application along with Weaviate and Nginx in production mode.
     - Nginx will serve as a reverse proxy, handling SSL termination if certificates are provided.

     The application will be available at `https://<your-domain>`.

2. **Configuration**

   - **Weaviate**: Configured via the ``weaviate.yml`` file (default port 8001).
   - **Pyris Application**: Configuration is handled through environment variables and mounted YAML configuration files.
   - **Nginx**: Used for handling requests in production and is configured via ``nginx.yml``.

3. **Accessing the Application**

   - **Development**: Access the API documentation at ``http://localhost:8000/docs``.
   - **Production**: Access the application at your domain (e.g., `https://your-domain.com`).

4. **Stopping the Containers**

   - **Development:**

     .. code-block:: bash

        docker compose -f docker-compose/pyris-dev.yml down

   - **Production:**

     .. code-block:: bash

        docker compose -f docker-compose/pyris-production.yml down

5. **Logs and Debugging**

   - View the logs for a specific service, e.g., Pyris:

     .. code-block:: bash

        docker compose -f docker-compose/pyris-dev.yml logs pyris-app

   - For production, ensure that Nginx and Weaviate services are running smoothly and check their respective logs if needed.

This setup should help you run the Pyris application in both development and production environments with Docker. Ensure you modify the configuration files as per your specific requirements before deploying.

Conclusion
----------

That's it! You've successfully installed and configured Pyris.
