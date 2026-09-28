import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { FeatureUsageEndpointsComponent } from './feature-usage-endpoints.component';
import { FeatureInteraction, FeatureKind, FeatureUsageEndpoint } from '../feature-usage.model';

describe('FeatureUsageEndpointsComponent', () => {
    let component: FeatureUsageEndpointsComponent;
    let fixture: ComponentFixture<FeatureUsageEndpointsComponent>;

    const commit: FeatureUsageEndpoint = {
        featureId: 1,
        featureKind: FeatureKind.REST,
        module: 'programming',
        identifier: 'POST api/programming/commit',
        featureLabel: 'PROGRAMMING_ONLINE_EDITOR',
        interaction: FeatureInteraction.ACTION,
        resource: 'RepositoryResource',
        callCount: 10,
        errorCount: 5,
        durationSumMs: 1000,
        durationMaxMs: 400,
        activeDays: 3,
        lastUsedDay: '2026-09-20',
    };
    const clone: FeatureUsageEndpoint = {
        ...commit,
        featureId: 2,
        featureKind: FeatureKind.GIT,
        module: 'localvc',
        identifier: 'fetch/assignment',
        interaction: FeatureInteraction.VIEW,
        resource: undefined,
        callCount: 30,
        errorCount: 0,
        lastUsedDay: undefined,
    };
    const removed: FeatureUsageEndpoint = {
        ...commit,
        featureId: 3,
        identifier: 'GET api/programming/removed',
        featureLabel: 'configuration/old-label',
        retired: true,
        callCount: 0,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FeatureUsageEndpointsComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(FeatureUsageEndpointsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('endpoints', [commit, clone, removed]);
        fixture.componentRef.setInput('knownFeatures', new Set(['PROGRAMMING_ONLINE_EDITOR']));
        fixture.componentRef.setInput('allModulesLabel', 'All modules');
        fixture.detectChanges();
    });

    it('should list the busiest endpoints first and derive error rate and mean duration', () => {
        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([2, 1, 3]);
        const commitRow = component.rows()[1];
        expect(commitRow.errorRate).toBe(50);
        expect(commitRow.meanDurationMs).toBe(100);
    });

    it('should sort by any column and keep undefined values last', () => {
        component.onSort({ field: 'lastUsedDay', order: -1 } as any);
        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([1, 3, 2]);

        component.onSort({ field: 'identifier', order: 1 } as any);
        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([2, 3, 1]);
    });

    it('should filter by module', () => {
        expect(component.moduleOptions().map((option) => option.value)).toEqual(['', 'localvc', 'programming']);

        component.selectedModule.set('localvc');

        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([2]);
    });

    it('should fall back to all modules when the selected module is filtered away', () => {
        component.selectedModule.set('localvc');
        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([2]);

        // the page's area filter leaves only programming endpoints
        fixture.componentRef.setInput('endpoints', [commit, removed]);

        expect(component.selectedModule()).toBe('');
        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([1, 3]);
    });

    it('should keep the selected module while it is still among the endpoints', () => {
        component.selectedModule.set('programming');

        fixture.componentRef.setInput('endpoints', [commit, removed]);

        expect(component.selectedModule()).toBe('programming');
        expect(component.rows().map((row) => row.endpoint.featureId)).toEqual([1, 3]);
    });

    it('should mark endpoints that belong to no feature and git entries without a controller', () => {
        const removedRow: HTMLElement = fixture.nativeElement.querySelector('[data-testid="endpoint-row-3"]');
        const cloneRow: HTMLElement = fixture.nativeElement.querySelector('[data-testid="endpoint-row-2"]');

        expect(removedRow.textContent).toContain('artemisApp.featureUsage.uncatalogued');
        expect(removedRow.textContent).toContain('artemisApp.featureUsage.retiredTag');
        expect(cloneRow.textContent).toContain('artemisApp.featureUsage.kind.GIT');
    });

    it('should ask for the trend of an endpoint with calls', () => {
        const emitted = vi.fn();
        component.trendRequested.subscribe(emitted);

        (fixture.nativeElement.querySelector('[data-testid="endpoint-row-1"] button') as HTMLButtonElement).click();

        expect(emitted).toHaveBeenCalledWith(commit);
        // nothing to chart for an endpoint nobody called
        expect(fixture.nativeElement.querySelector('[data-testid="endpoint-row-3"] button')).toBeNull();
    });
});
