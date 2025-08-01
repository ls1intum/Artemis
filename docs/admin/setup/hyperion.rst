.. _hyperion_service:

Hyperion Service
----------------

The **Hyperion** service is an AI-driven programming exercise creation assistance, designed to help instructors create programming exercises more easily and improve exercise quality. It's part of the EduTelligence suite and integrates with Artemis via REST API.

Hyperion currently provides two key AI-powered features:

- **Exercise Consistency Checking**: Analyzes programming exercises for conflicts between problem statements, solution code, and template code to ensure instructional coherence
- **Problem Statement Rewriting**: Improves and refines exercise descriptions using AI to enhance clarity and pedagogical value

The consistency checker currently focuses on the core exercise artifacts (problem statement, solution repository, and template repository). Future versions will expand to include test repository consistency and pedagogical consistency aspects such as coherence and constructive alignment with learning objectives.

.. important::
   Hyperion is part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_
   to ensure you're using compatible versions of Artemis and EduTelligence.

.. _EduTelligence Hyperion documentation: https://github.com/ls1intum/edutelligence/tree/main/hyperion

Prerequisites
^^^^^^^^^^^^^

- **Deploy Hyperion Service** (external dependency) - See setup options below
- **AI Model Access** - Configure one of the supported AI providers: OpenAI, Azure OpenAI, Ollama, OpenRouter, OpenWebUI, or Anthropic
- **Network Connectivity** - Ensure Artemis can reach the Hyperion service via HTTP/HTTPS

Local Development Setup
^^^^^^^^^^^^^^^^^^^^^^^

For local development, use the provided Docker Compose setup with hot reloading:

.. code:: bash

   # Navigate to the EduTelligence Hyperion docker directory
   cd /path/to/edutelligence/hyperion/docker

   # Start Hyperion in development mode
   docker compose -f compose.local.yaml up -d

This provides:

- **Hot reloading** for instant code changes during development
- **Volume mounting** for source code updates
- **Development optimizations** with ``fastapi dev --reload``

Before starting, configure your AI model provider:

.. code:: bash

   # In the Hyperion repository
   cp .env.example .env
   # Edit .env with your provider settings

Supported AI providers include OpenAI, Azure OpenAI, Ollama (local), OpenRouter, OpenWebUI, and Anthropic. See the ``.env.example`` file for detailed configuration options for each provider.

Production Deployment
^^^^^^^^^^^^^^^^^^^^^

For production, use the standard Docker Compose setup:

.. code:: bash

   # Using the production compose file
   docker compose -f compose.hyperion.yaml up -d

See the `EduTelligence Hyperion documentation <https://github.com/ls1intum/edutelligence/tree/main/hyperion>`_ for detailed deployment instructions.

Enable the ``hyperion`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,hyperion

Configure Hyperion Connection:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Hyperion service communicates with Artemis via REST API. Configure the connection in your
``src/main/resources/config/application-artemis.yml`` file:

.. code:: yaml

   artemis:
     hyperion:
       url: http://localhost:8000              # Hyperion service REST API URL
       api-key: local-development-key          # API key for authentication

Development Configuration:
^^^^^^^^^^^^^^^^^^^^^^^^^^

For development environments, typically using localhost:

.. code:: yaml

   artemis:
     hyperion:
       url: http://localhost:8000
       api-key: local-development-key

The default API key ``local-development-key`` matches the development configuration in Hyperion's ``.env.example`` file.

Production Configuration:
^^^^^^^^^^^^^^^^^^^^^^^^^

For production deployments, use HTTPS and secure API keys:

.. code:: yaml

   artemis:
     hyperion:
       url: https://hyperion.yourdomain.com
       api-key: ${HYPERION_API_KEY}           # Use environment variable for security

.. important::
   - Use HTTPS in production environments for secure communication
   - Store API keys as environment variables, not in configuration files
   - Ensure the Hyperion service is properly secured and accessible
   - For troubleshooting and advanced configuration, refer to the `EduTelligence Hyperion documentation <https://github.com/ls1intum/edutelligence/tree/main/hyperion>`_
