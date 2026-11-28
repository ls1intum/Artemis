import { cloneWith } from 'app/foundation/util/deep-clone.util';
import type { FeatureUsageEntryFeatureKindEnum, FeatureUsageEntryInteractionEnum } from 'app/openapi/model/feature-usage-entry';
import type { FeatureUsageOverview as FeatureUsageOverviewDto } from 'app/openapi/model/feature-usage-overview';
import type { FeatureUsageTrendPoint as FeatureUsageTrendPointDto, FeatureUsageTrendPointInteractionEnum } from 'app/openapi/model/feature-usage-trend-point';
import type { UserFeatureUsageStatusEnum } from 'app/openapi/model/user-feature-usage';
import { FeatureInteraction, FeatureKind, FeatureUsageOverview, FeatureUsageStatus, FeatureUsageTrendPoint } from './feature-usage.model';

const featureKinds = {
    REST: FeatureKind.REST,
    GIT: FeatureKind.GIT,
    BACKGROUND: FeatureKind.BACKGROUND,
} satisfies Record<FeatureUsageEntryFeatureKindEnum, FeatureKind>;

const interactions = {
    ACTION: FeatureInteraction.ACTION,
    VIEW: FeatureInteraction.VIEW,
    AUTOMATIC: FeatureInteraction.AUTOMATIC,
    SYSTEM: FeatureInteraction.SYSTEM,
} satisfies Record<FeatureUsageEntryInteractionEnum | FeatureUsageTrendPointInteractionEnum, FeatureInteraction>;

const statuses = {
    USED: FeatureUsageStatus.USED,
    ONLY_AUTOMATIC: FeatureUsageStatus.ONLY_AUTOMATIC,
    UNUSED: FeatureUsageStatus.UNUSED,
    NOT_AVAILABLE: FeatureUsageStatus.NOT_AVAILABLE,
} satisfies Record<UserFeatureUsageStatusEnum, FeatureUsageStatus>;

export function mapFeatureUsageOverview(dto: FeatureUsageOverviewDto): FeatureUsageOverview {
    const { features, endpoints, ...summary } = dto;
    const overview: FeatureUsageOverview = summary;
    if (features) {
        overview.features = features.map(({ status, ...feature }) => cloneWith(feature, { status: statuses[status] }));
    }
    if (endpoints) {
        overview.endpoints = endpoints.map(({ featureKind, interaction, ...endpoint }) =>
            cloneWith(endpoint, { featureKind: featureKinds[featureKind], interaction: interactions[interaction] }),
        );
    }
    return overview;
}

export function mapFeatureUsageTrendPoint({ usageDay, interaction, callCount }: FeatureUsageTrendPointDto): FeatureUsageTrendPoint {
    return { usageDay, interaction: interactions[interaction], callCount };
}
