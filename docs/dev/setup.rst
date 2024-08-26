.. _dev_setup:

Setup Guide
===========

Setting up Artemis in your development environment or a demo production environment is really easy following
the instructions on this page. When you want to support programming exercises, we recommend using the :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`.

In this guide, you learn how to set up the development environment of
Artemis. Artemis is based on `JHipster <https://jhipster.github.io>`__,
i.e. \ `Spring Boot <http://projects.spring.io/spring-boot>`__
development on the application server using Java 21, and TypeScript
development on the application client in the browser using
`Angular <https://angular.io>`__. To get an overview of the
used technology, have a look at the `JHipster Technology stack <https://jhipster.github.io/tech-stack>`__
and other tutorials on the JHipster homepage.

You can find tutorials how to set up JHipster in an IDE (`IntelliJ IDEA
Ultimate <https://www.jetbrains.com/idea>`__ is recommended) on
https://jhipster.github.io/configuring-ide. Note that the Community
Edition of IntelliJ IDEA does not provide Spring Boot support (see the
`comparison
matrix <https://www.jetbrains.com/idea/features/editions_comparison_matrix.html>`__).
Before you can build Artemis, you must install and configure the
following dependencies/tools on your machine:

1. `Java
   JDK <https://www.oracle.com/java/technologies/javase-downloads.html>`__:
   We use Java (JDK 21) to develop and run the Artemis application
   server, which is based on `Spring
   Boot <http://projects.spring.io/spring-boot>`__.
2. `MySQL Database Server 8 <https://dev.mysql.com/downloads/mysql>`__, or `PostgreSQL <https://www.postgresql.org/>`_:
   Artemis uses Hibernate to store entities in an SQL database and Liquibase to
   automatically apply schema transformations when updating Artemis.
3. `Node.js <https://nodejs.org/en/download>`__: We use Node LTS (>=20.16.0 < 21) to compile
   and run the client Angular application. Depending on your system, you
   can install Node either from source or as a pre-packaged bundle.
4. `Npm <https://nodejs.org/en/download>`__: We use Npm (>=10.8.0) to
   manage client side dependencies. Npm is typically bundled with Node.js,
   but can also be installed separately.
5. ( `Graphviz <https://www.graphviz.org/download/>`__: We use Graphviz to generate graphs within exercise task
   descriptions.
   It's not necessary for a successful build,
   but it's necessary for production setups as otherwise errors will show up during runtime. )
6. A **version control** and **build** system is necessary for the **programming exercise** feature of Artemis.
   There are multiple stacks available for the integration with Artemis:

   * :ref:`Integrated Code Lifecycle Setup <Integrated Code Lifecycle Setup>`
   * :ref:`LocalVC and Jenkins <Jenkins and LocalVC Setup>`
   * :ref:`GitLab and Jenkins <Jenkins and GitLab Setup>`
   * :ref:`GitLab and GitLab CI <GitLab CI and GitLab Setup>` (experimental, not yet production ready)

------------------------------------------------------------------------------------------------------------------------

.. note::

    This setup guide describes the core setup for a development Artemis instance and its database.
    It also contains the setup steps for the version control and continuous integrations systems needed for programming exercises.
    A production setup of those core services might need to be adapted further to be secure.
    Check the :ref:`administration setup <admin_setup>` for that.

.. note::

    Artemis allows extension with several additional services, e.g., for mobile notifications ('Hermes'),
    automatic feedback generation using large language models ('Iris/Pyris'), …
    Their setup is described as part of the :ref:`extension service setup <extensions_setup>`.

------------------------------------------------------------------------------------------------------------------------

.. toctree::
   :includehidden:
   :maxdepth: 2

   setup/database
   setup/server
   setup/client
   setup/integrated-code-lifecycle
   setup/jenkins-localvc
   setup/jenkins-gitlab
   setup/gitlabci-gitlab
   setup/aeolus
   setup/common-problems
   setup/docker-compose
   setup/local-database-tests
