.. _hyperion_admin_setup:

Hyperion Service
----------------

Hyperion extends Artemis with AI-assisted authoring features for programming exercises. It offers
consistency checks for problem statements and exercise artefacts and can rewrite instructions with the help
of generative AI. The functionality is provided entirely by Artemis and Spring AI, so no
EduTelligence service needs to be deployed.

Prerequisites
^^^^^^^^^^^^^

- A running Artemis instance that loads the ``core`` profile.
- Network access to an LLM provider that is supported by Spring AI (for example OpenAI or Azure OpenAI).
- A valid API key for the chosen provider.

Enable the Hyperion module
^^^^^^^^^^^^^^^^^^^^^^^^^^

Hyperion is disabled by default. Activate it by overriding the ``artemis.hyperion.enabled`` property in the
configuration that the server reads on startup (for example ``application-prod.yml``).

.. code:: yaml

   artemis:
     hyperion:
       enabled: true


Configure Spring AI
^^^^^^^^^^^^^^^^^^^^

Hyperion delegates all model interactions to Spring AI. Configure exactly one provider; Artemis currently
ships the Azure OpenAI starter, but classic OpenAI endpoints work as well when configured through Spring AI.

OpenAI
""""""

.. code:: yaml

   spring:
     ai:
       azure:
         openai:
           open-ai-api-key: <openai-api-key> # automatically sets the azure endpoint to https://api.openai.com/v1
           chat:
             options:
               deployment-name: gpt-5-mini # Or another (reasonably capable) model
               temperature: 1.0 # Required to be 1.0 for gpt-5

Azure OpenAI
""""""""""""

.. code:: yaml

   spring:
     ai:
       azure:
         openai:
           api-key: <azure-openai-api-key>
           endpoint: https://<your-resource-name>.openai.azure.com
           chat:
             options:
               deployment-name: <azure-deployment> # gpt-5-mini deployment recommended
               temperature: 1.0 # Required to be 1.0 for gpt-5

Verifying the integration
^^^^^^^^^^^^^^^^^^^^^^^^^

1. Restart the Artemis server and confirm that ``hyperion`` appears in ``activeModuleFeatures`` on
   ``/management/info``.
2. Log in as an instructor and open the programming exercise problem statement editor. New Hyperion actions
   appear in the markdown editor toolbar (rewrite and consistency check).
3. Run a consistency check to ensure the LLM call succeeds. Inspect the server logs for ``Hyperion`` entries
   if the request fails; misconfigured credentials and missing network egress are the most common causes.

Operational considerations
^^^^^^^^^^^^^^^^^^^^^^^^^^

- **Cost control:** Define usage policies and rate limits with your provider. Hyperion requests can process the
  full problem statement, so costs scale with exercise size.
- **Data protection:** Model providers receive exercise content. Obtain consent and align with institutional
  policies before enabling Hyperion in production.
