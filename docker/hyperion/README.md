# Hyperion workload images

The generic execution supervisor is the **AI Worker**, not a Hyperion-specific
transport. Its profile-gated code uses the Artemis WAR. The
[AI Worker transport](../aiworker/README.md) owns the protocol-4 TLS CORE broker
configuration. Hyperion feature settings are separate.

This directory owns Hyperion's language/toolchain sandbox recipes, not broker
credentials, worker identity or generic execution policy. A supervisor without an
installed workload advertises no useful generation capacity. A process starting
successfully is not a generation qualification test.

Use a dedicated worker VM for staging and production. The supervisor's Docker socket
is host-level authority; never share core's, the database's or an exam build agent's
daemon. Generated code runs with a read-only root filesystem and without networking.
Pin the Artemis and sandbox images by digest and coordinate core, worker and broker
upgrades after draining active work. Worker process loss is not a durable local-outbox
guarantee: core must fence the lost incarnation using the evidence it already holds.
