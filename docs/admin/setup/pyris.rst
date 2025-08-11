.. _pyris-setup:

Pyris Setup Guide
=================

.. contents::

.. important::
   Pyris is now part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_
   to ensure you're using compatible versions of Artemis and EduTelligence.

Prerequisites
-------------

- A server/VM or local machine
- **Python 3.13**: Ensure that Python 3.13 is installed.

  .. code-block:: bash

     python --version

  (Should be 3.13)

- **Docker and Docker Compose**: Required for containerized deployment.

Local Environment Setup
-----------------------

1. **Clone the EduTelligence Repository**

   To get started with Pyris development, you need to clone the EduTelligence repository (`https://github.com/ls1intum/edutelligence`) into a directory on your machine. Pyris is located in the `iris` subdirectory of the monorepo.

   Example command:

   .. code-block:: bash

      git clone https://github.com/ls1intum/edutelligence.git
      cd edutelligence/iris

2. **Install Dependencies**

   Navigate to the Pyris directory:

   .. code-block:: bash

      cd iris

   Install the required Python packages:

   .. code-block:: bash

      pip install -r requirements.txt

3. **Create Configuration Files**

   - **Create an Application Configuration File**

     Create an ``application.local.yml`` file in the iris directory. This file includes configurations used by the application.

     Example command:

     .. code-block:: bash

        cp application.example.yml application.local.yml

     Example ``application.local.yml``:

     .. code-block:: yaml

        # Token that Artemis will use to access Pyris
        api_keys:
          - token: "your-secret-token"

        # Weviate Connection
        weaviate:
          host: "localhost"
          port: "8001"
          grpc_port: "50051"

        env_vars: {}

   The `env_vars` section allows you to define custom environment variables that can be accessed within the Pyris application. These can be used for various purposes, such as setting feature flags or defining environment-specific configurations. Currently, the `env_vars` section is not used by Pyris, but it can be utilized in future versions.

   - **Create LLM Config File**

     Create an ``llm_config.local.yml`` file in the iris directory. This file includes a list of models with their configurations.

     Example command:

     .. code-block:: bash

        cp llm_config.example.yml llm_config.local.yml


     .. warning::

         The OpenAI configuration examples are intended solely for development and testing purposes and should not be used in production environments. For production use, we recommend configuring a GDPR-compliant solution.

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
          name: "GPT 4o"
          description: "GPT 4o on Azure"
          type: "azure_chat"
          endpoint: "<your_azure_model_endpoint>"
          api_version: "2024-02-15-preview"
          azure_deployment: "gpt4o"
          model: "gpt4o"
          api_key: "<your_azure_api_key>"
          tools: []
          capabilities:
            input_cost: 2.5
            output_cost: 10
            gpt_version_equivalent: 4.5  # Equivalent GPT version of the model
            context_length: 128000
            vendor: "OpenAI"
            privacy_compliance: false
            self_hosted: false
            image_recognition: true
            json_mode: true

     **Explanation of Configuration Parameters**

     The configuration parameters are used by pipelines in Pyris through the capability system to select the appropriate model for a given task. The parameter values under ``capabilities`` are mostly subjective and do not follow any standardized values.

     In the example configuration above, the values are based on the official documentation of the models.

     You can adjust the capabilities using the following example workflow:

        On their official website, OpenAI provides the following information about the `GPT-4o model <https://platform.openai.com/docs/models/gpt-4o>`_:

            - The model can process 128,000 tokens in a single request, so we set ``context_length`` to ``128k``.
            - The model is expected to outperform GPT-4 in terms of capabilities, so we set ``gpt_version_equivalent`` to ``4.5``.
            - The model is developed by OpenAI, so we set ``vendor`` to ``OpenAI``.
            - We cannot assume that the service providing the model (e.g., the official OpenAI API or Azure OpenAI) complies with the organization’s privacy regulations, so we set ``privacy_compliance`` to ``false``.
            - The model is not self-hosted, so we set ``self_hosted`` to ``false``.
            - The model supports image recognition, so we set ``image_recognition`` to ``true``.
            - The model supports structured JSON output mode, so we set ``json_mode`` to ``true``.
            - The input token cost is $2.50 per 1M tokens, so we set ``input_cost`` to ``2.5``.
            - The output token cost is $10.00 per 1M tokens, so we set ``output_cost`` to ``10``.

     .. note::
        The parameter values under ``capabilities`` are used to compare and rank models according to the requirements defined by a pipeline in order to select the most suitable model for a given task.

     The next section provides a more detailed explanation of the parameters used in the configuration file.

     **Parameter Descriptions:**

     - ``api_key``: The API key for the model.
     - ``capabilities``: The capabilities of the model.

       - ``context_length``: The maximum number of tokens the model can process in a single request.
       - ``gpt_version_equivalent``: The equivalent GPT version of the model in terms of overall capabilities.
       - ``image_recognition``: Whether the model supports image recognition.
       - ``input_cost``: The cost of input tokens for the model. The capability system will prioritize models with lower or equal input costs. The value can be determined by the admin according to model's pricing. A more expensive model can have a higher input cost.
       - ``output_cost``: The cost of output tokens for the model. The capability system will prioritize models with lower or equal output costs.The value can be determined by the admin according to model's pricing. A more expensive model can have a higher output cost.
       - ``json_mode``: Whether the model supports structured JSON output mode.
       - ``privacy_compliance``: Whether the model complies with privacy regulations. If true, capability system will prioritize privacy-compliant models. Privacy compliant models can be determined by the system admins according to organizational and legal requirements.
       - ``self_hosted``: Whether the model is self-hosted. If true, capability system will prioritize self-hosted models
       - ``vendor``: The provider of the model (e.g., OpenAI). This option is used by the capability system to filter models by vendor.
       - ``speed``: The model's processing speed.

     - ``description``: Additional information about the model.
     - ``id``: Unique identifier for the model across all models.
     - ``model``: The official name of the model as used by the vendor.
     - ``name``: A custom, human-readable name for the model.
     - ``type``: The model type, used to select the appropriate client (Currently available types are: ``openai_chat``, ``azure_chat``, ``ollama``).
     - ``endpoint``: The URL to connect to the model.
     - ``api_version``: The API version to use with the model.
     - ``azure_deployment``: The deployment name of the model on Azure.
     - ``tools``: The tools supported by the model. For now, we do not provide any predefined tools, but the field is necessary for the models with tool calling capabilities.

     **Notes on ``gpt_version_equivalent``:**

     The ``gpt_version_equivalent`` field is subjective and used to compare capabilities of different models using GPT models as a reference. For example:

     - GPT-4o equivalent: 4.5
     - GPT-4o Mini equivalent: 4.25
     - GPT-4 equivalent: 4.0
     - GPT-3.5 equivalent: 3.5

     .. warning::

        Most existing pipelines in Pyris require a model with a ``gpt_version_equivalent`` of 4.5 or higher. It is advised to define models in the ``llm_config.local.yml`` file with a ``gpt_version_equivalent`` of 4.5 or higher.

     **Required Pipeline Capabilities:**

     Below are the capabilities required by different pipelines in Pyris.

     1. **Exercise Chat Pipeline**
          - ``gpt_version_equivalent``: 4.5,
          - ``context_length``: 128000,
     2. **Course Chat Pipeline**
          - ``gpt_version_equivalent``: 4.5,
          - ``context_length``: 128000,
          - ``json_mode``: true,
     3. **Lecture Chat Pipeline** - Used by exercise and course chat pipelines
          - ``gpt_version_equivalent``: 3.5,
          - ``context_length``: 16385,
          - ``json_mode``: true,
     4. **Interaction Suggestions Pipeline** - Used by exercise and course chat pipelines
          - ``gpt_version_equivalent``: 4.5,
          - ``context_length``: 128000,
          - ``json_mode``: true


     .. warning::
         When defining models in the ``llm_config.local.yml`` file, ensure that there are models with capabilities defined above in order to meet the requirements of the pipelines. Otherwise pipelines may not be able to perform as well as expected, i.e. the quality of responses generated by the pipelines may be suboptimal.

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

        docker compose -f docker/pyris-dev.yml up --build

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

         docker compose -f docker/pyris-production.yml up -d

      - Pulls the latest Pyris image.
      - Starts Pyris, Weaviate, and Nginx.
      - Nginx handles SSL termination and reverse proxying.

   3. **Access the Application**

      - Application URL: `https://your-domain.com`

   **Option 2: Without Nginx**

   1. **Start the Containers**

      .. code-block:: bash

         docker compose -f docker/pyris-production-internal.yml up -d

      - Pulls the latest Pyris image.
      - Starts Pyris and Weaviate.

   2. **Access the Application**

      - Application URL: `http://localhost:8000`

2. **Managing the Containers**

   - **Stop the Containers**

     .. code-block:: bash

        docker compose -f <compose-file> down

     Replace ``<compose-file>`` with the appropriate Docker Compose file.

   - **View Logs**

     .. code-block:: bash

        docker compose -f <compose-file> logs -f <service-name>

     Example:

     .. code-block:: bash

        docker compose -f docker/pyris-dev.yml logs -f pyris-app

   - **Rebuild Containers**

     If you've made changes to the code or configurations:

     .. code-block:: bash

        docker compose -f <compose-file> up --build

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
