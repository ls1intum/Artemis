Apollon Service
---------------

The `Apollon Converter`_ is needed to convert models from their JSON representaiton to PDF.
Special configuration is required:

Enable the ``apollon`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,apollon

Configure API Endpoints:
^^^^^^^^^^^^^^^^^^^^^^^^

The Apollon conversion service is running on a dedicated machine and is addressed via
HTTP. We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   apollon:
      conversion-service-url: http://localhost:8080


.. _Apollon Converter: https://github.com/ls1intum/Apollon_converter
