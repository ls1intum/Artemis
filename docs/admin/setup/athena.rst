.. _athena_service:

Athena Service
--------------

The semi-automatic text assessment relies on the Athena_ service, which is part of the EduTelligence suite.
To enable automatic text assessments, special configuration is required:

Enable the ``athena`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,athena

Configure API Endpoints:
^^^^^^^^^^^^^^^^^^^^^^^^

The Athena service is running on a dedicated machine and is addressed via
HTTP. We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   artemis:
      # ...
      athena:
         url: http://localhost:5100
         secret: abcdef12345
         modules:
            # See https://github.com/ls1intum/edutelligence/tree/main/athena for a list of available modules
            text: module_text_cofee
            programming: module_programming_themisml

The secret can be any string. For more detailed instructions on how to set it up in Athena, refer to the Athena documentation_.

.. important::
   Athena is now part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_ 
   to ensure you're using compatible versions of Artemis and EduTelligence.

.. _Athena: https://github.com/ls1intum/edutelligence/tree/main/athena
.. _documentation: https://github.com/ls1intum/edutelligence/tree/main/athena
