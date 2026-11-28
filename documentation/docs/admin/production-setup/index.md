---
id: index
title: Production Setup
sidebar_label: Production Setup
last_reviewed: 2026-11-28
---

The general setup steps are described in the [development setup steps](/developer/setup).
This section describes some additional steps that are of interest for production or special setups.
For information on how to set up extension services to activate additional functionality in your Artemis instance, see
[their respective documentation](/admin/extensions-setup).

We recommend using the [Artemis Ansible Collection](https://github.com/ls1intum/artemis-ansible-collection) for
setting up Artemis in production. The collection provides a set of Ansible roles that automate the setup of Artemis,
including the required external system with sane configuration defaults.

## Upgrade core and build agents

Use the same release or CI commit for every node. Dedicated core nodes run
`Artemis-core.war`. Standalone LocalCI agents run `Artemis-buildagent.war`.
Installations that combine both profiles in one JVM use the universal `Artemis.war`.
Before an upgrade or rollback, suspend new submissions and drain queued builds,
running builds and pending results. Drain AI generation and pending deliveries
on installations with generation workers. Take deployment and database backups
appropriate to the release's migrations.

In the **Deploy Artemis with Ansible** workflow, enter the CI branch and exact commit SHA,
or enter a numeric release tag. For a rollback to a universal release, enter that tag and
select **Use the universal WAR of the specified rollback release**. The workflow resolves
the inventory group for the selected environment. For a CI commit that predates role WARs,
it verifies the universal artifact from the same run and selects its exact versioned filename. Its preflight verifies that each node
belongs to one artifact group and checks both executable digests before systemd stops.

To deploy directly, run the wrapper from an `artemis-ansible` checkout with its dependencies
installed. Set `source_checkout` to the absolute path of the Artemis source checkout.
Select the matching inventory cluster and version-update playbook. For a release:

```bash
wrapper="$source_checkout/deployment/ansible"
update="$PWD/playbooks/artemis-staging1/nodes-version-update.yml"
args=(-e "@$wrapper/role-wars.yml" -e artemis_cluster=artemis_staging1 -e "artemis_update_playbook=$update")
ansible-playbook "$wrapper/deploy-role-wars.yml" "${args[@]}" -e "artemis_release=$RELEASE"
```

For CI, extract the `Artemis-role-wars` artifact from one run into its own directory and
replace the last argument with `-e "artemis_war_directory=$WAR_DIRECTORY"`, using an absolute path.
Add `--tags preflight` to check the inputs on the controller without running the deployment.
The collection installs the selected executable at its existing systemd path, saves
the previous WAR with the `.old` suffix, and waits for node 1 before restarting other nodes.
For a universal rollback, append `-e artemis_universal_rollback=true` to the release command.
Older releases without a checksum manifest can be deployed with the collection's original
command after independently verifying the release:

```bash
ansible-playbook "$update" -e "artemis_version=$PREVIOUS_RELEASE"
```

After core reports healthy, verify the build agent appears in administration. Submit a
programming exercise and check its logs, feedback and result. For a pool with AI Workers,
verify a recent heartbeat, a free `hyperion-generation` slot and a completed generation
before reopening admission. See [AI Worker operations](/admin/aiworker).

## Compose test servers

The test-server Compose files use the core image for HTTP and Git. LocalCI stacks run
`artemis-app-buildagent` with its own workspace and generated SSH key. Only the agent
mounts the grading Docker socket. Set a unique Valkey password through `REDIS_PASSWORD`
on Redis stacks. Set `NGINX_PUBLIC_HOSTNAME` to the test server's public DNS name so
agent SSH clones go through nginx inside the application network. The test-server workflow copies the supported runner limits, proxy, image cleanup and Git settings
from Ansible's rendered environment into a worker-only file before switching images.
If you invoke the external helper yourself, run this on the test host after rendering the configuration:

```bash
deployment/docker/prepare-test-server-agent.sh "$ARTEMIS_ENV_FILE"
```

An explicitly supplied `ARTEMIS_BUILDAGENT_ENV_FILE` selects a separately maintained runner configuration. The agent starts with
a 512 MiB maximum heap and one concurrent build. On multi-node hosts, measure peak memory
for both core nodes, the agent and a representative build container before increasing concurrency.
In a two-core arm64 smoke run with 1 GiB core heaps and one C build slot, Docker reported
1.80 and 1.60 GiB for the core nodes and 491 MiB for the agent just after grading a submission.
These figures exclude the database, broker, Valkey, nginx and the build container. On arm64 hosts,
set `ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE=arm64` in the runner configuration.

Keep the deployed Compose revision, its environment files and the immutable image tag for
rollback. The Ansible-installed test-server helper checks out a branch's tip. When rolling back
through Helios across the role split, select a branch whose tip has the previous topology.
For an exact-checkout rollback on the host, start in the previous checkout, set `COMPOSE_PROJECT_NAME` to the existing project
name, `COMPOSE_FILE` to the previous topology, and use the saved environment with its
previous `ARTEMIS_DOCKER_TAG`. Pull the required images before replacing containers:

```bash
stack=(docker compose --env-file .env --env-file "$SAVED_DOCKER_ENV" -f "$COMPOSE_FILE")
"${stack[@]}" config --quiet
"${stack[@]}" pull
"${stack[@]}" up -d --no-build --remove-orphans
"${stack[@]}" exec nginx nginx -s reload
```

For a rollback across the role split, the previous Compose revision selects the universal
image and restores the combined profiles. Use the same database and application data
volumes, and follow the database restore procedure if the older release requires it.
