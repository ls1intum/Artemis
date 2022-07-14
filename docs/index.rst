.. _artemis:

======================================================
Artemis: Interactive Learning with Individual Feedback
======================================================

Main features
-------------

Artemis supports the following exercises:

#. :doc:`Programming exercises <user/exercises/programming>` with version control and automatic assessment with test cases and continuous integration
#. :doc:`Quiz exercises <user/exercises/quiz>` with multiple choice, drag and drop and short answer quiz questions
#. :doc:`Modeling exercises <user/exercises/modeling>` with semi-automatic assessment using machine learning concepts
#. :doc:`Textual exercises <user/exercises/textual>` with manual (and experimental semi-automatic) assessment
#. :doc:`File upload exercises <user/exercises/file-upload>` with manual assessment

All these exercises are supposed to be run either live in the lecture with instant feedback or as homework. Students can submit their solutions multiple times within the due date and use the (semi-)automatically provided feedback to improve their solution.

.. toctree::
   :caption: User Guide
   :includehidden:
   :maxdepth: 3

   user/exercises
   user/exam_mode
   user/orion
   user/communication


.. toctree::
   :caption: Contributor Guide
   :includehidden:
   :maxdepth: 3

   dev/setup
   dev/guidelines
   dev/system-design
   dev/migration
   dev/use-local-user-management
   Guided Tour <dev/guided-tour>
   dev/testservers
   dev/docker
   dev/cypress
   dev/open-source


.. toctree::
   :caption: Administration Guide
   :includehidden:
   :maxdepth: 3

   admin/registration
   admin/saml2-shibboleth
   admin/accessRights
   admin/troubleshooting
   admin/knownIssues
