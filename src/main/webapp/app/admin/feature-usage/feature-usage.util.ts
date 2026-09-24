import {
    FeatureAdoption,
    FeatureInteraction,
    FeatureKind,
    FeatureResourceGroup,
    FeatureTreeRow,
    FeatureUsageEndpoint,
    FeatureUsageStatus,
    FeatureUsageTrendPoint,
    PRODUCT_AREAS,
    UserFeatureUsage,
} from './feature-usage.model';

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

/** The translation key of a catalogue feature's name or description. */
export function featureTranslationKey(feature: string, field: 'name' | 'description'): string {
    return `artemisApp.featureUsage.catalog.feature.${feature}.${field}`;
}

/** The translation key of a product area's name. */
export function areaTranslationKey(area: string): string {
    return `artemisApp.featureUsage.catalog.area.${area}`;
}

/** Whether calls of this interaction are use of a feature, as opposed to automatic calls by the client or other systems. */
export function countsAsUse(interaction: FeatureInteraction): boolean {
    return interaction === FeatureInteraction.ACTION || interaction === FeatureInteraction.VIEW;
}

/** The HTTP verb of a REST identifier (`GET api/...`), or undefined for git and background entries. */
export function verbOf(endpoint: FeatureUsageEndpoint): string | undefined {
    return endpoint.featureKind === FeatureKind.REST ? endpoint.identifier.slice(0, endpoint.identifier.indexOf(' ')) : undefined;
}

/** The path of a REST identifier, or the whole identifier of a git or background entry. */
export function pathOf(endpoint: FeatureUsageEndpoint): string {
    return endpoint.featureKind === FeatureKind.REST ? endpoint.identifier.slice(endpoint.identifier.indexOf(' ') + 1) : endpoint.identifier;
}

/** The calls of the given endpoints that count as the given interaction. */
export function callsOf(endpoints: FeatureUsageEndpoint[], interaction: FeatureInteraction): number {
    return endpoints.filter((endpoint) => endpoint.interaction === interaction).reduce((sum, endpoint) => sum + (endpoint.callCount ?? 0), 0);
}

/** Automatic and system calls together, which is the traffic that does not count as use. */
export function automaticCallsOf(endpoints: FeatureUsageEndpoint[]): number {
    return callsOf(endpoints, FeatureInteraction.AUTOMATIC) + callsOf(endpoints, FeatureInteraction.SYSTEM);
}

/**
 * Groups the endpoints by the catalogue feature they serve. Endpoints whose label resolves to no feature, which only
 * happens for rows this version no longer offers, are left out: they belong to no feature and are only listed on the
 * endpoints tab.
 */
export function groupEndpointsByFeature(endpoints: FeatureUsageEndpoint[], features: UserFeatureUsage[]): Map<string, FeatureUsageEndpoint[]> {
    const known = new Set(features.map((feature) => feature.feature));
    const grouped = new Map<string, FeatureUsageEndpoint[]>();
    for (const endpoint of endpoints) {
        if (endpoint.featureLabel && known.has(endpoint.featureLabel)) {
            grouped.set(endpoint.featureLabel, [...(grouped.get(endpoint.featureLabel) ?? []), endpoint]);
        }
    }
    return grouped;
}

/** Groups adoption entries by the feature they switch on. */
export function groupAdoptionByFeature(adoption: FeatureAdoption[]): Map<string, FeatureAdoption[]> {
    const grouped = new Map<string, FeatureAdoption[]>();
    for (const entry of adoption) {
        if (entry.feature) {
            grouped.set(entry.feature, [...(grouped.get(entry.feature) ?? []), entry]);
        }
    }
    return grouped;
}

/**
 * Groups the endpoints of one feature by module and serving controller, which is how a feature maps to the code that
 * implements it. Git and background entries have no controller and are grouped by their kind instead.
 */
export function groupByResource(endpoints: FeatureUsageEndpoint[]): FeatureResourceGroup[] {
    const groups = new Map<string, FeatureResourceGroup>();
    for (const endpoint of endpoints) {
        const key = `${endpoint.module}/${endpoint.resource ?? endpoint.featureKind}`;
        const group = groups.get(key) ?? { key, module: endpoint.module, resource: endpoint.resource, featureKind: endpoint.featureKind, endpoints: [] };
        group.endpoints.push(endpoint);
        groups.set(key, group);
    }
    return [...groups.values()].sort((first, second) => first.key.localeCompare(second.key));
}

