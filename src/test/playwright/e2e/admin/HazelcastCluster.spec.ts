import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Commands } from '../../support/commands';
import { expect } from '@playwright/test';

/**
 * Smoke test for the multi-node Hazelcast cluster. Verifies that all expected nodes have joined the
 * cluster and at least one build agent is registered. Recent multi-node bugs (issue #12574, fixed in
 * #12578/#12579) were not surfaced by any existing test because no test asserts on cluster
 * membership directly. This test fails fast in the multi-node E2E pipeline if cluster formation
 * regresses.
 *
 * Tagged @multi-node so the single-node fast pipeline skips it; only the multi-node runner
 * (run-e2e-tests-local-multinode.sh / its CI counterpart) executes this file.
 */

const EXPECTED_NODE_COUNT = parseInt(process.env.EXPECTED_CLUSTER_NODE_COUNT ?? '3', 10);
const EXPECTED_MIN_BUILD_AGENTS = parseInt(process.env.EXPECTED_MIN_BUILD_AGENTS ?? '1', 10);
const CLUSTER_FORMATION_TIMEOUT_MS = 60_000;

interface WebsocketNodeDTO {
    memberId: string;
    address: string;
    host: string;
    port: number;
    local: boolean;
    liteMember: boolean;
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

test.describe('Hazelcast cluster formation', { tag: '@multi-node' }, () => {
    test.beforeEach('Login as admin', async ({ page }) => {
        await Commands.login(page, admin);
    });

    test(`Hazelcast cluster has ${EXPECTED_NODE_COUNT} connected nodes`, async ({ page }) => {
        await expect
            .poll(
                async () => {
                    const response = await page.request.get('/api/core/admin/websocket/nodes');
                    if (!response.ok()) {
                        return -1;
                    }
                    const nodes = (await response.json()) as WebsocketNodeDTO[];
                    return nodes.length;
                },
                {
                    timeout: CLUSTER_FORMATION_TIMEOUT_MS,
                    intervals: [1_000, 2_000, 5_000],
                    message: `Cluster did not reach ${EXPECTED_NODE_COUNT} members within ${CLUSTER_FORMATION_TIMEOUT_MS}ms`,
                },
            )
            .toBe(EXPECTED_NODE_COUNT);

        const response = await page.request.get('/api/core/admin/websocket/nodes');
        const nodes = (await response.json()) as WebsocketNodeDTO[];

        // Every member must report a UUID and a host:port address so we know the endpoint actually
        // serialised real cluster data and not an empty stub.
        for (const node of nodes) {
            expect(node.memberId, `member id for ${node.address} should be a UUID`).toMatch(/^[0-9a-f-]{36}$/i);
            expect(node.address, 'node address should be host:port').toMatch(/.+:\d+$/);
            expect(node.port, 'node port should be positive').toBeGreaterThan(0);
        }

        // Exactly one node should report itself as local — the node that served this HTTP request
        // through the load balancer. If zero or more than one were local, member identity is broken.
        const localNodes = nodes.filter((n) => n.local);
        expect(localNodes, 'exactly one node should report itself as local').toHaveLength(1);

        // Member ids must be unique. Duplicates would indicate stale entries that survived a restart.
        const memberIds = new Set(nodes.map((n) => n.memberId));
        expect(memberIds.size, 'member ids should be unique across the cluster').toBe(nodes.length);

        // The websocket broker connectivity flag is the canonical signal that STOMP relay is up on
        // a node. At least one node must report it true; if none do, real-time updates are broken.
        const brokerConnectedCount = nodes.filter((n) => n.brokerConnected).length;
        expect(brokerConnectedCount, 'at least one node should be connected to the websocket broker').toBeGreaterThanOrEqual(1);
    });

    test(`At least ${EXPECTED_MIN_BUILD_AGENTS} build agent is registered`, async ({ page }) => {
        await expect
            .poll(
                async () => {
                    const response = await page.request.get('/api/core/admin/build-agents');
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

        const response = await page.request.get('/api/core/admin/build-agents');
        const agents = (await response.json()) as BuildAgentInformation[];

        for (const agent of agents) {
            expect(agent.buildAgent.name, 'build agent name should be set').toBeTruthy();
            // Hazelcast member addresses are usually serialised as `[host]:port` or `host:port`.
            expect(agent.buildAgent.memberAddress, 'build agent member address should look like a Hazelcast endpoint').toMatch(/^\[?[^\]]+\]?:\d+$/);
            expect(agent.maxNumberOfConcurrentBuildJobs, 'build agent should advertise capacity').toBeGreaterThan(0);
        }

        // Build agent names must be unique across the cluster — duplicate registrations indicate a
        // node failed to deregister cleanly during a previous shutdown.
        const agentNames = new Set(agents.map((a) => a.buildAgent.name));
        expect(agentNames.size, 'build agent names should be unique across the cluster').toBe(agents.length);
    });
});
