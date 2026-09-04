import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { faChartLine, faChevronDown, faChevronRight, faEnvelope, faSearch, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ChartModule } from 'primeng/chart';

import {
    TumUiButtonDirective,
    TumUiCardComponent,
    TumUiIconFieldComponent,
    TumUiInputDirective,
    TumUiMessageComponent,
    TumUiSelectButtonComponent,
    TumUiSelectComponent,
    TumUiTabComponent,
    TumUiTabListComponent,
    TumUiTableDirective,
    TumUiTableSortEvent,
    TumUiTableSortableColumnComponent,
    TumUiTabsComponent,
    TumUiTagComponent,
} from '@tumaet/ui-angular';

import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { AlertService } from 'app/foundation/service/alert.service';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { lineChartOptions } from 'app/shared-ui/chart/chart-options';
import { multiSeriesToLineData } from 'app/shared-ui/chart/chart-adapters';

import { FeatureUsageService } from './feature-usage.service';
import {
    FEATURE_USAGE_CALLER_ROLES,
    FEATURE_USAGE_WINDOWS_IN_DAYS,
    FeatureAdoption,
    FeatureKind,
    FeatureTreeNode,
    FeatureTreeRow,
    FeatureUsageEntry,
    FeatureUsageOverview,
    FeatureUsageRow,
    FeatureUsageTrendPoint,
    UNCATALOGUED_AREA,
} from './feature-usage.model';

type SortField = 'name' | 'module' | 'callCount' | 'activeDays' | 'errorRate' | 'meanDurationMs' | 'maxDurationMs' | 'lastUsedDay';

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

const ALL_MODULES = '';

const ALL_ROLES = '';

/** Tab order: explore the tree first, then the flat views. */
const TAB_TREE = 0;

const TAB_UNUSED = 2;

const TAB_ADOPTION = 3;

/**
 * Admin page for the built-in feature usage analysis.
 *
 * The headline number is how many features saw no usage at all: ranking the popular ones is easy, but deciding what to
 * retire needs the other end of the list, and nothing in Artemis could answer that before.
 */
