.. _nebula-setup:

Nebula Setup Guide
==============================

.. contents::

.. important::
   Nebula is part of the EduTelligence suite. For a successful integration, ensure that the versions you deploy remain compatible with your Artemis release by consulting the `EduTelligence compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_.

Overview
--------

Nebula provides AI-powered processing pipelines for lecture videos. Artemis currently integrates with the **Transcriber** service to
automatically generate lecture transcripts (including slide-number alignment) for attachment video units.

Artemis Configuration
---------------------

Prerequisites
^^^^^^^^^^^^^

- A running Artemis instance with the :code:`scheduling` Spring profile enabled. The scheduler polls Nebula every 30 seconds for finished jobs.
- A shared secret that will be used both in Artemis and in the Nebula gateway.
- A running instance of `GoCast (TUM-Live) <https://github.com/TUM-Dev/gocast>`_ where the lecture recordings are hosted in a public course.

Enable the Nebula module
^^^^^^^^^^^^^^^^^^^^^^^^

Set the Nebula toggle, URL, and shared secret in your Artemis configuration. The secret you specify here must match the API key that Nebula expects
in the :code:`Authorization` header.

.. code-block:: yaml

   artemis:
     nebula:
       enabled: true
       url: https://nebula.example.com
       secret-token: your-shared-secret

     tum-live:
       # Ensure this URL points to your GoCast (TUM-Live) instance. Add the /api/v2 suffix.
       api-base-url: https://api.tum.live.example.com/api/v2

.. note::
   Artemis uses :code:`server.url` internally when contacting Nebula. Make sure the property reflects the external URL of your Artemis instance.

Starting transcriptions inside Artemis
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

After Nebula is configured, instructors can launch transcriptions from the lecture unit editor. See the :ref:`lecture management guide <lectures>` for the detailed, instructor-facing workflow.

Nebula Service Deployment
--------------------------------------------

Prerequisites
^^^^^^^^^^^^^

- **Python 3.12**
- **Poetry**
- **FFmpeg** available in :code:`PATH`
- **Docker** and **Docker Compose** if you plan to run the API gateway or production deployment

Local development workflow
^^^^^^^^^^^^^^^^^^^^^^^^^^

The EduTelligence repository provides step-by-step instructions for running Nebula locally. Follow the `Quick Start for Developers <https://github.com/ls1intum/edutelligence/blob/main/nebula/README.md#-quick-start-for-developers>`_
section and start only the **Transcriber Service** (skip the FAQ service). When configuring the Nginx gateway, make sure the :code:`map` block includes the same shared secret
you configured in Artemis. Once running, the relevant health checks are at:

.. code-block:: bash

   curl http://localhost:3007/transcribe/health
   curl http://localhost:3007/health

Production deployment
^^^^^^^^^^^^^^^^^^^^^

For production deployments, follow the `Production Deployment guide in the Nebula README <https://github.com/ls1intum/edutelligence/blob/main/nebula/README.md#-production-deployment>`_
and provision only the transcriber container plus the nginx gateway. Use distinct configuration files (``.env``, ``llm_config.production.yml``, ``nginx.production.conf``), keep Whisper / GPT-4o credentials secure,
and omit the FAQ service unless you plan to experiment with rewriting features.

After the stack is up, verify that the gateway responds:

.. code-block:: bash

   curl https://nebula.example.com/health
   curl -H "Authorization: your-shared-secret" https://nebula.example.com/transcribe/health

Connecting Artemis and Nebula
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When both sides are configured:

- Artemis sends transcription jobs to :code:`POST /transcribe/start` with the shared secret.
- Nebula processes the job asynchronously and exposes status updates via :code:`GET /transcribe/status/{jobId}`.
- The Artemis scheduler polls Nebula every 30 seconds and persists completed transcripts on the associated lecture unit.

If Artemis reports unauthorized or internal errors when starting a job, double-check that:

- :code:`artemis.nebula.enabled=true`
- The :code:`artemis.nebula.url` matches the Nginx gateway URL (including scheme)
- The Artemis secret token equals the key configured in Nginx (:code:`map $http_authorization $api_key_valid`)
- Nebula can reach its configured Whisper and GPT-4o endpoints (inspect the transcriber logs for HTTP 401/429/500 responses)

With those pieces in place, instructors can automatically transcribe lecture recordings stored on TUM-Live without manual copy/paste workflows.
