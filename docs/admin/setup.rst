.. _admin_setup:

Production Setup
================

The general setup steps are described in the :ref:`development setup steps <dev_setup>`.
This section describes some additional steps that are of interest for production or special setups.
For information on how to set up extension services to activate additional functionality in your Artemis instance, see
:ref:`their respective documentation <extensions_setup>`.

We recommend using the `Artemis Ansible Collection <https://github.com/ls1intum/artemis-ansible-collection>`_ for
setting up Artemis in production. The collection provides a set of Ansible roles that automate the setup of Artemis,
including the required external system with sane configuration defaults.

.. toctree::
   :includehidden:
   :maxdepth: 2

   setup/security
   setup/programming-exercises
   setup/customization
   setup/legal-documents
   setup/production-setup-tips
   setup/distributed
   setup/kubernetes
