import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { FeatureUsageComponent } from './feature-usage.component';
import { FeatureUsageService } from './feature-usage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { FeatureAdoption, FeatureKind, FeatureUsageEntry, FeatureUsageOverview } from './feature-usage.model';

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
        // days cannot be summed across endpoints, the largest is the correct lower bound
        expect(row!.activeDays).toBe(5);
        expect(row!.lastUsedDay).toBe('2026-08-05');
        // an aggregated row has no single feature to chart
        expect(row!.featureId).toBeUndefined();
    });

    it('should keep unlabelled features as their own rows', () => {
        component.ngOnInit();

        const row = component.allRows().find((candidate) => candidate.feature === 'push/assignment');
        expect(row).toBeDefined();
        expect(row!.endpointCount).toBe(1);
        expect(row!.featureId).toBe(3);
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

        expect(trendSpy).toHaveBeenCalledWith(3, 30);
        expect(component.trendPoints()).toHaveLength(1);
        expect(component.trendChartData()).toBeDefined();
    });

    it('should not attempt a trend for an aggregated row', () => {
        component.ngOnInit();
        const trendSpy = vi.spyOn(featureUsageService, 'getTrend');

        component.showTrend(component.allRows().find((row) => row.feature === 'static-code-analysis')!);

        expect(trendSpy).not.toHaveBeenCalled();
        expect(component.selectedTrendRow()).toBeUndefined();
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

    it('should report an error when the overview cannot be loaded', () => {
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(throwError(() => new Error('server down')));
        const errorSpy = vi.spyOn(alertService, 'error');

        component.ngOnInit();

        expect(errorSpy).toHaveBeenCalled();
        expect(component.loading()).toBeFalsy();
    });
});
