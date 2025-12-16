.. _dev_setup:

Setup Guide
===========

This guide explains how to set up Artemis in your development environment or a demo production environment. For production setups, you would need to additionally consider topics discussed in the :ref:`administration setup <admin_setup>`.
Running Artemis consists of two main steps:

#. **Set up the development environment** (see :ref:`Development Environment Setup <development-environment-setup>`).
#. **Set up Artemis** (see :ref:`Set up Artemis <setting-up-artemis>`), which includes setting up optional but recommended features such as programming exercises.
   The installation guide provides two options for programming exercises, including version control and build system setup.

Artemis is based on `JHipster <https://jhipster.github.io>`__, combining:

* `Spring Boot <http://projects.spring.io/spring-boot>`__ (Java 25) for the application server.
* `Angular 20 <https://angular.dev>`__ (TypeScript) for the web application that serves the user interface.

To get an overview of the technology stack, visit the `JHipster Technology Stack <https://jhipster.github.io/tech-stack>`__
and refer to other tutorials on the JHipster homepage.

.. _development-environment-setup:

Development Environment Setup
-----------------------------

Before installing Artemis, install and configure the following dependencies:

1. **Java JDK 25**
   Download and install Java, e.g. from `Oracle JDK <https://www.oracle.com/java/technologies/javase-downloads.html>`__.

2. **Database Server**
   Please refer to :ref:`this guide <Database Setup>` for setting up a database server.
   Artemis uses Hibernate for database interactions and Liquibase for schema migrations.
   You can either use MySQL or PostgreSQL as the database server.

3. **Node.js (LTS >=24.7.0 < 25)**
   Download from `Node.js <https://nodejs.org/en/download>`__.
   Required for compiling and running the Angular client.

4. **npm (>=11.5.1)**
   Installed automatically with Node.js but can be updated separately.

5. **Graphviz (Optional, but Recommended for Production Setups)**
   Install from `Graphviz <https://www.graphviz.org/download/>`__ to enable graph generation in exercise descriptions.
   If missing, errors will appear in production.


IDE Setup
^^^^^^^^^

We recommend using `IntelliJ IDEA Ultimate <https://www.jetbrains.com/idea>`__ for development.
Refer to JHipster’s guide on configuring an IDE:
`JHipster IDE Setup <https://jhipster.github.io/configuring-ide>`__.

**Note:** The Community Edition of IntelliJ IDEA lacks Spring Boot support. See the
`comparison matrix <https://www.jetbrains.com/idea/features/editions_comparison_matrix.html>`__ for details.

.. _setting-up-artemis:

Set up Artemis
--------------

Once the development environment is set up, the next step is to configure the Artemis server. This includes optional features such as programming exercises, which require a version control and build system.

Start by following the :ref:`Server Setup <Server Setup>` guide, which explains the necessary configurations and provides details on enabling programming exercises.

After completing the server setup, proceed with setting up the Artemis client by following the :ref:`Client Setup Guide <client-setup>`.


Setup on Artemis Test Server with Helios
----------------------------------------

Helios is an application that provides a well-designed UI for deploying various services the AET is building using GitHub Actions.
You can use Helios to deploy an Artemis test server without needing to set up the server and client manually.
For detailed instructions on setting up Artemis with Helios, refer to the :ref:`setup-with-helios` guide.


.. toctree::
   :includehidden:
   :maxdepth: 2
   :hidden:

   setup/database
   setup/server
   setup/client
   setup/helios
   setup/integrated-code-lifecycle
   setup/jenkins-localvc
   setup/aeolus
   setup/openapi
   setup/common-problems
   setup/docker-compose
   setup/docker-debugging
   setup/local-database-tests

Production Setup and Extension Services
----------------------------------------

Production Setup
^^^^^^^^^^^^^^^^^

A production setup of these core services might need further adaptation to ensure security.
For details on securing your production environment, see the :ref:`administration setup <admin_setup>`.

Extension Services
^^^^^^^^^^^^^^^^^^^

Artemis allows extension with several additional services, such as:

- Mobile notifications via **Hermes**
- Automatic feedback generation using large language models (e.g., **Iris/Athena**)
- And more...

The setup for these additional services is described as part of the :ref:`extension service setup <extensions_setup>`.