@Component({
    selector: 'jhi-feature-usage',
    templateUrl: './feature-usage.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        DecimalPipe,
        FaIconComponent,
        ChartModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TumUiButtonDirective,
        TumUiCardComponent,
        TumUiIconFieldComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
        TumUiSelectButtonComponent,
        TumUiSelectComponent,
        TumUiTableDirective,
        TumUiTableSortableColumnComponent,
        TumUiTabsComponent,
        TumUiTabListComponent,
        TumUiTabComponent,
        TumUiTagComponent,
    ],
})
export class FeatureUsageComponent implements OnInit {
    private readonly featureUsageService = inject(FeatureUsageService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly trendColors = inject(ChartColorService).resolvedColors(() => [GraphColors.DARK_BLUE]);

    protected readonly faSearch = faSearch;
    protected readonly faSpinner = faSpinner;
    protected readonly faChartLine = faChartLine;
    protected readonly faEnvelope = faEnvelope;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronDown = faChevronDown;
    protected readonly FeatureKind = FeatureKind;
    protected readonly TAB_TREE = TAB_TREE;
    protected readonly TAB_UNUSED = TAB_UNUSED;
    protected readonly TAB_ADOPTION = TAB_ADOPTION;

    protected readonly windowOptions = FEATURE_USAGE_WINDOWS_IN_DAYS.map((days) => ({ label: `artemisApp.featureUsage.window.days${days}`, value: days }));

    readonly loading = signal<boolean>(false);
    readonly sendingDigest = signal<boolean>(false);
    readonly overview = signal<FeatureUsageOverview | undefined>(undefined);
    readonly adoption = signal<FeatureAdoption[] | undefined>(undefined);
    readonly selectedWindow = signal<number>(30);
    readonly activeTab = signal<number>(0);
    readonly searchTerm = signal<string>('');
    readonly selectedModule = signal<string>(ALL_MODULES);
    readonly selectedCallerRole = signal<string>(ALL_ROLES);
    readonly sortField = signal<SortField>('callCount');
    readonly sortAscending = signal<boolean>(false);

    readonly selectedTrendRow = signal<FeatureUsageRow | undefined>(undefined);
    readonly trendPoints = signal<FeatureUsageTrendPoint[] | undefined>(undefined);

    /**
     * The window and the role can be changed, and a chart opened, faster than the server answers. Each new request cancels
     * the one it supersedes, because a slower earlier response arriving last would otherwise overwrite the page with data
     * for a filter or a feature the controls no longer show.
     */
    private overviewSubscription?: Subscription;

    private trendSubscription?: Subscription;

    /**
     * Collapses entries that share a `@FeatureUsage` label into one row. Everything else maps one to one.
     */
    readonly allRows = computed<FeatureUsageRow[]>(() => {
        const rowsByKey = new Map<string, FeatureUsageRow>();
        for (const entry of this.overview()?.features ?? []) {
            const labelled = !!entry.featureLabel;
            const key = `${entry.module}/${labelled ? entry.featureLabel : entry.identifier}`;
            const existing = rowsByKey.get(key);
            if (existing) {
                mergeInto(existing, entry);
            } else {
                rowsByKey.set(key, toRow(key, entry, labelled));
            }
        }
        // The server counts distinct days per grouped feature; only fall back to the per-endpoint lower bound computed in
        // mergeInto when it did not report one (nothing used in the window, so the row is at zero anyway).
        const exactActiveDays = new Map((this.overview()?.activeDaysPerFeature ?? []).map((entry) => [`${entry.module}/${entry.featureKey}`, entry.activeDays]));
        return [...rowsByKey.values()].map((row) => finalizeDerivedValues(cloneWith(row, { activeDays: exactActiveDays.get(row.key) ?? row.activeDays })));
    });

    /**
     * The headline counts, in features rather than endpoints.
     * <p>
     * The server counts inventory rows, which are endpoints. Reporting those next to tables that list grouped features made
     * the page contradict itself: "895 unused" above a list of 131 rows.
     */
    readonly trackedFeatureCount = computed<number>(() => this.allRows().length);

    readonly retiredFeatureCount = computed<number>(() => this.allRows().filter((row) => row.retired).length);

    readonly modules = computed<string[]>(() => [...new Set(this.allRows().map((row) => row.module))].sort((first, second) => first.localeCompare(second)));

    /** Resolved eagerly rather than through a pipe, because the select renders `optionLabel` verbatim. */
    private readonly allModulesLabel = signal<string>('');

    readonly moduleOptions = computed(() => [{ label: this.allModulesLabel(), value: ALL_MODULES }, ...this.modules().map((module) => ({ label: module, value: module }))]);

    /** Resolved eagerly for the same reason as the module label. */
    private readonly allRolesLabel = signal<string>('');

    readonly callerRoleOptions = computed(() => [{ label: this.allRolesLabel(), value: ALL_ROLES }, ...FEATURE_USAGE_CALLER_ROLES.map((role) => ({ label: role, value: role }))]);

    /**
     * The actionable list: no usage, and the feature still exists. A retired feature has zero calls by definition and would
     * only bury the rows that still need a decision.
     */
    readonly unusedRows = computed<FeatureUsageRow[]>(() => this.allRows().filter((row) => row.callCount === 0 && !row.retired));

    /** Module and search filtering only. Both the tree and the flat table start from this. */
    readonly filteredRows = computed<FeatureUsageRow[]>(() => {
        const term = this.searchTerm().trim().toLowerCase();
        const module = this.selectedModule();
        return this.allRows().filter((row) => (module === ALL_MODULES || row.module === module) && (!term || matchesTerm(row, term)));
    });

    readonly visibleRows = computed<FeatureUsageRow[]>(() => {
        const rows = this.activeTab() === TAB_UNUSED ? this.filteredRows().filter((row) => row.callCount === 0 && !row.retired) : this.filteredRows();
        return [...rows].sort(this.rowComparator());
    });

    /** Keys of the expanded tree nodes. Everything starts collapsed: every module expanded at once is not a summary. */
    readonly expandedKeys = signal<ReadonlySet<string>>(new Set());

    readonly featureTree = computed<FeatureTreeNode[]>(() => buildTree(this.filteredRows()));

    /**
     * The tree flattened to the rows that are currently visible, so the template can render it as a plain table instead of
     * a recursive component.
     */
    readonly visibleTreeRows = computed<FeatureTreeRow[]>(() => {
        const total = this.featureTree().reduce((sum, node) => sum + node.callCount, 0);
        const expanded = this.expandedKeys();
        const rows: FeatureTreeRow[] = [];
        const visit = (nodes: FeatureTreeNode[]) => {
            for (const node of nodes) {
                const isExpanded = expanded.has(node.key);
                rows.push(cloneWith(node, { hasChildren: node.children.length > 0, expanded: isExpanded, sharePercent: total ? (node.callCount / total) * 100 : 0 }));
                if (isExpanded) {
                    visit(node.children);
                }
            }
        };
        visit(this.featureTree());
        return rows;
    });

    readonly visibleAdoption = computed<FeatureAdoption[]>(() => {
        const term = this.searchTerm().trim().toLowerCase();
        const module = this.selectedModule();
        return (this.adoption() ?? []).filter(
            (entry) => (module === ALL_MODULES || entry.module === module) && (!term || entry.key.toLowerCase().includes(term) || entry.module.toLowerCase().includes(term)),
        );
    });

    readonly roleDistribution = computed(() => this.overview()?.roleDistribution ?? []);

    readonly trendChartData = computed(() => {
        const points = this.trendPoints();
        if (!points) {
            return undefined;
        }
        return multiSeriesToLineData([{ name: this.selectedTrendRow()?.name ?? '', series: this.dailySeriesOverWholeWindow(points) }], this.trendColors());
    });

    /**
     * Expands the trend into one point per day of the window, filling the days the server left out with zero.
     *
     * The query returns only the days that saw usage, and the chart's axis is categorical: it draws the points it is
     * given, evenly spaced, whatever their dates. A feature used on the first and last day of a week therefore rendered
     * as two adjacent points, which reads as steady use across the week instead of two isolated bursts with five silent
     * days between them. That is the opposite of what the chart is consulted for.
     *
     * The window is materialised from the overview's `from`, which the server derived from its own clock, rather than
     * from the browser's. A viewer whose clock is off by a day would otherwise generate day keys that match none of the
     * server's buckets, and every lookup would miss: the chart would show a flat zero line for a feature that is in
     * fact used. An empty window is a legitimate answer and renders as exactly that flat line.
     */
    private dailySeriesOverWholeWindow(points: FeatureUsageTrendPoint[]): { name: string; value: number }[] {
        const callsByDay = new Map(points.map((point) => [point.usageDay, point.callCount]));
        const overview = this.overview();
        const firstDay = overview
            ? Date.parse(`${overview.from}T00:00:00Z`)
            : Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), new Date().getUTCDate()) - (this.selectedWindow() - 1) * MILLISECONDS_PER_DAY;
        const dayCount = overview?.days ?? this.selectedWindow();
        const series: { name: string; value: number }[] = [];
        for (let offset = 0; offset < dayCount; offset++) {
            const day = new Date(firstDay + offset * MILLISECONDS_PER_DAY).toISOString().slice(0, 10);
            series.push({ name: day, value: callsByDay.get(day) ?? 0 });
        }
        return series;
    }

    /** Not a computed: it depends on nothing, so recomputing it would only pretend to be reactive. */
    readonly trendChartOptions = lineChartOptions({ yAxis: { min: 0 }, legend: false });

    ngOnInit(): void {
        this.allModulesLabel.set(this.translateService.instant('artemisApp.featureUsage.allModules'));
        this.allRolesLabel.set(this.translateService.instant('artemisApp.featureUsage.allRoles'));
        this.load();
    }

    onWindowChanged(days: number): void {
        this.selectedWindow.set(days);
        this.closeTrend();
        this.load();
    }

    onCallerRoleChanged(callerRole: string): void {
        this.selectedCallerRole.set(callerRole);
        this.closeTrend();
        this.load();
    }

    toggleTreeNode(key: string): void {
        const expanded = new Set(this.expandedKeys());
        if (!expanded.delete(key)) {
            expanded.add(key);
        }
        this.expandedKeys.set(expanded);
    }

    /** Opens every module and area, for when the whole taxonomy needs scanning rather than exploring. */
    expandAllTreeNodes(): void {
        const keys = new Set<string>();
        const visit = (nodes: FeatureTreeNode[]) => {
            for (const node of nodes) {
                if (node.children.length) {
                    keys.add(node.key);
                    visit(node.children);
                }
            }
        };
        visit(this.featureTree());
        this.expandedKeys.set(keys);
    }

    collapseAllTreeNodes(): void {
        this.expandedKeys.set(new Set());
    }

    onTableSort(event: TumUiTableSortEvent): void {
        this.sortField.set(event.field as SortField);
        this.sortAscending.set(event.order === 1);
    }

    /**
     * Charts the daily usage of a row. Only possible for a row that is a single feature: an aggregated label would need one
     * request per underlying endpoint, which is not worth it for a chart.
     */
    showTrend(row: FeatureUsageRow): void {
        if (row.featureIds.length === 0) {
            return;
        }
        this.trendSubscription?.unsubscribe();
        this.selectedTrendRow.set(row);
        this.trendPoints.set(undefined);
        const callerRole = this.selectedCallerRole();
        this.trendSubscription = this.featureUsageService.getTrend(row.featureIds, this.selectedWindow(), callerRole === ALL_ROLES ? undefined : callerRole).subscribe({
            next: (points) => this.trendPoints.set(points),
            error: (error) => {
                // Without closing, the panel keeps spinning forever: nothing else ever sets trendPoints.
                this.alertService.error(error.message);
                this.closeTrend();
            },
        });
    }

    /**
     * Sends the weekly digest email on demand. The scheduled job needs the scheduling profile and a configured recipient, so
     * this is how an administrator finds out whether it will actually arrive.
     */
    sendDigestEmail(): void {
        this.sendingDigest.set(true);
        this.featureUsageService.sendDigestEmail().subscribe({
            next: () => {
                this.sendingDigest.set(false);
                this.alertService.success('artemisApp.featureUsage.digestSent');
            },
            error: () => {
                this.sendingDigest.set(false);
                this.alertService.error('artemisApp.featureUsage.digestFailed');
            },
        });
    }

    closeTrend(): void {
        this.trendSubscription?.unsubscribe();
        this.selectedTrendRow.set(undefined);
        this.trendPoints.set(undefined);
    }

    private load(): void {
        this.overviewSubscription?.unsubscribe();
        // Discarded rather than left on screen. The window and role signals have already changed by the time this runs, so
        // keeping the previous report would show the old selection's numbers under the new controls with nothing marking
        // them as stale, and would leave them there indefinitely if the request fails - the page would go on presenting a
        // 30 day report as if it answered the 7 day question.
        this.overview.set(undefined);
        this.loading.set(true);
        const callerRole = this.selectedCallerRole();
        this.overviewSubscription = this.featureUsageService.getOverview(this.selectedWindow(), callerRole === ALL_ROLES ? undefined : callerRole).subscribe({
            next: (overview) => {
                this.overview.set(overview);
                this.loading.set(false);
            },
            error: (error) => {
                this.loading.set(false);
                this.alertService.error(error.message);
            },
        });
        if (!this.adoption()) {
            this.featureUsageService.getAdoption().subscribe({
                next: (adoption) => this.adoption.set(adoption),
                error: (error) => this.alertService.error(error.message),
            });
        }
    }

    private rowComparator(): (first: FeatureUsageRow, second: FeatureUsageRow) => number {
        const field = this.sortField();
        const direction = this.sortAscending() ? 1 : -1;
        return (first, second) => {
            const firstValue = first[field];
            const secondValue = second[field];
            if (typeof firstValue === 'number' && typeof secondValue === 'number') {
                return (firstValue - secondValue) * direction;
            }
            // Undefined sorts last regardless of direction: an unused feature has no "last used" day, and letting those
            // float to the top of a descending sort would bury the rows the column is being sorted for.
            if (firstValue === undefined || secondValue === undefined) {
                return firstValue === secondValue ? 0 : firstValue === undefined ? 1 : -1;
            }
            return String(firstValue).localeCompare(String(secondValue)) * direction;
        };
    }
}

