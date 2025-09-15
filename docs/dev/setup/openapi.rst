.. _openapi:

OpenAPI and API-driven Development
-----------------------------------
We migrate the Artemis server to an API-driven development approach using OpenAPI specifications. This especially helps us with client generation to avoid subtle bugs that are often unnoticed in code reviews.

Currently, the OpenAPI specs are only generated for the ``tutorialgroup`` and ``hyperion`` modules. We only support API-driven development for endpoints that are DTO-only and do not return domain objects.
If you want to make use of the OpenAPI specs, you need to ensure that the endpoint you are working on is a DTO-only endpoint. This means that the endpoint should not return or expect any domain objects, but only Data Transfer Objects (DTOs) or primitives.

When you update a REST endpoint or a DTO, you need to generate a new OpenAPI spec and generate new client files.

You need to follow these steps to generate the OpenAPI spec and client files:

1. To generate the OpenAPI spec, run the following command in the root directory:

.. code-block:: bash

    ./gradlew generateApiDocs -x webapp

This will generate an ``openapi.yaml`` file in the ``openapi`` directory.

2. To generate the client files, run the following command in the root directory:

.. code-block:: bash

    ./gradlew openApiGenerate

This command prunes to specs file to only include DTO-only endpoints, generates the client files based on the OpenAPI spec and performs some post-processing to make the client compile.
The generated client files are stored in the ``src/main/webapp/app/openapi`` directory.



