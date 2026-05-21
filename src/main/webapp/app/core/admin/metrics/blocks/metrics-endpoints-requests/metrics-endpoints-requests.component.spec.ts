/**
 * Vitest tests for MetricsEndpointsRequestsComponent.
 * Tests the endpoints requests metrics display component.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef } from '@angular/core';

import { MetricsEndpointsRequestsComponent } from 'app/core/admin/metrics/blocks/metrics-endpoints-requests/metrics-endpoints-requests.component';
import { HttpMethod, Services } from 'app/core/admin/metrics/metrics.model';

describe('MetricsEndpointsRequestsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MetricsEndpointsRequestsComponent;
    let componentRef: ComponentRef<MetricsEndpointsRequestsComponent>;
    let fixture: ComponentFixture<MetricsEndpointsRequestsComponent>;

    const mockEndpointsRequestsMetrics: Services = {
        ExerciseResource: {
            [HttpMethod.Get]: { count: 1500, max: 250, mean: 45 },
            [HttpMethod.Post]: { count: 200, max: 500, mean: 120 },
        },
        CourseResource: {
            [HttpMethod.Get]: { count: 3000, max: 100, mean: 25 },
            [HttpMethod.Put]: { count: 2500, max: 150, mean: 35 },
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsEndpointsRequestsComponent],
        })
            .overrideTemplate(MetricsEndpointsRequestsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsEndpointsRequestsComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    it('should create component', () => {
        componentRef.setInput('endpointsRequestsMetrics', mockEndpointsRequestsMetrics);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should accept endpointsRequestsMetrics input', () => {
        componentRef.setInput('endpointsRequestsMetrics', mockEndpointsRequestsMetrics);
        fixture.detectChanges();
        expect(component.endpointsRequestsMetrics()).toEqual(mockEndpointsRequestsMetrics);
    });

    it('should have updating default to false', () => {
        componentRef.setInput('endpointsRequestsMetrics', mockEndpointsRequestsMetrics);
        fixture.detectChanges();
        expect(component.updating()).toBe(false);
    });

    it('should accept updating input as true', () => {
        componentRef.setInput('endpointsRequestsMetrics', mockEndpointsRequestsMetrics);
        componentRef.setInput('updating', true);
        fixture.detectChanges();
        expect(component.updating()).toBe(true);
    });

    it('should handle empty services metrics', () => {
        componentRef.setInput('endpointsRequestsMetrics', {});
        fixture.detectChanges();
        expect(Object.keys(component.endpointsRequestsMetrics())).toHaveLength(0);
    });

    it('should handle services with many endpoints', () => {
        const metricsWithManyEndpoints: Services = {
            ...mockEndpointsRequestsMetrics,
            UserResource: {
                [HttpMethod.Get]: { count: 500, max: 75, mean: 20 },
                [HttpMethod.Post]: { count: 100, max: 200, mean: 80 },
                [HttpMethod.Put]: { count: 50, max: 150, mean: 60 },
                [HttpMethod.Delete]: { count: 25, max: 100, mean: 40 },
            },
        };
        componentRef.setInput('endpointsRequestsMetrics', metricsWithManyEndpoints);
        fixture.detectChanges();
        expect(Object.keys(component.endpointsRequestsMetrics())).toHaveLength(3);
    });
});
