import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Subject, of, throwError } from 'rxjs';

import { FeatureUsageComponent, TAB_ENDPOINTS } from './feature-usage.component';
import { FeatureUsageService } from './feature-usage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { FeatureAdoption, FeatureInteraction, FeatureKind, FeatureUsageEndpoint, FeatureUsageOverview, FeatureUsageStatus, UserFeatureUsage } from './feature-usage.model';

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

describe('FeatureUsageComponent', () => {
    let component: FeatureUsageComponent;
    let fixture: ComponentFixture<FeatureUsageComponent>;
    let featureUsageService: FeatureUsageService;
    let alertService: AlertService;

    const editor = feature('PROGRAMMING_ONLINE_EDITOR', 'PROGRAMMING', { actionCount: 15, viewCount: 40, activeDays: 3, modules: ['programming'] });
    const variants = feature('HYPERION_VARIANT_GENERATION', 'AI_AUTHORING', { status: FeatureUsageStatus.ONLY_AUTOMATIC, automaticCount: 700, modules: ['hyperion'] });
    const faq = feature('FAQ', 'COMMUNICATION', { noActions: true, viewCount: 20, modules: ['communication'] });
    const science = feature('SCIENCE', 'COMPETENCIES', { status: FeatureUsageStatus.NOT_AVAILABLE, endpointCount: 0 });

    const endpoints: FeatureUsageEndpoint[] = [
        endpoint(1, {
            featureLabel: editor.feature,
            identifier: 'POST api/programming/participations/{participationId}/repository/commit',
            interaction: FeatureInteraction.ACTION,
            resource: 'RepositoryProgrammingExerciseParticipationResource',
            callCount: 15,
        }),
        endpoint(2, { featureLabel: variants.feature, module: 'hyperion', identifier: 'GET api/hyperion/variant-jobs', interaction: FeatureInteraction.AUTOMATIC, callCount: 700 }),
        endpoint(3, { featureLabel: faq.feature, module: 'communication', identifier: 'GET api/communication/courses/{courseId}/faqs', callCount: 20 }),
        endpoint(4, { featureLabel: 'configuration/old-label', identifier: 'GET api/programming/removed', retired: true }),
    ];

    const overview: FeatureUsageOverview = {
        days: 7,
        from: '2026-09-18',
        availableFeatures: 3,
        usedFeatures: 2,
        onlyAutomatic: 1,
        unusedFeatures: 0,
        notAvailable: 1,
        noActions: 1,
        retiredEndpoints: 1,
        actionCount: 15,
        viewCount: 60,
        automaticCount: 700,
        systemCount: 0,
        recordingSince: '2026-01-15T08:00:00Z',
        features: [editor, faq, science, variants],
        endpoints,
        roleDistribution: [{ callerRole: 'STUDENT', callCount: 75 }],
    };

    const adoption: FeatureAdoption[] = [
        { module: 'programming', key: 'online-editor', feature: editor.feature, count: 3, total: 10 },
        { module: 'course', key: 'communication', feature: 'MESSAGING', count: 1, total: 2 },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FeatureUsageComponent],
            providers: [
                MockProvider(FeatureUsageService, {
                    getOverview: () => of(overview),
                    getAdoption: () => of(adoption),
                    getFeatureTrend: () =>
                        of([
                            { usageDay: '2026-09-24', interaction: FeatureInteraction.ACTION, callCount: 2 },
                            { usageDay: '2026-09-24', interaction: FeatureInteraction.AUTOMATIC, callCount: 400 },
                            { usageDay: '2026-09-24', interaction: FeatureInteraction.SYSTEM, callCount: 3 },
                        ]),
                    getEndpointTrend: () => of([{ usageDay: '2026-09-23', interaction: FeatureInteraction.VIEW, callCount: 20 }]),
                    sendDigestEmail: () => of(undefined),
                }),
                MockProvider(AlertService, { error: vi.fn(), success: vi.fn() }),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(FeatureUsageComponent);
        component = fixture.componentInstance;
        featureUsageService = TestBed.inject(FeatureUsageService);
        alertService = TestBed.inject(AlertService);
    });

    it('should load the report and the adoption on init', () => {
        const overviewSpy = vi.spyOn(featureUsageService, 'getOverview');
        const adoptionSpy = vi.spyOn(featureUsageService, 'getAdoption');

        fixture.detectChanges();

        expect(overviewSpy).toHaveBeenCalledWith(30, undefined);
        expect(adoptionSpy).toHaveBeenCalledOnce();
        expect(component.overview()).toEqual(overview);
        expect(component.loading()).toBe(false);
    });

    it('should lead with use, not with calls, so automatic calls cannot make the page look busy', () => {
        fixture.detectChanges();

        expect(component.useCount()).toBe(75);
        const card = fixture.nativeElement.querySelector('[data-testid="kpi-calls"] [data-testid="kpi-value"]');
        expect(card.textContent.trim()).toBe('75');
        expect(fixture.nativeElement.querySelector('[data-testid="kpi-only-automatic"] [data-testid="kpi-value"]').textContent.trim()).toBe('1');
    });

    it('should hide features this deployment does not offer unless asked to show them', () => {
        fixture.detectChanges();

        expect(component.visibleFeatures().map((entry) => entry.feature)).toEqual([editor.feature, faq.feature, variants.feature]);

        component.showNotAvailable.set(true);
        expect(component.visibleFeatures().map((entry) => entry.feature)).toContain(science.feature);
    });

    it('should filter features by area', () => {
        fixture.detectChanges();

        component.selectedArea.set('AI_AUTHORING');

        expect(component.visibleFeatures().map((entry) => entry.feature)).toEqual([variants.feature]);
    });

    it('should find a feature by its name, its module and a path behind it', () => {
        fixture.detectChanges();

        component.searchTerm.set('faq');
        expect(component.visibleFeatures().map((entry) => entry.feature)).toEqual([faq.feature]);

        component.searchTerm.set('hyperion');
        expect(component.visibleFeatures().map((entry) => entry.feature)).toEqual([variants.feature]);

        // a path reaches the feature behind it, which is how a developer looks up an endpoint
        component.searchTerm.set('repository/commit');
        expect(component.visibleFeatures().map((entry) => entry.feature)).toEqual([editor.feature]);
    });

    it('should apply the search and the area to the endpoints and the adoption as well', () => {
        fixture.detectChanges();

        component.selectedArea.set('PROGRAMMING');
        expect(component.visibleEndpoints().map((entry) => entry.featureId)).toEqual([1]);
        expect(component.visibleAdoption().map((entry) => entry.key)).toEqual(['online-editor']);

        component.selectedArea.set('');
        component.searchTerm.set('removed');
        expect(component.visibleEndpoints().map((entry) => entry.featureId)).toEqual([4]);
    });

    it('should reload with the selected role and window and discard the stale report', () => {
        fixture.detectChanges();
        const overviewSpy = vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(new Subject<FeatureUsageOverview>());

        component.onCallerRoleChanged('STUDENT');
        expect(overviewSpy).toHaveBeenLastCalledWith(30, 'STUDENT');
        expect(component.overview()).toBeUndefined();

        component.onWindowChanged(90);
        expect(overviewSpy).toHaveBeenLastCalledWith(90, 'STUDENT');
    });

    it('should ignore a slower earlier response that arrives after a newer request', () => {
        fixture.detectChanges();
        const first = new Subject<FeatureUsageOverview>();
        const second = new Subject<FeatureUsageOverview>();
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValueOnce(first).mockReturnValueOnce(second);

        component.onWindowChanged(7);
        component.onWindowChanged(90);
        second.next({ ...overview, days: 90 });
        first.next({ ...overview, days: 7 });

        expect(component.overview()?.days).toBe(90);
    });

    it('should chart a feature as actions, views, automatic and system calls, over every day of the window', () => {
        fixture.detectChanges();
        const trendSpy = vi.spyOn(featureUsageService, 'getFeatureTrend');

        component.showTrend({ feature: variants });

        expect(trendSpy).toHaveBeenCalledWith(variants.feature, 30, undefined);
        const chart = component.trendChartData()!;
        expect(chart.labels).toHaveLength(7);
        // system calls are not made by the page, so they get their own line rather than joining the automatic one
        expect(chart.series.map((series) => series.data.at(-1))).toEqual([2, 0, 400, 3]);
    });

    it('should chart a single endpoint and keep the role filter', () => {
        fixture.detectChanges();
        component.selectedCallerRole.set('EDITOR');
        const trendSpy = vi.spyOn(featureUsageService, 'getEndpointTrend');

        component.showTrend({ endpoint: endpoints[2] });

        expect(trendSpy).toHaveBeenCalledWith([3], 30, 'EDITOR');
        expect(component.trendTarget()).toEqual({ title: endpoints[2].identifier, isFeature: false });
    });

    it('should close the trend when it cannot be loaded, instead of spinning forever', () => {
        fixture.detectChanges();
        vi.spyOn(featureUsageService, 'getFeatureTrend').mockReturnValue(throwError(() => new Error('boom')));

        component.showTrend({ feature: editor });

        expect(alertService.error).toHaveBeenCalledWith('boom');
        expect(component.trendTarget()).toBeUndefined();
    });

    it('should close the trend when the window changes, because it would chart the old window', () => {
        fixture.detectChanges();
        component.showTrend({ feature: editor });

        component.onWindowChanged(7);

        expect(component.trendTarget()).toBeUndefined();
    });

    it('should report whether the digest email could be sent', () => {
        fixture.detectChanges();

        component.sendDigestEmail();
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.featureUsage.digestSent');
        expect(component.sendingDigest()).toBe(false);

        vi.spyOn(featureUsageService, 'sendDigestEmail').mockReturnValue(throwError(() => new Error('no recipient')));
        component.sendDigestEmail();
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.featureUsage.digestFailed');
    });

    it('should report a failing overview request', () => {
        vi.spyOn(featureUsageService, 'getOverview').mockReturnValue(throwError(() => new Error('down')));

        fixture.detectChanges();

        expect(alertService.error).toHaveBeenCalledWith('down');
        expect(component.loading()).toBe(false);
    });

    it('should render the feature tree and the endpoints table', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="feature-tree"]')).not.toBeNull();

        component.activeTab.set(TAB_ENDPOINTS);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="endpoints-table"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('[data-testid^="endpoint-row-"]')).toHaveLength(4);
    });
});