/** Busiest first by use, then by automatic traffic, then by identifier so the order is stable. */
function compareEndpoints(first: FeatureUsageEndpoint, second: FeatureUsageEndpoint): number {
    const useOf = (endpoint: FeatureUsageEndpoint) => (countsAsUse(endpoint.interaction) ? (endpoint.callCount ?? 0) : 0);
    return useOf(second) - useOf(first) || (second.callCount ?? 0) - (first.callCount ?? 0) || first.identifier.localeCompare(second.identifier);
}

/**
 * Flattens area, feature, resource and endpoint into the rows that are currently visible, so the template can render the
 * tree as a plain table instead of a recursive component.
 *
 * Areas and features keep the catalogue order, which groups related features the way users think of them; sorting by
 * calls would scatter an area's features across the table. Resources and endpoints are sorted by use.
 *
 * @param features         the features to show, already filtered
 * @param endpointsByFeature the endpoints behind each feature
 * @param adoptionByFeature  the adoption entries of each feature
 * @param expandedKeys     the keys of the rows whose children are shown
 */
export function buildFeatureTree(
    features: UserFeatureUsage[],
    endpointsByFeature: Map<string, FeatureUsageEndpoint[]>,
    adoptionByFeature: Map<string, FeatureAdoption[]>,
    expandedKeys: ReadonlySet<string>,
): FeatureTreeRow[] {
    const rows: FeatureTreeRow[] = [];
    for (const area of PRODUCT_AREAS) {
        const areaFeatures = features.filter((feature) => feature.area === area);
        if (!areaFeatures.length) {
            continue;
        }
        const areaKey = area;
        const areaExpanded = expandedKeys.has(areaKey);
        const offered = areaFeatures.filter((feature) => feature.status !== FeatureUsageStatus.NOT_AVAILABLE);
        rows.push({
            key: areaKey,
            level: 0,
            kind: 'area',
            name: area,
            hasChildren: true,
            expanded: areaExpanded,
            actionCount: sum(areaFeatures, (feature) => feature.actionCount),
            viewCount: sum(areaFeatures, (feature) => feature.viewCount),
            automaticCount: sum(areaFeatures, (feature) => feature.automaticCount + feature.systemCount),
            errorCount: sum(areaFeatures, (feature) => feature.errorCount),
            durationSumMs: sum(areaFeatures, (feature) => feature.durationSumMs),
            lastUsedDay: latest(areaFeatures.map((feature) => feature.lastUsedDay)),
            usedFeatures: offered.filter((feature) => feature.status === FeatureUsageStatus.USED).length,
            availableFeatures: offered.length,
        });
        if (!areaExpanded) {
            continue;
        }
        for (const feature of areaFeatures) {
            const endpoints = endpointsByFeature.get(feature.feature) ?? [];
            const featureKey = `${areaKey}/${feature.feature}`;
            const featureExpanded = expandedKeys.has(featureKey);
            rows.push({
                key: featureKey,
                level: 1,
                kind: 'feature',
                name: feature.feature,
                hasChildren: endpoints.length > 0,
                expanded: featureExpanded,
                actionCount: feature.actionCount,
                viewCount: feature.viewCount,
                automaticCount: feature.automaticCount + feature.systemCount,
                errorCount: feature.errorCount,
                durationSumMs: feature.durationSumMs,
                activeDays: feature.activeDays,
                lastUsedDay: feature.lastUsedDay,
                feature,
                adoption: adoptionByFeature.get(feature.feature),
            });
            if (!featureExpanded) {
                continue;
            }
            for (const group of groupByResource(endpoints)) {
                const groupKey = `${featureKey}/${group.key}`;
                const groupExpanded = expandedKeys.has(groupKey);
                const use = group.endpoints.filter((endpoint) => countsAsUse(endpoint.interaction));
                rows.push({
                    key: groupKey,
                    level: 2,
                    kind: 'resource',
                    name: group.resource ? `${group.module} · ${group.resource}` : group.module,
                    hasChildren: true,
                    expanded: groupExpanded,
                    actionCount: callsOf(group.endpoints, FeatureInteraction.ACTION),
                    viewCount: callsOf(group.endpoints, FeatureInteraction.VIEW),
                    automaticCount: automaticCallsOf(group.endpoints),
                    errorCount: sum(use, (endpoint) => endpoint.errorCount ?? 0),
                    durationSumMs: sum(use, (endpoint) => endpoint.durationSumMs ?? 0),
                    lastUsedDay: latest(use.map((endpoint) => endpoint.lastUsedDay)),
                    featureKind: group.featureKind,
                });
                if (!groupExpanded) {
                    continue;
                }
                for (const endpoint of [...group.endpoints].sort(compareEndpoints)) {
                    rows.push(endpointRow(`${groupKey}/${endpoint.featureId}`, endpoint));
                }
            }
        }
    }
    return rows;
}

