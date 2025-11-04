.. _pyris-setup:
.. _iris-setup:

Iris & Pyris Setup Guide
========================

.. contents::

.. important::
   Pyris is now part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_
   to ensure you're using compatible versions of Artemis and EduTelligence.

Overview
--------

Iris is an intelligent virtual tutor integrated into Artemis, providing one-on-one programming assistance, course content support,
and competency generation for students. Iris relies on Pyris, an intermediary service from the EduTelligence suite that brokers
requests to Large Language Models (LLMs) via a FastAPI backend.

This guide consolidates everything you need to configure both Artemis and Pyris so they communicate securely and reliably.

Artemis Configuration
---------------------

Prerequisites
^^^^^^^^^^^^^

- Ensure you have a running instance of Artemis.
- Have access to the Artemis deployment configuration (e.g., ``application-artemis.yml``).
- Decide on a shared secret that will also be configured in Pyris.

Enable the ``iris`` Spring profile
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,iris

Configure Pyris API endpoints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Pyris service is addressed by Artemis via HTTP(s). Extend ``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   artemis:
     # ...
     iris:
         url: http://localhost:8000
         secret: abcdef12345

.. tip::
   The value of ``secret`` must match one of the tokens configured under ``api_keys`` in your Pyris ``application.local.yml``.

For detailed information on deploying and configuring Pyris itself, continue with the next section.

Pyris Service Setup
-------------------

Prerequisites
^^^^^^^^^^^^^

- A server/VM or local machine.
- **Python 3.12**: Ensure that Python 3.12 is installed.

  .. code-block:: bash

     python --version

  (Should be 3.12)

- **Poetry**: Used to manage Python dependencies and the virtual environment.
- **Docker and Docker Compose**: Required if you want to run Pyris via containers.

Local Development Setup
^^^^^^^^^^^^^^^^^^^^^^^

1. **Clone the EduTelligence Repository**

   Clone the EduTelligence repository (`https://github.com/ls1intum/edutelligence`) onto your machine and switch into the ``iris`` subdirectory.

   .. code-block:: bash

      git clone https://github.com/ls1intum/edutelligence.git
      cd edutelligence/iris

2. **Install Dependencies**

   Pyris uses Poetry for dependency management. Install all required packages (this also creates the virtual environment):

   .. code-block:: bash

      poetry install

   .. tip::
      Install the repository-wide pre-commit hooks from the EduTelligence root directory with ``pre-commit install`` if you plan to contribute changes.

3. **Create Configuration Files**

   - **Create an Application Configuration File**

     Create an ``application.local.yml`` file in the ``iris`` directory, based on the provided example.

     .. code-block:: bash

        cp application.example.yml application.local.yml

     Example ``application.local.yml``:

     .. code-block:: yaml

        # Token that Artemis will use to access Pyris
        api_keys:
          - token: "your-secret-token"

        # Weaviate connection
        weaviate:
          host: "localhost"
          port: "8001"
          grpc_port: "50051"

        env_vars:

     Make sure the token you define here matches the ``secret`` configured in Artemis.

   - **Create an LLM Config File**

     Create an ``llm_config.local.yml`` file in the ``iris`` directory.

     .. code-block:: bash

        cp llm_config.example.yml llm_config.local.yml

     .. warning::

         The OpenAI configuration examples are intended solely for development and testing purposes and should not be used in production environments. For production use, we recommend configuring a GDPR-compliant solution.

     **Example OpenAI Configuration**

     .. code-block:: yaml

        - id: "oai-gpt-41-mini"
          name: "GPT 4.1 Mini"
          description: "GPT 4.1 Mini on OpenAI"
          type: "openai_chat"
          model: "gpt-4.1-mini"
          api_key: "<your_openai_api_key>"
          tools: []
          cost_per_million_input_token: 0.4
          cost_per_million_output_token: 1.6

     **Example Azure OpenAI Configuration**

     .. code-block:: yaml

        - id: "azure-gpt-4-omni"
          name: "GPT 4 Omni"
          description: "GPT 4 Omni on Azure"
          type: "azure_chat"
          endpoint: "<your_azure_model_endpoint>"
          api_version: "2024-02-15-preview"
          azure_deployment: "gpt4o"
          model: "gpt4o"
          api_key: "<your_azure_api_key>"
          tools: []
          cost_per_million_input_token: 0.4
          cost_per_million_output_token: 1.6

     **Explanation of Configuration Parameters**

     The configuration parameters are used by pipelines in Pyris to select the appropriate model for a given task.

     - ``api_key``: The API key for the model.
     - ``description``: Additional information about the model.
     - ``id``: Unique identifier for the model across all models.
     - ``model``: The official name of the model as used by the vendor. This value is also used for model selection inside Pyris (e.g., ``gpt-4.1`` or ``gpt-4.1-mini``).
     - ``name``: A human-readable name for the model.
     - ``type``: The model type used to select the appropriate client (e.g., ``openai_chat``, ``azure_chat``, ``ollama``).
     - ``endpoint``: The URL used to connect to the model (if required by the provider).
     - ``api_version``: The API version to use with the model (provider specific).
     - ``azure_deployment``: The deployment name of the model on Azure.
     - ``tools``: Tools supported by the model.
     - ``cost_per_million_input_token`` / ``cost_per_million_output_token``: Pricing information used for routing when multiple models satisfy the same requirements.

     .. note::
        Most existing pipelines currently require the full GPT-4.1 model family to be configured. Monitor Pyris logs for warnings about missing models so you can update your ``llm_config.local.yml`` accordingly.

