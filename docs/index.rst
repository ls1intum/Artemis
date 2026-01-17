.. _artemis:

======================================================
Artemis: Interactive Learning with Individual Feedback
======================================================

Main features
-------------

Artemis supports the following exercises:

#. Programming exercises with version control and automatic assessment with test cases and continuous integration
#. Quiz exercises with multiple choice, drag and drop and short answer quiz questions
#. Modeling exercises with semi-automatic assessment using machine learning concepts
#. Textual exercises with manual (and experimental semi-automatic) assessment
#. File upload exercises with manual assessment

For detailed information about all exercises, see the :doc:`User Guide <user/user-guide>`.

All these exercises are supposed to be run either live in the lecture with instant feedback or as homework. Students can submit their solutions multiple times within the due date and use the (semi-)automatically provided feedback to improve their solution.

.. toctree::
   :caption: User Guide
   :includehidden:
   :maxdepth: 3

   user/user-guide


.. toctree::
   :caption: Contributor Guide
   :includehidden:
   :maxdepth: 3

   dev/setup
   dev/development-process/development-process
   dev/guidelines
   dev/guidelines/reviewer-guidelines
   dev/system-design
   dev/migration
   dev/use-local-user-management
   dev/testservers
   dev/docker
   dev/playwright
   dev/open-source
   dev/local-moodle-setup-for-lti


.. toctree::
   :caption: Administration Guide
   :includehidden:
   :maxdepth: 3

   admin/setup
   admin/scaling
   admin/extension-services
   admin/registration
   admin/saml2-shibboleth
   admin/accessRights
   admin/troubleshooting
   admin/database
   admin/knownIssues
   admin/benchmarking-tool
   admin/telemetry
   admin/cleanup-service


.. toctree::
   :caption: Research
   :includehidden:
   :maxdepth: 3

   research/publications
