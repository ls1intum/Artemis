import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';

import { FeatureUsageComponent } from './feature-usage.component';
import { FeatureUsageService } from './feature-usage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { FeatureAdoption, FeatureKind, FeatureUsageEntry, FeatureUsageOverview, FeatureUsageTrendPoint } from './feature-usage.model';

describe('FeatureUsageComponent', () => {
    let component: FeatureUsageComponent;
    let fixture: ComponentFixture<FeatureUsageComponent>;
    let featureUsageService: FeatureUsageService;
    let alertService: AlertService;

    /** Two endpoints that share a label, one that does not, and one nobody called. */
    const entries: FeatureUsageEntry[] = [
        {
            featureId: 1,
            featureKind: FeatureKind.REST,
            module: 'programming',
            identifier: 'POST api/programming/programming-exercises',
            featureLabel: 'configuration/static-code-analysis',
            callCount: 30,
            errorCount: 3,
            durationSumMs: 300,
            durationMaxMs: 50,
            activeDays: 3,
            lastUsedDay: '2026-08-04',
        },
        {
            featureId: 2,
            featureKind: FeatureKind.REST,
            module: 'programming',
            identifier: 'PUT api/programming/programming-exercises/{exerciseId}',
            featureLabel: 'configuration/static-code-analysis',
            callCount: 10,
            errorCount: 0,
            durationSumMs: 100,
            durationMaxMs: 80,
            activeDays: 5,
            lastUsedDay: '2026-08-05',
        },
        {
            featureId: 3,
            featureKind: FeatureKind.GIT,
            module: 'localvc',
            identifier: 'push/assignment',
            callCount: 8,
            errorCount: 0,
            durationSumMs: 800,
            durationMaxMs: 200,
            activeDays: 2,
            lastUsedDay: '2026-08-03',
        },
        {
            featureId: 4,
            featureKind: FeatureKind.REST,
            module: 'quiz',
            identifier: 'GET api/quiz/never-called',
            callCount: 0,
            errorCount: 0,
            durationSumMs: 0,
            durationMaxMs: 0,
            activeDays: 0,
            retired: false,
        },
        {
            featureId: 5,
            featureKind: FeatureKind.REST,
            module: 'quiz',
            identifier: 'GET api/quiz/removed-two-releases-ago',
            callCount: 0,
            errorCount: 0,
            durationSumMs: 0,
            durationMaxMs: 0,
            activeDays: 0,
            retired: true,
        },
    ];

    const overview: FeatureUsageOverview = {
        days: 30,
        from: '2026-07-07',
        trackedFeatures: 5,
        unusedFeatures: 1,
        retiredFeatures: 1,
        totalCalls: 48,
        recordingSince: '2026-01-15T08:00:00Z',
        features: entries,
        roleDistribution: [{ callerRole: 'STUDENT', callCount: 40 }],
    };

    const adoption: FeatureAdoption[] = [
        { module: 'programming', key: 'static-code-analysis', count: 3, total: 10 },
        { module: 'quiz', key: 'mode/batched', count: 0, total: 4 },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FeatureUsageComponent],
            providers: [
                MockProvider(FeatureUsageService, {
                    getOverview: () => of(overview),
                    getAdoption: () => of(adoption),
                    getTrend: () => of([{ usageDay: '2026-08-05', callCount: 10 }]),
                    sendDigestEmail: () => of(undefined),
                }),
                MockProvider(AlertService, { error: vi.fn(), success: vi.fn() }),
                MockProvider(TranslateService, { instant: ((key: string) => key) as TranslateService['instant'] }),
            ],
        });

        fixture = TestBed.createComponent(FeatureUsageComponent);
        component = fixture.componentInstance;
        featureUsageService = TestBed.inject(FeatureUsageService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load the overview and the adoption data on init', () => {
        const overviewSpy = vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(of(overview));
        const adoptionSpy = vi.spyOn(featureUsageService, 'getAdoption').mockReturnValue(of(adoption));

        component.ngOnInit();

        expect(overviewSpy).toHaveBeenCalledWith(30, undefined);
        expect(adoptionSpy).toHaveBeenCalledOnce();
        expect(component.overview()).toEqual(overview);
    });

    it('should collapse endpoints that share a label into one row', () => {
        component.ngOnInit();

        const row = component.allRows().find((candidate) => candidate.feature === 'static-code-analysis');
        expect(row).toBeDefined();
        expect(row!.endpointCount).toBe(2);
        expect(row!.callCount).toBe(40);
        expect(row!.errorCount).toBe(3);
        // 400ms over 40 calls
        expect(row!.meanDurationMs).toBe(10);
        expect(row!.maxDurationMs).toBe(80);
        // no activeDaysPerFeature in this fixture, so the row falls back to the per-endpoint lower bound
        expect(row!.activeDays).toBe(5);
        expect(row!.lastUsedDay).toBe('2026-08-05');
        // both endpoints stay addressable, so the chart can cover the whole feature
        expect(row!.featureIds).toEqual([1, 2]);
    });

    it('should use the exact distinct-day union the server reports for a grouped feature', () => {
        // The two endpoints behind the label were used on disjoint days: 3 and 5 with no overlap is 8 active days for the
        // feature, which neither summing nor maxing the per-endpoint counts can produce from the entries alone.
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(
            of({
                ...overview,
                activeDaysPerFeature: [{ module: 'programming', featureKey: 'configuration/static-code-analysis', activeDays: 8 }],
            }),
        );
        component.ngOnInit();

        const row = component.allRows().find((candidate) => candidate.feature === 'static-code-analysis');
        expect(row!.activeDays).toBe(8);
    });

    it('should keep the caller role filter when charting a row', () => {
        component.ngOnInit();
        const trendSpy = vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(of([{ usageDay: '2026-08-05', callCount: 10 }]));

        component.onCallerRoleChanged('STUDENT');
        const row = component.allRows().find((candidate) => candidate.feature === 'static-code-analysis');
        component.showTrend(row!);

        // otherwise opening a chart on a role-filtered table silently widens it back to every caller
        expect(trendSpy).toHaveBeenCalledWith([1, 2], expect.any(Number), 'STUDENT');
    });

    it('should chart every caller when no role is selected', () => {
        component.ngOnInit();
        const trendSpy = vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(of([{ usageDay: '2026-08-05', callCount: 10 }]));

        const row = component.allRows().find((candidate) => candidate.feature === 'static-code-analysis');
        component.showTrend(row!);

        expect(trendSpy).toHaveBeenCalledWith([1, 2], expect.any(Number), undefined);
    });

    it('should count the headline numbers in features rather than in endpoints', () => {
        component.ngOnInit();

        // five inventory rows, two of which share a label, so the page must lead with four features and not with five
        expect(overview.trackedFeatures).toBe(5);
        expect(component.trackedFeatureCount()).toBe(4);
        expect(component.retiredFeatureCount()).toBe(1);
        expect(component.unusedRows()).toHaveLength(1);
    });

    it('should keep unlabelled features as their own rows', () => {
        component.ngOnInit();

        const row = component.allRows().find((candidate) => candidate.feature === 'push/assignment');
        expect(row).toBeDefined();
        expect(row!.endpointCount).toBe(1);
        expect(row!.featureIds).toEqual([3]);
        expect(row!.featureKind).toBe(FeatureKind.GIT);
    });

    it('should compute the error rate only where there were calls', () => {
        component.ngOnInit();

        const used = component.allRows().find((candidate) => candidate.feature === 'static-code-analysis');
        const unused = component.allRows().find((candidate) => candidate.feature === 'GET api/quiz/never-called');
        expect(used!.errorRate).toBeCloseTo(7.5);
        expect(unused!.errorRate).toBe(0);
    });

    it('should list the features that saw no usage', () => {
        component.ngOnInit();

        expect(component.unusedRows()).toHaveLength(1);
        expect(component.unusedRows()[0].feature).toBe('GET api/quiz/never-called');
    });

    it('should keep retired features out of the actionable unused list', () => {
        component.ngOnInit();

        // it has zero calls too, but it no longer exists, so it is not a decision anyone has to make
        expect(component.unusedRows().map((row) => row.feature)).not.toContain('GET api/quiz/removed-two-releases-ago');
        expect(component.allRows().find((row) => row.feature === 'GET api/quiz/removed-two-releases-ago')!.retired).toBeTruthy();
    });

    it('should treat a label as retired only once every endpoint behind it is gone', () => {
        component.ngOnInit();

        // both endpoints of the label are still offered
        expect(component.allRows().find((row) => row.feature === 'static-code-analysis')!.retired).toBeFalsy();
    });

    it('should expose the endpoints behind a labelled row', () => {
        component.ngOnInit();

        const row = component.allRows().find((candidate) => candidate.feature === 'static-code-analysis');
        expect(row!.identifiers).toEqual(['POST api/programming/programming-exercises', 'PUT api/programming/programming-exercises/{exerciseId}']);
    });

    it('should find a labelled row by the path of one of its endpoints', () => {
        component.ngOnInit();

        component.searchTerm.set('programming-exercises/{exerciseId}');

        expect(component.visibleRows()).toHaveLength(1);
        expect(component.visibleRows()[0].feature).toBe('static-code-analysis');
    });

    it('should reload restricted to the selected caller role', () => {
        component.ngOnInit();
        const overviewSpy = vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(of(overview));

        component.onCallerRoleChanged('STUDENT');

        expect(overviewSpy).toHaveBeenCalledWith(30, 'STUDENT');
        expect(component.selectedCallerRole()).toBe('STUDENT');
    });

    it('should drop the role restriction again', () => {
        component.ngOnInit();
        component.onCallerRoleChanged('STUDENT');
        const overviewSpy = vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(of(overview));

        component.onCallerRoleChanged('');

        expect(overviewSpy).toHaveBeenCalledWith(30, undefined);
    });

    it('should show only unused features on the unused tab', () => {
        component.ngOnInit();

        component.activeTab.set(2);

        expect(component.visibleRows()).toHaveLength(1);
        expect(component.visibleRows()[0].callCount).toBe(0);
    });

    it('should filter by module', () => {
        component.ngOnInit();

        component.selectedModule.set('quiz');

        expect(component.visibleRows()).toHaveLength(2);
        expect(component.visibleRows().map((row) => row.module)).toEqual(['quiz', 'quiz']);
    });

    it('should filter by search term across feature and module', () => {
        component.ngOnInit();

        component.searchTerm.set('localvc');
        expect(component.visibleRows()).toHaveLength(1);

        component.searchTerm.set('static-code');
        expect(component.visibleRows()).toHaveLength(1);
        expect(component.visibleRows()[0].feature).toBe('static-code-analysis');
    });

    it('should sort by calls descending by default', () => {
        component.ngOnInit();

        expect(component.visibleRows().map((row) => row.callCount)).toEqual([40, 8, 0, 0]);
    });

    it('should sort features without a last used day last, in both directions', () => {
        component.ngOnInit();
        component.onTableSort({ field: 'lastUsedDay', order: -1 });

        expect(component.visibleRows().at(-1)!.lastUsedDay).toBeUndefined();

        component.onTableSort({ field: 'lastUsedDay', order: 1 });

        expect(component.visibleRows().at(-1)!.lastUsedDay).toBeUndefined();
    });

    it('should reload with the new window and drop the open trend when the period changes', () => {
        component.ngOnInit();
        const overviewSpy = vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(of(overview));
        component.showTrend(component.allRows().find((row) => row.feature === 'push/assignment')!);
        expect(component.selectedTrendRow()).toBeDefined();

        component.onWindowChanged(90);

        expect(overviewSpy).toHaveBeenCalledWith(90, undefined);
        expect(component.selectedTrendRow()).toBeUndefined();
        expect(component.trendPoints()).toBeUndefined();
    });

    it('should load the trend of a single feature', () => {
        component.ngOnInit();
        const trendSpy = vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(of([{ usageDay: '2026-08-05', callCount: 10 }]));

        component.showTrend(component.allRows().find((row) => row.feature === 'push/assignment')!);

        expect(trendSpy).toHaveBeenCalledWith([3], 30, undefined);
        expect(component.trendPoints()).toHaveLength(1);
        expect(component.trendChartData()).toBeDefined();
    });

    it('should chart every endpoint of an aggregated row', () => {
        component.ngOnInit();
        const trendSpy = vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(of([{ usageDay: '2026-08-05', callCount: 40 }]));

        component.showTrend(component.allRows().find((row) => row.feature === 'static-code-analysis')!);

        // charting one of the two endpoints would report a fraction of the feature's usage as the feature's usage
        expect(trendSpy).toHaveBeenCalledWith([1, 2], 30, undefined);
        expect(component.selectedTrendRow()?.feature).toBe('static-code-analysis');
    });

    it('should filter the adoption entries as well', () => {
        component.ngOnInit();
        component.activeTab.set(3);

        component.selectedModule.set('quiz');

        expect(component.visibleAdoption()).toHaveLength(1);
        expect(component.visibleAdoption()[0].key).toBe('mode/batched');
    });

    it('should send the weekly digest email on demand', () => {
        component.ngOnInit();
        const sendSpy = vi.spyOn(featureUsageService, 'sendDigestEmail').mockReturnValue(of(undefined));
        const successSpy = vi.spyOn(alertService, 'success');

        component.sendDigestEmail();

        expect(sendSpy).toHaveBeenCalledOnce();
        expect(successSpy).toHaveBeenCalled();
        expect(component.sendingDigest()).toBeFalsy();
    });

    it('should report a failed digest send and stop the spinner', () => {
        component.ngOnInit();
        vi.spyOn(featureUsageService, 'sendDigestEmail').mockReturnValue(throwError(() => new Error('no recipient configured')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.sendDigestEmail();

        expect(errorSpy).toHaveBeenCalled();
        expect(component.sendingDigest()).toBeFalsy();
    });

    describe('feature tree', () => {
        it('should nest features under their area and module', () => {
            component.ngOnInit();

            const programming = component.featureTree().find((node) => node.name === 'programming');
            expect(programming).toBeDefined();
            expect(programming!.level).toBe(0);
            const configuration = programming!.children.find((node) => node.name === 'configuration');
            expect(configuration).toBeDefined();
            expect(configuration!.children.map((node) => node.name)).toEqual(['static-code-analysis']);
        });

        it('should total calls up the tree', () => {
            component.ngOnInit();

            const programming = component.featureTree().find((node) => node.name === 'programming');
            // the two endpoints of the labelled feature, 30 + 10
            expect(programming!.callCount).toBe(40);
            expect(programming!.children[0].callCount).toBe(40);
        });

        it('should group uncatalogued endpoints under one clearly named area', () => {
            component.ngOnInit();

            const quiz = component.featureTree().find((node) => node.name === 'quiz');
            expect(quiz!.children.map((node) => node.name)).toEqual(['other']);
        });

        it('should order modules by calls', () => {
            component.ngOnInit();

            expect(component.featureTree().map((node) => node.name)).toEqual(['programming', 'localvc', 'quiz']);
        });

        it('should count unused features below a node but exclude retired ones', () => {
            component.ngOnInit();

            const quiz = component.featureTree().find((node) => node.name === 'quiz');
            // one unused endpoint plus one retired one; only the unused one is work anybody still has to decide about
            expect(quiz!.unusedCount).toBe(1);
            expect(quiz!.featureCount).toBe(1);
        });

        it('should start fully collapsed and show only modules', () => {
            component.ngOnInit();

            expect(component.visibleTreeRows().every((row) => row.level === 0)).toBeTruthy();
            expect(component.visibleTreeRows()).toHaveLength(3);
        });

        it('should reveal the areas of a module when it is expanded', () => {
            component.ngOnInit();

            component.toggleTreeNode('programming');

            expect(component.visibleTreeRows().map((row) => row.name)).toContain('configuration');
            expect(component.visibleTreeRows().find((row) => row.name === 'programming')!.expanded).toBeTruthy();
        });

        it('should collapse again on a second toggle', () => {
            component.ngOnInit();

            component.toggleTreeNode('programming');
            component.toggleTreeNode('programming');

            expect(component.visibleTreeRows().every((row) => row.level === 0)).toBeTruthy();
        });

        it('should expand and collapse the whole tree', () => {
            component.ngOnInit();

            component.expandAllTreeNodes();
            expect(component.visibleTreeRows().some((row) => row.level === 2)).toBeTruthy();

            component.collapseAllTreeNodes();
            expect(component.visibleTreeRows().every((row) => row.level === 0)).toBeTruthy();
        });

        it('should report each module share of the total calls', () => {
            component.ngOnInit();

            const programming = component.visibleTreeRows().find((row) => row.name === 'programming');
            // 40 of 48 recorded calls
            expect(programming!.sharePercent).toBeCloseTo(83.33, 1);
        });

        it('should prune the tree to the module filter', () => {
            component.ngOnInit();

            component.selectedModule.set('quiz');

            expect(component.featureTree().map((node) => node.name)).toEqual(['quiz']);
        });
    });

    it('should offer every caller role the server records, including SUPER_ADMIN', () => {
        component.ngOnInit();

        const roles = component.callerRoleOptions().map((option) => option.value);

        // SecurityUtils records SUPER_ADMIN as its own bucket and the role distribution exposes it, so omitting it here
        // made that traffic unfilterable even though it can be a substantial share of the report
        expect(roles).toContain('SUPER_ADMIN');
        // Highest first, matching the server's precedence order
        expect(roles.indexOf('SUPER_ADMIN')).toBeLessThan(roles.indexOf('ADMIN'));
        expect(roles).toEqual(expect.arrayContaining(['SUPER_ADMIN', 'ADMIN', 'INSTRUCTOR', 'EDITOR', 'TEACHING_ASSISTANT', 'STUDENT', 'ANONYMOUS']));
    });

    it('should chart every day of the window, including the ones with no usage', () => {
        // The days are stated outright rather than derived from the browser clock, because the window has to come from
        // the server's own range: a viewer whose clock is off by a day would otherwise generate keys that match none of
        // the server's buckets and see a flat zero line for a feature that is in fact used.
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(of({ ...overview, days: 7, from: '2026-07-07' }));
        // usage on the first and last day of a seven day window and nothing in between
        vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(
            of([
                { usageDay: '2026-07-07', callCount: 4 },
                { usageDay: '2026-07-13', callCount: 9 },
            ]),
        );

        component.ngOnInit();
        component.onWindowChanged(7);
        component.showTrend(component.allRows()[0]);

        const labels = component.trendChartData()!.labels as string[];
        const values = (component.trendChartData()!.datasets[0].data as number[]).map(Number);
        // The axis is categorical, so two points would be drawn adjacent and read as steady use across the week rather
        // than two isolated bursts with five silent days between them.
        expect(labels).toHaveLength(7);
        expect(values).toEqual([4, 0, 0, 0, 0, 0, 9]);
        expect(labels[0]).toBe('2026-07-07');
        expect(labels[6]).toBe('2026-07-13');
    });

    it('should chart a feature with no usage as a flat line instead of spinning forever', () => {
        vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(of([]));

        component.ngOnInit();
        component.showTrend(component.allRows().find((row) => row.callCount === 0)!);

        // The template falls back to the spinner whenever trendChartData() is undefined, so an empty answer has to
        // produce a chart of its own: the unused tab lists exactly the features whose trend comes back empty, and the
        // request has completed, so nothing would ever replace the spinner.
        const data = component.trendChartData();
        expect(data).toBeDefined();
        expect((data!.datasets[0].data as number[]).map(Number)).toEqual(new Array(30).fill(0));
    });

    it('should close the trend panel when the trend request fails', () => {
        vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(throwError(() => new Error('trend unavailable')));

        component.ngOnInit();
        component.showTrend(component.allRows()[0]);

        // Leaving the row selected would leave the spinner running with nothing left to load it.
        expect(component.selectedTrendRow()).toBeUndefined();
        expect(component.trendPoints()).toBeUndefined();
        expect(alertService.error).toHaveBeenCalled();
    });

    it('should not show the previous report while a new window is still loading', () => {
        component.ngOnInit();
        expect(component.overview()).toEqual(overview);
        const pending = new Subject<FeatureUsageOverview>();
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(pending);

        component.onWindowChanged(7);

        // the controls already say 7 days, so leaving the 30 day numbers up would present them as the answer to a
        // question they were not the answer to
        expect(component.overview()).toBeUndefined();
        expect(component.loading()).toBeTruthy();
    });

    it('should not keep the previous report when the reload fails', () => {
        component.ngOnInit();
        expect(component.overview()).toEqual(overview);
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(throwError(() => new Error('server down')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.onCallerRoleChanged('STUDENT');

        // otherwise the stale report stays on screen for good, indistinguishable from a current one
        expect(component.overview()).toBeUndefined();
        expect(component.loading()).toBeFalsy();
        expect(errorSpy).toHaveBeenCalled();
    });

    it('should ignore an overview response that a newer window has superseded', () => {
        component.ngOnInit();
        const slowFirst = new Subject<FeatureUsageOverview>();
        const fastSecond = new Subject<FeatureUsageOverview>();
        const sevenDayOverview = { ...overview, days: 7 };
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValueOnce(slowFirst).mockReturnValueOnce(fastSecond);

        component.onWindowChanged(90);
        component.onWindowChanged(7);
        fastSecond.next(sevenDayOverview);
        // the 90 day query only now comes back, after the controls have already moved on to 7 days
        slowFirst.next({ ...overview, days: 90 });

        // the request was cancelled, so the stale answer cannot overwrite the report the controls describe
        expect(component.overview()).toEqual(sevenDayOverview);
        expect(component.selectedWindow()).toBe(7);
    });

    it('should ignore a trend response that a newer feature has superseded', () => {
        component.ngOnInit();
        const slowFirst = new Subject<FeatureUsageTrendPoint[]>();
        const fastSecond = new Subject<FeatureUsageTrendPoint[]>();
        vi.spyOn(featureUsageService, 'getTrend').mockReturnValueOnce(slowFirst).mockReturnValueOnce(fastSecond);
        const rows = component.allRows();

        component.showTrend(rows[0]);
        component.showTrend(rows[1]);
        fastSecond.next([{ usageDay: '2026-08-06', callCount: 2 }]);
        slowFirst.next([{ usageDay: '2026-08-05', callCount: 99 }]);

        // otherwise the chart shows the first feature's daily counts under the second feature's name
        expect(component.trendPoints()).toEqual([{ usageDay: '2026-08-06', callCount: 2 }]);
        expect(component.selectedTrendRow()).toBe(rows[1]);
    });

    it('should not fill the chart after it has been closed again', () => {
        component.ngOnInit();
        const pending = new Subject<FeatureUsageTrendPoint[]>();
        vi.spyOn(featureUsageService, 'getTrend').mockReturnValue(pending);

        component.showTrend(component.allRows()[0]);
        component.closeTrend();
        pending.next([{ usageDay: '2026-08-05', callCount: 10 }]);

        expect(component.trendPoints()).toBeUndefined();
    });

    it('should report an error when the overview cannot be loaded', () => {
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(throwError(() => new Error('server down')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.ngOnInit();

        expect(errorSpy).toHaveBeenCalled();
        expect(component.loading()).toBeFalsy();
    });
});
