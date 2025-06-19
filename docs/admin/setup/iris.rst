Iris Service
------------

Iris is an intelligent virtual tutor integrated into the Artemis platform.
It is designed to provide one-on-one programming assistance without human tutors.
The core technology of Iris is based on Generative AI and Large Language Models, like OpenAI's GPT and Meta's LLaMA.

Iris also powers other smart features in Artemis, like the automatic generation of descriptions for hints.

Iris is powered by Pyris, which is part of the EduTelligence suite.

This section outlines how to set up IRIS in your own Artemis instance.

Prerequisites
^^^^^^^^^^^^^

- Ensure you have a running instance of Artemis.
- Set up a running instance of Pyris_. Refer to the :doc:`pyris` for more information.

Enable the ``iris`` Spring profile:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

::

   --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local,iris

Configure Pyris API Endpoints:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The Pyris service is running on a dedicated machine and is addressed via
HTTP(s). We need to extend the configuration in the file
``src/main/resources/config/application-artemis.yml`` like so:

.. code:: yaml

   artemis:
     # ...
     iris:
         url: http://localhost:8000
         secret: abcdef12345

The secret can be any string. For more detailed instructions on how to set it up in Pyris, refer to the :doc:`pyris`.

.. important::
   Pyris (which powers Iris) is now part of the EduTelligence suite. Please check the `compatibility matrix <https://github.com/ls1intum/edutelligence#-artemis-compatibility>`_
   to ensure you're using compatible versions of Artemis and EduTelligence.

.. _Pyris: https://github.com/ls1intum/edutelligence/tree/main/iris
.. _pyris-documentation: :doc:`pyris`