function endpointRow(key: string, endpoint: FeatureUsageEndpoint): FeatureTreeRow {
    const calls = endpoint.callCount ?? 0;
    const use = countsAsUse(endpoint.interaction);
    return {
        key,
        level: 3,
        kind: 'endpoint',
        name: pathOf(endpoint),
        hasChildren: false,
        expanded: false,
        actionCount: endpoint.interaction === FeatureInteraction.ACTION ? calls : 0,
        viewCount: endpoint.interaction === FeatureInteraction.VIEW ? calls : 0,
        automaticCount: use ? 0 : calls,
        // an automatic endpoint's own errors are still worth seeing, on its own row, where they cannot inflate the feature
        errorCount: endpoint.errorCount ?? 0,
        durationSumMs: endpoint.durationSumMs ?? 0,
        activeDays: endpoint.activeDays ?? 0,
        lastUsedDay: endpoint.lastUsedDay,
        endpoint,
    };
}

/** Every key the tree can expand, for "expand all". */
export function expandableKeys(features: UserFeatureUsage[], endpointsByFeature: Map<string, FeatureUsageEndpoint[]>): Set<string> {
    const keys = new Set<string>();
    for (const feature of features) {
        keys.add(feature.area);
        const endpoints = endpointsByFeature.get(feature.feature) ?? [];
        if (endpoints.length) {
            const featureKey = `${feature.area}/${feature.feature}`;
            keys.add(featureKey);
            groupByResource(endpoints).forEach((group) => keys.add(`${featureKey}/${group.key}`));
        }
    }
    return keys;
}

/**
 * Expands the trend into one point per day of the window and series, filling the days the server left out with zero.
 *
 * The query returns only the days that saw calls, and the chart's axis is categorical: it draws the points it is given,
 * evenly spaced, whatever their dates. A feature used on the first and last day of a week would otherwise render as two
 * adjacent points, which reads as steady use across the week instead of two isolated bursts with five silent days
 * between them.
 *
 * The window is materialised from the overview's `from`, which the server derived from its own clock, rather than from the
 * browser's. A viewer whose clock is off by a day would otherwise generate day keys that match none of the server's
 * buckets, and the chart would show a flat zero line for a feature that is in fact used.
 *
 * @param points       the trend as the server returned it
 * @param from         the first day of the window, as `YYYY-MM-DD`
 * @param days         the length of the window
 * @param interactions the interactions to sum into this series
 */
export function dailySeries(points: FeatureUsageTrendPoint[], from: string, days: number, interactions: FeatureInteraction[]): { name: string; value: number }[] {
    const callsByDay = new Map<string, number>();
    for (const point of points) {
        if (interactions.includes(point.interaction)) {
            callsByDay.set(point.usageDay, (callsByDay.get(point.usageDay) ?? 0) + point.callCount);
        }
    }
    const firstDay = Date.parse(`${from}T00:00:00Z`);
    const series: { name: string; value: number }[] = [];
    for (let offset = 0; offset < days; offset++) {
        const day = new Date(firstDay + offset * MILLISECONDS_PER_DAY).toISOString().slice(0, 10);
        series.push({ name: day, value: callsByDay.get(day) ?? 0 });
    }
    return series;
}

/** The latest of several `YYYY-MM-DD` days, or undefined if there is none. */
export function latest(days: (string | undefined)[]): string | undefined {
    return days.reduce<string | undefined>((latestDay, day) => (!day ? latestDay : !latestDay || day > latestDay ? day : latestDay), undefined);
}

function sum<T>(items: T[], value: (item: T) => number): number {
    return items.reduce((total, item) => total + (value(item) ?? 0), 0);
}
