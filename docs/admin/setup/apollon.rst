Apollon Service: Converting UML Models
======================================

The `Apollon Converter`_ is needed to convert models from their JSON representation to PDF.
Special configuration is required:

#. Enable the ``apollon`` Spring profile

    .. code:: bash

        --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling,apollon

#. Configure API Endpoints

    The Apollon conversion service is running on a dedicated machine and is addressed via
    HTTP. We need to extend the configuration in the file
    ``src/main/resources/config/application-artemis.yml`` like so:

    .. code:: yaml

        apollon:
            conversion-service-url: http://localhost:8080


.. _Apollon Converter: https://github.com/ls1intum/Apollon_converter
