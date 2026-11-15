import { describe, expect, it } from 'vitest';

import { FeatureInteraction, FeatureKind, FeatureUsageEndpoint, FeatureUsageStatus, FeatureUsageTrendPoint, UserFeatureUsage } from './feature-usage.model';
import {
    buildFeatureTree,
    callsOf,
    dailySeries,
    expandableKeys,
    groupAdoptionByFeature,
    groupByResource,
    groupEndpointsByFeature,
    latest,
    pathOf,
    verbOf,
} from './feature-usage.util';

function endpoint(featureId: number, overrides: Partial<FeatureUsageEndpoint>): FeatureUsageEndpoint {
    return {
        featureId,
        featureKind: FeatureKind.REST,
        module: 'programming',
        identifier: `GET api/programming/endpoint-${featureId}`,
        interaction: FeatureInteraction.VIEW,
        resource: 'SomeResource',
        callCount: 0,
        errorCount: 0,
        durationSumMs: 0,
        durationMaxMs: 0,
        activeDays: 0,
        ...overrides,
    };
}

function feature(name: string, area: string, overrides: Partial<UserFeatureUsage> = {}): UserFeatureUsage {
    return {
        feature: name,
        area,
        status: FeatureUsageStatus.USED,
        noActions: false,
        actionCount: 0,
        viewCount: 0,
        automaticCount: 0,
        systemCount: 0,
        errorCount: 0,
        durationSumMs: 0,
        durationMaxMs: 0,
        activeDays: 0,
        actionDays: 0,
        endpointCount: 1,
        hasActionEndpoints: true,
        ...overrides,
    };
}