function matchesTerm(row: FeatureUsageRow, term: string): boolean {
    return (
        row.name.toLowerCase().includes(term) ||
        row.module.toLowerCase().includes(term) ||
        // a labelled row is named after the feature, so searching for a path has to reach the endpoints behind it
        row.identifiers.some((identifier) => identifier.toLowerCase().includes(term))
    );
}

function toRow(key: string, entry: FeatureUsageEntry, labelled: boolean): FeatureUsageRow {
    // A catalogued label is "area/feature". Anything not catalogued yet is grouped under one clearly named bucket rather
    // than hidden, so a growing bucket is itself the signal that the catalogue needs an entry.
    const separator = labelled ? entry.featureLabel!.indexOf('/') : -1;
    const area = separator > 0 ? entry.featureLabel!.slice(0, separator) : UNCATALOGUED_AREA;
    const feature = separator > 0 ? entry.featureLabel!.slice(separator + 1) : (entry.featureLabel ?? entry.identifier);
    return {
        key,
        module: entry.module,
        area,
        feature,
        name: `${area}/${feature}`,
        featureKind: entry.featureKind,
        endpointCount: 1,
        identifiers: [entry.identifier],
        retired: !!entry.retired,
        featureIds: [entry.featureId],
        callCount: entry.callCount ?? 0,
        errorCount: entry.errorCount ?? 0,
        errorRate: 0,
        durationSumMs: entry.durationSumMs ?? 0,
        meanDurationMs: 0,
        maxDurationMs: entry.durationMaxMs ?? 0,
        activeDays: entry.activeDays ?? 0,
        lastUsedDay: entry.lastUsedDay,
    };
}

