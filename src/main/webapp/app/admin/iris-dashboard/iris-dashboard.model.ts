import type { GetBreakdownParams, GetOverviewParams } from 'app/openapi/api/iris-admin-dashboard-api';

/** UI windows expressed as fixed elapsed days; the server also accepts YEAR buckets. */
export type IrisDashboardTimeSpan = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER';

export type IrisDashboardBreakdownDimension = GetBreakdownParams['dimension'];

/** Restricts the dashboard to users by their current AI selection. ALL applies no filter. */
export type IrisDashboardAiSelection = NonNullable<GetOverviewParams['aiSelection']>;