describe('feature usage utilities', () => {
    const commit = endpoint(1, { identifier: 'POST api/programming/commit', interaction: FeatureInteraction.ACTION, resource: 'RepositoryResource', callCount: 15, errorCount: 3 });
    const files = endpoint(2, { identifier: 'GET api/programming/files', resource: 'RepositoryResource', callCount: 40 });
    const clone = endpoint(3, { featureKind: FeatureKind.GIT, module: 'localvc', identifier: 'fetch/assignment', resource: undefined, callCount: 5 });
    const probe = endpoint(4, {
        module: 'hyperion',
        identifier: 'GET api/hyperion/variant-jobs',
        interaction: FeatureInteraction.AUTOMATIC,
        resource: 'VariantResource',
        callCount: 700,
    });
    const generation = endpoint(5, { module: 'hyperion', identifier: 'POST api/hyperion/generate', interaction: FeatureInteraction.ACTION, resource: 'VariantResource' });

    const editor = feature('PROGRAMMING_ONLINE_EDITOR', 'PROGRAMMING', { actionCount: 15, viewCount: 45, errorCount: 3, activeDays: 4, lastUsedDay: '2026-09-20' });
    const variants = feature('HYPERION_VARIANT_GENERATION', 'AI_AUTHORING', { status: FeatureUsageStatus.ONLY_AUTOMATIC, automaticCount: 700 });
    const science = feature('SCIENCE', 'COMPETENCIES', { status: FeatureUsageStatus.NOT_AVAILABLE, endpointCount: 0 });

    const endpointsByFeature = new Map([
        [editor.feature, [commit, files, clone]],
        [variants.feature, [probe, generation]],
    ]);

    it('should split a REST identifier into verb and path and leave other kinds whole', () => {
        expect(verbOf(commit)).toBe('POST');
        expect(pathOf(commit)).toBe('api/programming/commit');
        expect(verbOf(clone)).toBeUndefined();
        expect(pathOf(clone)).toBe('fetch/assignment');
    });

    it('should sum the calls of one interaction and keep automatic calls apart', () => {
        expect(callsOf([commit, files, probe], FeatureInteraction.ACTION)).toBe(15);
        expect(callsOf([commit, files, probe], FeatureInteraction.VIEW)).toBe(40);
        const buildAgentAddress = endpoint(9, { interaction: FeatureInteraction.SYSTEM, callCount: 2 });
        expect(callsOf([commit, files, probe, buildAgentAddress], FeatureInteraction.AUTOMATIC)).toBe(700);
        expect(callsOf([commit, files, probe, buildAgentAddress], FeatureInteraction.SYSTEM)).toBe(2);
    });

    it('should group endpoints by feature and leave out those that belong to no feature', () => {
        const grouped = groupEndpointsByFeature(
            [
                { ...commit, featureLabel: editor.feature },
                { ...probe, featureLabel: variants.feature },
                { ...files, featureLabel: 'configuration/old-label' },
                { ...clone, featureLabel: undefined },
            ],
            [editor, variants],
        );

        expect([...grouped.keys()]).toEqual([editor.feature, variants.feature]);
        expect(grouped.get(editor.feature)).toHaveLength(1);
    });

    it('should group adoption by the feature it switches on', () => {
        const grouped = groupAdoptionByFeature([
            { module: 'programming', key: 'online-editor', feature: editor.feature, count: 3, total: 10 },
            { module: 'programming', key: 'unmapped', count: 1, total: 10 },
        ]);

        expect([...grouped.keys()]).toEqual([editor.feature]);
    });

    it('should group the endpoints of a feature by module and controller, and git entries by their kind', () => {
        const groups = groupByResource([commit, files, clone]);

        expect(groups.map((group) => group.key)).toEqual(['localvc/GIT', 'programming/RepositoryResource']);
        expect(groups[1].endpoints).toHaveLength(2);
        expect(groups[0].resource).toBeUndefined();
    });

    it('should start with one collapsed row per area, in catalogue order, and count used of offered features', () => {
        const rows = buildFeatureTree([variants, editor, science], endpointsByFeature, new Map(), new Set());

        expect(rows.map((row) => row.key)).toEqual(['PROGRAMMING', 'COMPETENCIES', 'AI_AUTHORING']);
        expect(rows.every((row) => row.kind === 'area' && !row.expanded)).toBe(true);
        expect(rows[0].usedFeatures).toBe(1);
        expect(rows[0].availableFeatures).toBe(1);
        // a feature this deployment does not offer is not counted as offered
        expect(rows[1].availableFeatures).toBe(0);
    });

    it('should keep automatic calls out of the action and view columns of an area', () => {
        const [, , aiAuthoring] = buildFeatureTree([variants, editor, science], endpointsByFeature, new Map(), new Set());

        expect(aiAuthoring.actionCount).toBe(0);
        expect(aiAuthoring.viewCount).toBe(0);
        expect(aiAuthoring.automaticCount).toBe(700);
        // distinct days cannot be summed over features, so an area does not claim any
        expect(aiAuthoring.activeDays).toBeUndefined();
    });

    it('should show the features of an expanded area with their adoption', () => {
        const adoption = new Map([[editor.feature, [{ module: 'programming', key: 'online-editor', feature: editor.feature, count: 3, total: 10 }]]]);

        const rows = buildFeatureTree([editor], endpointsByFeature, adoption, new Set(['PROGRAMMING']));

        expect(rows.map((row) => row.key)).toEqual(['PROGRAMMING', 'PROGRAMMING/PROGRAMMING_ONLINE_EDITOR']);
        expect(rows[1].feature).toBe(editor);
        expect(rows[1].adoption).toHaveLength(1);
        expect(rows[1].activeDays).toBe(4);
        expect(rows[1].hasChildren).toBe(true);
    });

    it('should not offer to expand a feature without any endpoint', () => {
        const rows = buildFeatureTree([science], endpointsByFeature, new Map(), new Set(['COMPETENCIES']));

        expect(rows[1].hasChildren).toBe(false);
    });

    it('should break an expanded feature down into resources and those into endpoints, busiest use first', () => {
        const rows = buildFeatureTree(
            [editor],
            endpointsByFeature,
            new Map(),
            new Set(['PROGRAMMING', 'PROGRAMMING/PROGRAMMING_ONLINE_EDITOR', 'PROGRAMMING/PROGRAMMING_ONLINE_EDITOR/programming/RepositoryResource']),
        );

        const resources = rows.filter((row) => row.kind === 'resource');
        expect(resources.map((row) => row.name)).toEqual(['localvc', 'programming · RepositoryResource']);
        expect(resources[0].featureKind).toBe(FeatureKind.GIT);
        expect(resources[1].actionCount).toBe(15);
        expect(resources[1].viewCount).toBe(40);

        const endpoints = rows.filter((row) => row.kind === 'endpoint');
        expect(endpoints.map((row) => row.name)).toEqual(['api/programming/files', 'api/programming/commit']);
        expect(endpoints[1].actionCount).toBe(15);
        expect(endpoints[1].viewCount).toBe(0);
    });

    it('should put the calls of an automatic endpoint into the automatic column only', () => {
        const rows = buildFeatureTree(
            [variants],
            endpointsByFeature,
            new Map(),
            new Set(['AI_AUTHORING', 'AI_AUTHORING/HYPERION_VARIANT_GENERATION', 'AI_AUTHORING/HYPERION_VARIANT_GENERATION/hyperion/VariantResource']),
        );

        const probeRow = rows.find((row) => row.endpoint?.featureId === probe.featureId)!;
        expect(probeRow.automaticCount).toBe(700);
        expect(probeRow.viewCount).toBe(0);
        // the resource shows no use either, although it serves 700 calls
        const resource = rows.find((row) => row.kind === 'resource')!;
        expect(resource.actionCount + resource.viewCount).toBe(0);
        expect(resource.automaticCount).toBe(700);
    });

    it('should keep system calls apart from automatic calls on every level of the tree', () => {
        // The legend defines automatic calls as calls the page made on its own; a build agent is another system
        const buildAgentAddress = endpoint(9, {
            module: 'localvc',
            identifier: 'GET api/localvc/public/observed-client-address',
            interaction: FeatureInteraction.SYSTEM,
            resource: 'PublicBuildAgentAddressResource',
            callCount: 4,
        });
        const buildAgents = feature('BUILD_AGENTS', 'BUILD_SYSTEM', { status: FeatureUsageStatus.ONLY_AUTOMATIC, systemCount: 4 });

        const rows = buildFeatureTree(
            [buildAgents],
            new Map([[buildAgents.feature, [buildAgentAddress]]]),
            new Map(),
            new Set(['BUILD_SYSTEM', 'BUILD_SYSTEM/BUILD_AGENTS', 'BUILD_SYSTEM/BUILD_AGENTS/localvc/PublicBuildAgentAddressResource']),
        );

        expect(rows.map((row) => row.kind)).toEqual(['area', 'feature', 'resource', 'endpoint']);
        for (const row of rows) {
            expect(row.systemCount).toBe(4);
            expect(row.automaticCount).toBe(0);
            expect(row.actionCount + row.viewCount).toBe(0);
        }
    });

    it('should list every expandable key for expand all', () => {
        const keys = expandableKeys([editor, science], endpointsByFeature);

        expect([...keys].sort()).toEqual(
            [
                'COMPETENCIES',
                'PROGRAMMING',
                'PROGRAMMING/PROGRAMMING_ONLINE_EDITOR',
                'PROGRAMMING/PROGRAMMING_ONLINE_EDITOR/localvc/GIT',
                'PROGRAMMING/PROGRAMMING_ONLINE_EDITOR/programming/RepositoryResource',
            ].sort(),
        );
    });

    it('should fill every day of the window and sum only the requested interactions', () => {
        const points: FeatureUsageTrendPoint[] = [
            { usageDay: '2026-09-01', interaction: FeatureInteraction.ACTION, callCount: 2 },
            { usageDay: '2026-09-01', interaction: FeatureInteraction.AUTOMATIC, callCount: 300 },
            { usageDay: '2026-09-03', interaction: FeatureInteraction.SYSTEM, callCount: 5 },
        ];

        expect(dailySeries(points, '2026-09-01', 3, [FeatureInteraction.ACTION])).toEqual([
            { name: '2026-09-01', value: 2 },
            { name: '2026-09-02', value: 0 },
            { name: '2026-09-03', value: 0 },
        ]);
        expect(dailySeries(points, '2026-09-01', 3, [FeatureInteraction.AUTOMATIC, FeatureInteraction.SYSTEM]).map((point) => point.value)).toEqual([300, 0, 5]);
    });

    it('should find the latest day and ignore missing ones', () => {
        expect(latest(['2026-09-01', undefined, '2026-09-10', '2026-09-03'])).toBe('2026-09-10');
        expect(latest([undefined])).toBeUndefined();
    });
});
