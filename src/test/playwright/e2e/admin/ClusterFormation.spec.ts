import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Commands } from '../../support/commands';
import { expect } from '@playwright/test';

/**
 * Smoke test for the multi-node cluster. Verifies that all expected core nodes have registered and that at least one
 * build agent is known. Recent multi-node bugs (issue #12574, fixed in #12578/#12579) were not surfaced by any existing
 * test because no test asserts on cluster membership directly. This test fails fast in the multi-node E2E pipeline if
 * cluster formation regresses.
 *
 * Backend-agnostic on purpose: Artemis reaches its cross-node state through the DistributedDataProvider abstraction and
 * supports both Hazelcast and Redis, so the same assertions have to hold on either. The two report node identity in
 * different namespaces — Hazelcast uses the member's `[host]:port`, the Redis provider uses the configured client name,
 * which has no port — so the shape assertions that only make sense for one of them are guarded by
 * DISTRIBUTED_DATA_PROVIDER, which the multi-node runners export.
 *
 * Note: the multi-node stack is asymmetric — only nodes with the `core` Spring profile register as cluster nodes.
 * Buildagent-only nodes are visible via /api/admin/build-agents instead.
 *
 * Tagged @multi-node so the single-node fast pipeline skips it; only the multi-node runners
 * (run-e2e-tests-local-multinode.sh / run-e2e-tests-local-multinode-fast.sh / their CI counterpart) execute this file.
 */

const EXPECTED_NODE_COUNT = parseInt(process.env.EXPECTED_CLUSTER_NODE_COUNT ?? '2', 10);
const EXPECTED_MIN_BUILD_AGENTS = parseInt(process.env.EXPECTED_MIN_BUILD_AGENTS ?? '1', 10);
const CLUSTER_FORMATION_TIMEOUT_MS = 60_000;
const USES_HAZELCAST = (process.env.DISTRIBUTED_DATA_PROVIDER ?? 'hazelcast').toLowerCase() === 'hazelcast';

interface WebsocketNodeDTO {
    memberId: string;
    address: string;
    host: string;
    port: number;
    local: boolean;
    instanceId?: string;
    brokerConnected: boolean;
}

interface BuildAgentDTO {
    name: string;
    memberAddress: string;
    displayName: string;
}

interface BuildAgentInformation {
    buildAgent: BuildAgentDTO;
    maxNumberOfConcurrentBuildJobs: number;
    numberOfCurrentBuildJobs: number;
}

test.describe('Cluster formation', { tag: '@multi-node' }, () => {
    test.beforeEach('Login as admin', async ({ page }) => {
        await Commands.login(page, admin);
    });

    test(`Cluster has ${EXPECTED_NODE_COUNT} connected core nodes`, async ({ page }) => {
        await expect
            .poll(
                async () => {
                    const response = await page.request.get('/api/admin/websocket/nodes');
                    if (!response.ok()) {
                        return -1;
                    }
                    const nodes = (await response.json()) as WebsocketNodeDTO[];
                    return nodes.length;
                },
                {
                    timeout: CLUSTER_FORMATION_TIMEOUT_MS,
                    intervals: [1_000, 2_000, 5_000],
                    message: `Cluster did not reach ${EXPECTED_NODE_COUNT} nodes within ${CLUSTER_FORMATION_TIMEOUT_MS}ms`,
                },
            )
            .toBe(EXPECTED_NODE_COUNT);

        const response = await page.request.get('/api/admin/websocket/nodes');
        const nodes = (await response.json()) as WebsocketNodeDTO[];

        // Every node must report an identity and an address so we know the endpoint actually serialised real registry
        // data and not an empty stub.
        for (const node of nodes) {
            expect(node.memberId, 'node id should be set').toBeTruthy();
            expect(node.address, 'node address should be set').toBeTruthy();
            expect(node.host, 'node host should be set').toBeTruthy();
            if (USES_HAZELCAST) {
                // Hazelcast identifies a member by its endpoint, which the registry publishes as `[host]:port`.
                expect(node.address, 'node address should be host:port on Hazelcast').toMatch(/.+:\d+$/);
                expect(node.port, 'node port should be positive on Hazelcast').toBeGreaterThan(0);
            }
        }

        // Exactly one node should report itself as local — the node that served this HTTP request
        // through the load balancer. If zero or more than one were local, node identity is broken.
        const localNodes = nodes.filter((n) => n.local);
        expect(localNodes, 'exactly one node should report itself as local').toHaveLength(1);

        // Node ids must be unique. Duplicates would indicate stale entries that survived a restart.
        const memberIds = new Set(nodes.map((n) => n.memberId));
        expect(memberIds.size, 'node ids should be unique across the cluster').toBe(nodes.length);

        // The websocket broker connectivity flag is the canonical signal that STOMP relay is up on
        // a node. At least one node must report it true; if none do, real-time updates are broken.
        const brokerConnectedCount = nodes.filter((n) => n.brokerConnected).length;
        expect(brokerConnectedCount, 'at least one node should be connected to the websocket broker').toBeGreaterThanOrEqual(1);
    });

    test(`At least ${EXPECTED_MIN_BUILD_AGENTS} build agent is registered`, async ({ page }) => {
        await expect
            .poll(
                async () => {
                    const response = await page.request.get('/api/admin/build-agents');
                    if (!response.ok()) {
                        return -1;
                    }
                    const agents = (await response.json()) as BuildAgentInformation[];
                    return agents.length;
                },
                {
                    timeout: CLUSTER_FORMATION_TIMEOUT_MS,
                    intervals: [1_000, 2_000, 5_000],
                    message: `Fewer than ${EXPECTED_MIN_BUILD_AGENTS} build agents registered within ${CLUSTER_FORMATION_TIMEOUT_MS}ms`,
                },
            )
            .toBeGreaterThanOrEqual(EXPECTED_MIN_BUILD_AGENTS);

        const response = await page.request.get('/api/admin/build-agents');
        const agents = (await response.json()) as BuildAgentInformation[];

        for (const agent of agents) {
            expect(agent.buildAgent.name, 'build agent name should be set').toBeTruthy();
            expect(agent.buildAgent.memberAddress, 'build agent member address should be set').toBeTruthy();
            if (USES_HAZELCAST) {
                // Hazelcast serialises a member address as `[host]:port` or `host:port`; the Redis provider reports the
                // node's configured client name, which has no port at all.
                expect(agent.buildAgent.memberAddress, 'build agent member address should look like a Hazelcast endpoint').toMatch(/^\[?[^\]]+\]?:\d+$/);
            }
            expect(agent.maxNumberOfConcurrentBuildJobs, 'build agent should advertise capacity').toBeGreaterThan(0);
        }

        // Build agent names must be unique across the cluster — duplicate registrations indicate a
        // node failed to deregister cleanly during a previous shutdown.
        const agentNames = new Set(agents.map((a) => a.buildAgent.name));
        expect(agentNames.size, 'build agent names should be unique across the cluster').toBe(agents.length);
    });
});
