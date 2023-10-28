Athena Service
--------------

The semi-automatic text assessment relies on the Athena_ service.
To enable automatic text assessments, special configuration is required:

Enable the ``athena`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling,athena

Configure API Endpoints:
^^^^^^^^^^^^^^^^^^^^^^^^

The Athena service is running on a dedicated machine and is addressed via
HTTP. We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   artemis:
     # ...
     athena:
         url: http://localhost:5000
         secret: abcdef12345

The secret can be any string. For more detailed instructions on how to set it up in Athena, refer to the Athena documentation_.

.. _Athena: https://github.com/ls1intum/Athena
.. _documentation: https://ls1intum.github.io/Athena
