.. _hyperion_service:

Hyperion Features (Spring AI)
-----------------------------

Artemis now implements Hyperion’s features directly using Spring AI. No external Hyperion service or OpenAPI client is required.

Provided features:

- Exercise Consistency Checking using the local AI provider via Spring AI
- Problem Statement Rewriting via Spring AI

Setup
^^^^^

1) Enable the hyperion profile when you want these endpoints available:

::

    --spring.profiles.active=dev,artemis,core,hyperion

2) Configure Spring AI (example for OpenAI):

.. code:: yaml

    spring:
       ai:
          openai:
             api-key: ${OPENAI_API_KEY}
             chat:
                options:
                   model: gpt-4o-mini

3) Use the endpoints under ``/api/hyperion`` as before.

Notes
^^^^^

- The legacy external Hyperion REST integration and OpenAPI-generated client have been removed.
- Health checks now report a local-ai status instead of remote service status.
