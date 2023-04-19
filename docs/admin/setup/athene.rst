Athene Service: Semi-Automatic Text Exercise Assessment
=======================================================

The semi-automatic text assessment relies on the Athene_ service.
To enable automatic text assessments, special configuration is required:

#. Enable the ``athene`` Spring profile

    .. code:: bash

        --spring.profiles.active=dev,bamboo,bitbucket,jira,artemis,scheduling,athene

#. Configure API Endpoints

    The Athene service is running on a dedicated machine and is addressed via
    HTTP. We need to extend the configuration in the file
    ``src/main/resources/config/application-artemis.yml`` like so:

    .. code:: yaml

        artemis:
        # ...
        athene:
            url: http://localhost
            base64-secret: YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=
            token-validity-in-seconds: 10800

.. _Athene: https://github.com/ls1intum/Athene
