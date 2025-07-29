.. _hyperion_service:

Hyperion Service
----------------

The **Hyperion** service is an AI-driven microservice for programming exercise creation assistance, designed to help instructors create programming exercises more easily and improve exercise quality. It's part of the EduTelligence suite and integrates with Artemis via REST API.

Hyperion provides key AI-powered features:

- **Exercise Consistency Checking**: Analyzes programming exercises for conflicts between problem statements, solution code, template code, and tests
- **Problem Statement Rewriting**: Improves and refines exercise descriptions using AI to enhance clarity and pedagogical value

Prerequisites
^^^^^^^^^^^^^

- **Deploy Hyperion Service** (external dependency) - See the `EduTelligence Hyperion documentation <https://github.com/ls1intum/edutelligence/tree/main/hyperion>`_
- **AI Model Access** - Configure OpenAI, Azure OpenAI, or Ollama in the Hyperion service
- **Network Connectivity** - Ensure Artemis can reach the Hyperion service via HTTP/HTTPS

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
       api-key: your-hyperion-api-key         # API key for authentication

Development Configuration:
^^^^^^^^^^^^^^^^^^^^^^^^^^

For development environments, typically using localhost:

.. code:: yaml

   artemis:
     hyperion:
       url: http://localhost:8000
       api-key: local-development-key

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

Health Check:
^^^^^^^^^^^^^

Artemis automatically monitors Hyperion service health through the ``/health`` endpoint. The health check uses a short timeout (10 seconds) to avoid blocking the application health monitoring.

.. important::
   Hyperion is part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_
   to ensure you're using compatible versions of Artemis and EduTelligence.

.. _EduTelligence Hyperion documentation: https://github.com/ls1intum/edutelligence/tree/main/hyperion
