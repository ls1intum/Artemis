.. _hyperion_service:

Hyperion Service
----------------

The **Hyperion** service is an AI-driven microservice for programming exercise creation assistance, designed to help instructors improve exercise quality. It's part of the EduTelligence suite and integrates with Artemis via gRPC.

Hyperion provides key AI-powered features:

- **Exercise Consistency Checking**: Analyzes programming exercises for conflicts between problem statements, solution code, template code, and tests
- **Problem Statement Rewriting**: Improves and refines exercise descriptions using AI to enhance clarity and pedagogical value

Prerequisites
^^^^^^^^^^^^^

- **Deploy Hyperion Service** (external dependency) - See the `EduTelligence Hyperion documentation <https://github.com/ls1intum/edutelligence/tree/main/hyperion>`_
- **AI Model Access** - Configure OpenAI, Azure OpenAI, or Ollama in the Hyperion service
- **Network Connectivity** - Ensure Artemis can reach the Hyperion service via gRPC

Enable the ``hyperion`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,hyperion

Configure Hyperion Connection:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Hyperion service communicates with Artemis via gRPC. Configure the connection in your
``src/main/resources/config/application-artemis.yml`` file:

.. code:: yaml

   artemis:
     hyperion:
       host: localhost                      # Hyperion service hostname
       port: 50051                          # Hyperion service gRPC port
       timeouts:
         health: 5s                         # Timeout for health checks
         consistency-check: 5m              # Timeout for exercise consistency checks
         rewrite-problem-statement: 2m      # Timeout for problem statement rewriting

Development Configuration (Plaintext):
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For development environments, the service runs without TLS by default:

.. code:: yaml

   artemis:
     hyperion:
       host: localhost
       port: 50051
       timeouts:
         health: 5s
         consistency-check: 5m
         rewrite-problem-statement: 2m

Production Configuration with TLS:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For production deployments, enable TLS and configure proper security:

.. code:: yaml

   artemis:
     hyperion:
       host: hyperion.yourdomain.com
       port: 50051
       root-ca: /etc/certs/hyperion/ca.crt          # Path to CA certificate (required for TLS)
       client-cert: /etc/certs/hyperion/client.crt  # Path to client certificate (optional, for mTLS)
       client-key: /etc/certs/hyperion/client.key   # Path to client private key (required with client-cert)
       timeouts:
         health: 5s
         consistency-check: 5m
         rewrite-problem-statement: 2m

.. important::
   - TLS is automatically enabled when ``root-ca`` is configured
   - Mutual TLS (mTLS) is enabled when both ``client-cert`` and ``client-key`` are provided
   - Use TLS in production environments for secure communication
   - Ensure certificates are properly managed and rotated

Health Check:
^^^^^^^^^^^^^

Artemis automatically monitors Hyperion service health through the standard gRPC health checking protocol. You can manually verify the service status using:

.. code:: bash

   # Development (plaintext)
   grpc_health_probe -addr=localhost:50051

   # Production with TLS
   grpc_health_probe -addr=hyperion.yourdomain.com:50051 -tls -tls-ca-cert ca.crt

   # With mutual TLS (mTLS)
   grpc_health_probe -addr=hyperion.yourdomain.com:50051 -tls \
     -tls-ca-cert ca.crt -tls-client-cert client.crt -tls-client-key client.key

Troubleshooting:
^^^^^^^^^^^^^^^^

Common issues and solutions:

**Connection Refused**
   - Verify Hyperion service is running on the configured host and port
   - Check network connectivity between Artemis and Hyperion
   - Ensure firewall rules allow gRPC traffic on the configured port

**TLS/Certificate Issues**
   - Verify certificate file paths and permissions in ``artemis.hyperion`` configuration
   - Check certificate validity and expiration dates
   - Ensure CA certificates match between client and server

**Timeout Issues**
   - Adjust timeout values in ``artemis.hyperion.timeouts`` based on your environment
   - Monitor AI model response times in Hyperion service logs
   - Check network latency between Artemis and Hyperion

**Service Health Issues**
   - Check Hyperion service logs for startup errors
   - Verify AI model configuration (OpenAI API keys, Azure endpoints, etc.) in Hyperion
   - Ensure Hyperion has sufficient resources (CPU, memory) allocated

.. important::
   Hyperion is part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_
   to ensure you're using compatible versions of Artemis and EduTelligence.

.. _EduTelligence Hyperion documentation: https://github.com/ls1intum/edutelligence/tree/main/hyperion
