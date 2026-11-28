---
id: index
title: Production Setup
sidebar_label: Production Setup
last_reviewed: 2026-11-28
---

For a local evaluation, follow [Try Artemis](/about/try). For a first production installation, start with the
[university example in the Artemis Ansible Collection](https://github.com/ls1intum/artemis-ansible-collection/tree/fba9e85b58ee66759d5b9b929b37c3e542c1e0b1/examples/university).
It shows the required inventory settings for a core server and a separate worker for student builds. The example pins
the deployment versions and describes the TLS ingress and secrets the university supplies.

Before opening a course, complete the [hardening checklist](/admin/production-setup/hardening-checklist) and rehearse
recovering a graded exercise from backup. You can enable [extension services](/admin/extensions-setup) later.
