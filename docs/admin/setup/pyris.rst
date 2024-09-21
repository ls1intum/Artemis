.. _pyris-setup:

Pyris Setup Guide
=================

.. contents::

Prerequisites
-------------

- A server/VM or local machine
- **Python 3.12**: Ensure that Python 3.12 is installed.

  .. code-block:: bash

     python --version

  (Should be 3.12)

- **Docker and Docker Compose**: Required for containerized deployment.

Local Environment Setup
-----------------------

1. **Clone the Pyris Repository**

   To get started with Pyris development, you need to clone the Pyris repository (`https://github.com/ls1intum/Pyris`) into a directory on your machine. For example, you can clone the repository into a folder called ``Pyris``.

   Example command:

   .. code-block:: bash

      git clone https://github.com/ls1intum/Pyris.git Pyris

2. **Install Dependencies**

   Install the required Python packages:

   .. code-block:: bash

      pip install -r requirements.txt

3. **Create Configuration Files**

   - **Create an Application Configuration File**

     Create an ``application.local.yml`` file in the root directory. This file includes configurations used by the application.

     Example command:

     .. code-block:: bash

        cp Pyris/application.example.yml application.local.yml

     Example ``application.local.yml``:

     .. code-block:: yaml

        api_keys:
          - token: "your-secret-token"

        weaviate:
          host: "localhost"
          port: "8001"
          grpc_port: "50051"

        env_vars:
          test: "test-value"

   - **Create LLM Config File**

     Create an ``llm_config.local.yml`` file in the root directory. This file includes a list of models with their configurations.

     Example command:

     .. code-block:: bash

        cp Pyris/llm_config.example.yml llm_config.local.yml

     **Example OpenAI Configuration**

     .. code-block:: yaml

        - id: "oai-gpt-35-turbo"
          name: "GPT 3.5 Turbo"
          description: "GPT 3.5 16k"
          type: "openai_chat"
          model: "gpt-3.5-turbo"
          api_key: "<your_openai_api_key>"
          tools: []
          capabilities:
            input_cost: 0.5
            output_cost: 1.5
            gpt_version_equivalent: 3.5
            context_length: 16385
            vendor: "OpenAI"
            privacy_compliance: false
            self_hosted: false
            image_recognition: false
            json_mode: true

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

     **Explanation of Configuration Parameters**

     The configuration parameters are utilized through the capability system by pipelines in Pyris to select the appropriate model for a task.

     **Parameter Descriptions:**

     - ``api_key``: The API key for the model.
     - ``capabilities``: The capabilities of the model.

       - ``context_length``: The maximum number of tokens the model can process in a single request.
       - ``gpt_version_equivalent``: The equivalent GPT version of the model in terms of overall capabilities.
       - ``image_recognition``: Whether the model supports image recognition.
       - ``input_cost``: The cost of input tokens for the model.
       - ``output_cost``: The cost of output tokens for the model.
       - ``json_mode``: Whether the model supports structured JSON output mode.
       - ``privacy_compliance``: Whether the model complies with privacy regulations.
       - ``self_hosted``: Whether the model is self-hosted.
       - ``vendor``: The provider of the model (e.g., OpenAI).
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

4. **Run the Server**

   Start the Pyris server:

   .. code-block:: bash

      APPLICATION_YML_PATH=./application.local.yml \
      LLM_CONFIG_PATH=./llm_config.local.yml \
      uvicorn app.main:app --reload

5. **Access API Documentation**

   Open your browser and navigate to `http://localhost:8000/docs` to access the interactive API documentation.

This setup should help you run the Pyris application on your local machine. Ensure you modify the configuration files as per your specific requirements before deploying.

Using Docker
------------

**Prerequisites**

- Ensure Docker and Docker Compose are installed on your machine.
- Clone the Pyris repository to your local machine.

**Docker Compose Files**

- **Development**: ``docker-compose/pyris-dev.yml``
- **Production with Nginx**: ``docker-compose/pyris-production.yml``
- **Production without Nginx**: ``docker-compose/pyris-production-internal.yml``

**Setup Instructions**

1. **Running the Containers**

   You can run Pyris in different environments: development or production.

   **Development Environment**

   - **Start the Containers**

     .. code-block:: bash

        docker-compose -f docker-compose/pyris-dev.yml up --build

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

         docker-compose -f docker-compose/pyris-production.yml up -d

      - Pulls the latest Pyris image.
      - Starts Pyris, Weaviate, and Nginx.
      - Nginx handles SSL termination and reverse proxying.

   3. **Access the Application**

      - Application URL: `https://your-domain.com`

   **Option 2: Without Nginx**

   1. **Start the Containers**

      .. code-block:: bash

         docker-compose -f docker-compose/pyris-production-internal.yml up -d

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

        docker-compose -f docker-compose/pyris-dev.yml logs -f pyris-app

   - **Rebuild Containers**

     If you've made changes to the code or configurations:

     .. code-block:: bash

        docker-compose -f <compose-file> up --build

3. **Customizing Configuration**

   - **Environment Variables**

     You can customize settings using environment variables:

     - ``PYRIS_DOCKER_TAG``: Specifies the Pyris Docker image tag.
     - ``PYRIS_APPLICATION_YML_FILE``: Path to your ``application.yml`` file.
     - ``PYRIS_LLM_CONFIG_YML_FILE``: Path to your ``llm-config.yml`` file.
     - ``PYRIS_PORT``: Host port for Pyris application (default is ``8000``).
     - ``WEAVIATE_PORT``: Host port for Weaviate REST API (default is ``8001``).
     - ``WEAVIATE_GRPC_PORT``: Host port for Weaviate gRPC interface (default is ``50051``).

   - **Configuration Files**

     Modify configuration files as needed:

     - **Pyris Configuration**: Update ``application.yml`` and ``llm-config.yml``.
     - **Weaviate Configuration**: Adjust settings in ``weaviate.yml``.
     - **Nginx Configuration**: Modify Nginx settings in ``nginx.yml`` and related config files.

4. **Additional Notes**

   - **Accessing Services Internally**

     - Within Docker, services can communicate using service names (e.g., ``pyris-app``, ``weaviate``).

   - **SSL Certificates**

     - Ensure SSL certificates are valid and properly secured.
     - Update paths in the Docker Compose file if necessary.

   - **Scaling**

     - For increased load, consider scaling services or using orchestration tools like Kubernetes.

This setup should help you run the Pyris application in both development and production environments with Docker. Ensure you modify the configuration files as per your specific requirements before deploying.

Troubleshooting
---------------

- **Port Conflicts**

  If you encounter port conflicts, change the host ports using environment variables:

  .. code-block:: bash

     export PYRIS_PORT=8080

- **Permission Issues**

  Ensure you have the necessary permissions for files and directories, especially for SSL certificates.

- **Docker Resources**

  If services fail to start, ensure Docker has sufficient resources allocated.

Conclusion
----------

That's it! You've successfully installed and configured Pyris.
