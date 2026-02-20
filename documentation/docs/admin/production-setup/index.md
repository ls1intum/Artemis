---
title: Production Setup
---

The general setup steps are described in the [development setup steps](/developer/setup).
This section describes some additional steps that are of interest for production or special setups.
For information on how to set up extension services to activate additional functionality in your Artemis instance, see
[their respective documentation](/admin/extensions-setup).

We recommend using the [Artemis Ansible Collection](https://github.com/ls1intum/artemis-ansible-collection) for
setting up Artemis in production. The collection provides a set of Ansible roles that automate the setup of Artemis,
including the required external system with sane configuration defaults.