function mergeInto(row: FeatureUsageRow, entry: FeatureUsageEntry): void {
    row.endpointCount += 1;
    row.identifiers.push(entry.identifier);
    // A label counts as retired only once every endpoint behind it is gone; while one remains, the feature still exists.
    row.retired = row.retired && !!entry.retired;
    row.featureIds.push(entry.featureId);
    row.callCount += entry.callCount ?? 0;
    row.errorCount += entry.errorCount ?? 0;
    row.durationSumMs += entry.durationSumMs ?? 0;
    row.maxDurationMs = Math.max(row.maxDurationMs, entry.durationMaxMs ?? 0);
    // Days cannot be summed across endpoints, they would double count a day on which two of them were used, and the
    // largest undercounts when two endpoints were used on different days. This keeps a lower bound only as the fallback
    // for when the server reported no grouped count; allRows replaces it with the exact distinct-day union.
    row.activeDays = Math.max(row.activeDays, entry.activeDays ?? 0);
    row.lastUsedDay = maxDay(row.lastUsedDay, entry.lastUsedDay);
}

function finalizeDerivedValues(row: FeatureUsageRow): FeatureUsageRow {
    row.errorRate = row.callCount ? (row.errorCount / row.callCount) * 100 : 0;
    row.meanDurationMs = row.callCount ? Math.round(row.durationSumMs / row.callCount) : 0;
    return row;
}

