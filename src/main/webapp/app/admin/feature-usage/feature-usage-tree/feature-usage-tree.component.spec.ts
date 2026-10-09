import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { FeatureUsageTreeComponent } from './feature-usage-tree.component';
import { FeatureInteraction, FeatureKind, FeatureUsageEndpoint, FeatureUsageStatus, UserFeatureUsage } from '../feature-usage.model';

describe('FeatureUsageTreeComponent', () => {
    let component: FeatureUsageTreeComponent;
    let fixture: ComponentFixture<FeatureUsageTreeComponent>;

    const variants: UserFeatureUsage = {
        feature: 'HYPERION_VARIANT_GENERATION',
        area: 'AI_AUTHORING',
        status: FeatureUsageStatus.ONLY_AUTOMATIC,
        noActions: false,
        actionCount: 0,
        viewCount: 0,
        automaticCount: 700,
        systemCount: 0,
        errorCount: 0,
        durationSumMs: 0,
        durationMaxMs: 0,
        activeDays: 0,
        actionDays: 0,
        endpointCount: 2,
        hasActionEndpoints: true,
    };
    const faq: UserFeatureUsage = { ...variants, feature: 'FAQ', area: 'COMMUNICATION', status: FeatureUsageStatus.USED, noActions: true, viewCount: 20, automaticCount: 0 };

    const probe: FeatureUsageEndpoint = {
        featureId: 1,
        featureKind: FeatureKind.REST,
        module: 'hyperion',
        identifier: 'GET api/hyperion/variant-jobs',
        featureLabel: variants.feature,
        interaction: FeatureInteraction.AUTOMATIC,
        resource: 'HyperionExerciseVariantResource',
        callCount: 700,
        errorCount: 7,
        durationSumMs: 700,
        durationMaxMs: 5,
        activeDays: 2,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FeatureUsageTreeComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(FeatureUsageTreeComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('features', [faq, variants]);
        fixture.componentRef.setInput('endpointsByFeature', new Map([[variants.feature, [probe]]]));
        fixture.componentRef.setInput('adoptionByFeature', new Map([[faq.feature, [{ module: 'communication', key: 'faq', feature: faq.feature, count: 2, total: 5 }]]]));
        fixture.detectChanges();
    });

    it('should start with the areas collapsed', () => {
        expect(component.rows().map((row) => row.key)).toEqual(['COMMUNICATION', 'AI_AUTHORING']);
        expect(fixture.nativeElement.querySelectorAll('[data-kind="area"]')).toHaveLength(2);
        expect(fixture.nativeElement.querySelectorAll('[data-kind="feature"]')).toHaveLength(0);
    });

    it('should show a feature with its status, the viewed-only flag and its adoption when its area is expanded', () => {
        component.toggle('COMMUNICATION');
        fixture.detectChanges();

        const row: HTMLElement = fixture.nativeElement.querySelector('[data-testid="tree-row-COMMUNICATION/FAQ"]');
        expect(row.getAttribute('data-status')).toBe(FeatureUsageStatus.USED);
        expect(row.textContent).toContain('artemisApp.featureUsage.noActionsTag');
        expect(row.textContent).toContain('artemisApp.featureUsage.adoptionTag');
    });

    it('should collapse an expanded row again', () => {
        component.toggle('COMMUNICATION');
        component.toggle('COMMUNICATION');

        expect(component.rows()).toHaveLength(2);
    });

    it('should expand and collapse everything', () => {
        component.expandAll();
        fixture.detectChanges();

        expect(component.rows().map((row) => row.kind)).toEqual(['area', 'feature', 'area', 'feature', 'resource', 'endpoint']);
        const endpointRow: HTMLElement = fixture.nativeElement.querySelector('[data-kind="endpoint"]');
        expect(endpointRow.querySelector('[data-testid="endpoint-interaction"]')!.textContent).toContain('artemisApp.featureUsage.interaction.AUTOMATIC');

        component.collapseAll();
        expect(component.rows()).toHaveLength(2);
    });

    it('should report the error rate and duration of an endpoint over its own calls', () => {
        component.expandAll();
        const endpointRow = component.rows().find((row) => row.kind === 'endpoint')!;

        expect(component.errorRate(endpointRow)).toBe(1);
        expect(component.meanDuration(endpointRow)).toBe(1);
        // the feature has no use, so it reports neither
        const featureRow = component.rows().find((row) => row.feature === variants)!;
        expect(component.errorRate(featureRow)).toBeUndefined();
        expect(component.meanDuration(featureRow)).toBeUndefined();
        expect(component.hasCalls(featureRow)).toBe(true);
    });

    it('should ask for the trend of a feature or of an endpoint', () => {
        const emitted = vi.fn();
        component.trendRequested.subscribe(emitted);
        component.expandAll();

        component.showTrend(component.rows().find((row) => row.feature === variants)!);
        component.showTrend(component.rows().find((row) => row.kind === 'endpoint')!);
        component.showTrend(component.rows()[0]);

        expect(emitted).toHaveBeenCalledTimes(2);
        expect(emitted).toHaveBeenNthCalledWith(1, { feature: variants });
        expect(emitted).toHaveBeenNthCalledWith(2, { endpoint: probe });
    });
});
