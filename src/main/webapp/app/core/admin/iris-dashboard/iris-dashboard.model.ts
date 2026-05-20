export type IrisDashboardMetric = 'SESSIONS' | 'MESSAGES' | 'NO_RESPONSE_RATE' | 'RESPONSE_TIME' | 'RATINGS' | 'TOKEN_COST' | 'ENGAGEMENT';

export type IrisDashboardSpan = 'DAY' | 'WEEK' | 'MONTH';

export type IrisDashboardBreakdownDimension = 'CHAT_MODE' | 'COURSE' | 'MODEL';

export type IrisDashboardRange = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';

export type IrisDashboardSessionType = 'PROGRAMMING_EXERCISE_CHAT' | 'TEXT_EXERCISE_CHAT' | 'COURSE_CHAT' | 'LECTURE_CHAT' | 'TUTOR_SUGGESTION';

export interface IrisDashboardOverview {
    totalSessions: number;
    activeSessions: number;
    engagementRate: number;
    totalMessages: number;
    uniqueUsers: number;
    userMessageCount: number;
    eligibleSessions: number;
    noResponseRate: number;
    noResponseMessageCount: number;
    noResponseSessionCount: number;
    thumbsUpRatio: number;
    thumbsDownRatio: number;
    thumbsUpAbsoluteRate: number;
    thumbsDownAbsoluteRate: number;
    sessionsWithThumbsUp: number;
    sessionsWithThumbsDown: number;
    averageResponseTimeSeconds: number;
    p50ResponseTimeSeconds: number;
    p95ResponseTimeSeconds: number;
    totalTokenCostEur: number;
}

export interface IrisDashboardTimeSeriesEntry {
    bucketStart: string;
    series: Record<string, number>;
}

export interface IrisDashboardTimeSeries {
    metric: IrisDashboardMetric;
    entries: IrisDashboardTimeSeriesEntry[];
}

export interface IrisDashboardBreakdownEntry {
    name: string;
    metrics: Record<string, number>;
}

export interface IrisDashboardConfig {
    maxQueryWindowDays: number;
    staleThresholdMinutes: number;
    digest: {
        enabled: boolean;
        cron: string;
        recipients: string[];
    };
    alert: {
        enabled: boolean;
        noResponseRateThreshold: number;
        checkIntervalMinutes: number;
        cooldownMinutes: number;
        lookbackMinutes: number;
        minimumActiveSessions: number;
        minimumUserMessages: number;
        recipients: string[];
    };
}

export interface IrisDashboardQuery {
    from: Date;
    to: Date;
    chatMode?: IrisDashboardSessionType;
}

export interface ChartSeriesPoint {
    name: string;
    value: number;
}

export interface ChartDataEntry {
    name: string;
    series: ChartSeriesPoint[];
}
