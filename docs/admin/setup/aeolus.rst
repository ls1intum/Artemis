Aeolus Service
--------------

Aeolus is a service that provides a REST API for the Artemis platform to generate custom build plans for
programming exercises. It is designed to be used in combination with the Artemis platform to provide
build plans in multiple CI systems, currently Jenkins and LocalCI.

This section outlines how to set up Aeolus in your own Artemis instance.

Prerequisites
^^^^^^^^^^^^^

- Ensure you have a running instance of Artemis.
- Set up a running instance of Aeolus. See the `Aeolus documentation <https://ls1intum.github.io/Aeolus/>`_ for more information.

Enable the ``aeolus`` Spring profile
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,atlas,local,aeolus

Configure the Aeolus Endpoint
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Aeolus service can run on a dedicated machine since Artemis accesses it via a REST API call. We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` to include the Aeolus endpoint. How to do this is described in :ref:`configure artemis for aeolus`.