4. **Run the Server**

   Start the Pyris server:

   .. code-block:: bash

      APPLICATION_YML_PATH=./application.local.yml \
      LLM_CONFIG_PATH=./llm_config.local.yml \
      uvicorn app.main:app --reload

5. **Access API Documentation**

   Open your browser and navigate to `http://localhost:8000/docs` to access the interactive API documentation.

Using Docker
^^^^^^^^^^^^

**Prerequisites**

- Ensure Docker and Docker Compose are installed on your machine.
- Clone the EduTelligence repository to your local machine.
- Create the necessary configuration files as described in the previous section.

**Docker Compose Files**

- **Development**: ``docker/pyris-dev.yml``
- **Production with Nginx**: ``docker/pyris-production.yml``
- **Production without Nginx**: ``docker/pyris-production-internal.yml``

**Setup Instructions**

1. **Running the Containers**

   You can run Pyris in different environments: development or production.

   **Development Environment**

   - **Start the Containers**

     .. code-block:: bash

        docker-compose -f docker/pyris-dev.yml up --build

     - Builds the Pyris application.
     - Starts Pyris and Weaviate in development mode.
     - Mounts local configuration files for easy modification.

   - **Access the Application**

     - Application URL: `http://localhost:8000`
     - API Docs: `http://localhost:8000/docs`

   **Production Environment**

   **Option 1: With Nginx**

   1. **Prepare SSL Certificates**

      - Place your SSL certificate (`fullchain.pem`) and private key (`priv_key.pem`) in the specified paths or update the paths in the Docker Compose file.

   2. **Start the Containers**

      .. code-block:: bash

         docker-compose -f docker/pyris-production.yml up -d

      - Pulls the latest Pyris image.
      - Starts Pyris, Weaviate, and Nginx.
      - Nginx handles SSL termination and reverse proxying.

   3. **Access the Application**

      - Application URL: `https://your-domain.com`

   **Option 2: Without Nginx**

   1. **Start the Containers**

      .. code-block:: bash

         docker-compose -f docker/pyris-production-internal.yml up -d

      - Pulls the latest Pyris image.
      - Starts Pyris and Weaviate.

   2. **Access the Application**

      - Application URL: `http://localhost:8000`

2. **Managing the Containers**

   - **Stop the Containers**

     .. code-block:: bash

        docker-compose -f <compose-file> down

     Replace ``<compose-file>`` with the appropriate Docker Compose file.

   - **View Logs**

     .. code-block:: bash

        docker-compose -f <compose-file> logs -f <service-name>

     Example:

     .. code-block:: bash

        docker-compose -f docker/pyris-dev.yml logs -f pyris-app

   - **Rebuild Containers**

     If you've made changes to the code or configurations:

     .. code-block:: bash

        docker-compose -f <compose-file> up --build

3. **Customizing Configuration**

   - **Environment Variables**

     You can customize settings using environment variables:

     - ``PYRIS_DOCKER_TAG``: Specifies the Pyris Docker image tag.
     - ``PYRIS_APPLICATION_YML_FILE``: Path to your ``application.yml`` file.
     - ``PYRIS_LLM_CONFIG_YML_FILE``: Path to your ``llm_config.yml`` file.
     - ``PYRIS_PORT``: Host port for Pyris application (default is ``8000``).
     - ``WEAVIATE_PORT``: Host port for Weaviate REST API (default is ``8001``).
     - ``WEAVIATE_GRPC_PORT``: Host port for Weaviate gRPC interface (default is ``50051``).

   - **Configuration Files**

     Modify configuration files as needed:

     - **Pyris Configuration**: Update ``application.yml`` and ``llm_config.yml``.
     - **Weaviate Configuration**: Adjust settings in ``weaviate.yml``.
     - **Nginx Configuration**: Modify Nginx settings in ``nginx.yml`` and related config files.

Troubleshooting
^^^^^^^^^^^^^^^

- **Port Conflicts**

  If you encounter port conflicts, change the host ports using environment variables:

  .. code-block:: bash

     export PYRIS_PORT=8080

- **Permission Issues**

  Ensure you have the necessary permissions for files and directories, especially for SSL certificates.

- **Docker Resources**

  If services fail to start, ensure Docker has sufficient resources allocated.

Conclusion
^^^^^^^^^^

With Artemis configured to communicate with Pyris and Pyris deployed locally or via Docker, Iris is ready to support your courses.