/**
 * Builds module to area to feature, with every level carrying the totals of what is below it.
 *
 * The aggregate that needs care is `unusedCount`: it counts the features below a node that this version still offers and
 * that nobody used, which is what makes a quiet branch worth opening. Retired features are left out of it, otherwise a
 * module would look full of work that has already been done.
 */
function buildTree(rows: FeatureUsageRow[]): FeatureTreeNode[] {
    const modules = new Map<string, Map<string, FeatureUsageRow[]>>();
    for (const row of rows) {
        const areas = modules.get(row.module) ?? new Map<string, FeatureUsageRow[]>();
        modules.set(row.module, areas);
        areas.set(row.area, [...(areas.get(row.area) ?? []), row]);
    }

    const moduleNodes = [...modules.entries()].map(([module, areas]) => {
        const areaNodes = [...areas.entries()].map(([area, areaRows]) => aggregate(`${module}/${area}`, area, 1, areaRows.map(featureNode)));
        return aggregate(module, module, 0, areaNodes);
    });
    return sortByCalls(moduleNodes);
}

function featureNode(row: FeatureUsageRow): FeatureTreeNode {
    return {
        key: row.key,
        name: row.feature,
        level: 2,
        callCount: row.callCount,
        errorCount: row.errorCount,
        errorRate: row.errorRate,
        durationSumMs: row.durationSumMs,
        featureCount: row.retired ? 0 : 1,
        unusedCount: !row.retired && row.callCount === 0 ? 1 : 0,
        lastUsedDay: row.lastUsedDay,
        children: [],
    };
}

function aggregate(key: string, name: string, level: number, children: FeatureTreeNode[]): FeatureTreeNode {
    const callCount = children.reduce((sum, child) => sum + child.callCount, 0);
    const errorCount = children.reduce((sum, child) => sum + child.errorCount, 0);
    return {
        key,
        name,
        level,
        callCount,
        errorCount,
        errorRate: callCount ? (errorCount / callCount) * 100 : 0,
        durationSumMs: children.reduce((sum, child) => sum + child.durationSumMs, 0),
        featureCount: children.reduce((sum, child) => sum + child.featureCount, 0),
        unusedCount: children.reduce((sum, child) => sum + child.unusedCount, 0),
        lastUsedDay: children.reduce<string | undefined>((latest, child) => maxDay(latest, child.lastUsedDay), undefined),
        children: sortByCalls(children),
    };
}

function sortByCalls(nodes: FeatureTreeNode[]): FeatureTreeNode[] {
    // Busiest first, then unused ones grouped at the end where they are easy to scan, then alphabetically for stability.
    return [...nodes].sort((first, second) => second.callCount - first.callCount || first.name.localeCompare(second.name));
}

function maxDay(first: string | undefined, second: string | undefined): string | undefined {
    if (!first) {
        return second;
    }
    if (!second) {
        return first;
    }
    return first >= second ? first : second;
}
