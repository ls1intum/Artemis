export interface IrisDashboardOverview {
    totalSessions: number;
    activeSessions: number;
    engagementRate: number;
    totalMessages: number;
    uniqueUsers: number;
    noResponseRate: number;
    noResponseMessageCount: number;
    noResponseSessionCount: number;
    thumbsUpRatio: number;
    thumbsDownRatio: number;
    thumbsUpAbsoluteRate: number;
    thumbsDownAbsoluteRate: number;
    sessionsWithThumbsUp: number;
    sessionsWithThumbsDown: number;
    thumbsUpCount: number;
    thumbsDownCount: number;
    avgResponseTimeSeconds: number;
    p50ResponseTimeSeconds: number;
    p95ResponseTimeSeconds: number;
    totalTokenCostEur: number;
}

export interface IrisDashboardTimeSeriesEntry {
    bucketStart: string;
    series: Record<string, number>;
}

export interface IrisDashboardTimeSeries {
    metric: string;
    entries: IrisDashboardTimeSeriesEntry[];
}

export interface IrisDashboardBreakdownEntry {
    label: string;
    values: Record<string, number>;
}

export interface IrisDashboardConfig {
    maxQueryWindowDays: number;
    staleThresholdMinutes: number;
    digestEnabled: boolean;
    digestCron: string;
    alertEnabled: boolean;
    alertNoResponseRateThreshold: number;
    alertCheckIntervalMinutes: number;
    alertCooldownMinutes: number;
    alertLookbackMinutes: number;
    alertMinimumEligibleSessions: number;
    alertMinimumUserMessages: number;
}

export type IrisDashboardTimeSpan = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';

export type IrisDashboardMetric = 'SESSIONS' | 'MESSAGES' | 'NO_RESPONSE_RATE' | 'RESPONSE_TIME' | 'RATINGS' | 'TOKEN_COST' | 'ENGAGEMENT';

export type IrisDashboardBreakdownDimension = 'CHAT_MODE' | 'COURSE' | 'MODEL';
