.. _lti:

LTI (Learning Tools Interoperability)
=====================================

.. contents:: Content of this document
    :local:
    :depth: 2

Overview
--------

What is LTI?

Artemis supports LTI 1.1 (deprecated) and LTI 1.3

Main supported features:
    - handle launches and create new users
    - communicate user grades for exercises with platform

General
-------

LTI can be configured for online courses. A course must me marked as an online course. (see more in the course creation docs).

The instructor can then navigate to the LTI Configuration page for a course. (Show screenshot). There are multiple tabs there.

Describe 4 tabs:
    - General - has the main
    - LTI 1.1 - configuration specific to LTI 1.3
    - LTI 1.3 - configuration specific to LTI 1.3
    - Exercises - used to link exercises

LTI 1.1
-------

Explain LTI 1.1 fields

LTI 1.3
-------

Explain LTI 1.3 fields, some of them have to be configured in Artemis and identify the platform, others identify Artemis and have to be copied to the platform

Manual configuration
^^^^^^^^^^^^^^^^^^^^

The LTI 1.3 configuration can be done manually. The fields have to be copied and set manually by an instructor in Artemis and the platform.

(Below is a screencast demonstrating how to do it for Moodle)

Dynamic registration
^^^^^^^^^^^^^^^^^^^^

The LTI 1.3 configuration can be done automatically. All we need is to copy the dynamic registration url and input it in platform, which also needs to support this service.

Note: You have to be logged in Artemis as an instructor of the course when you do this
Note: It might be necessary to have specific rights in the platform to be able to do this: e.g. be an Administrator

(Below is a screencast demonstrating how to do it for Moodle)

(Below is a screencast demonstrating how to do it for saltire)
(Note: for saltire the clientId isn't set automaticaly - therefore we need to still set it manually)
