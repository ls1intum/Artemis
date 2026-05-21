/**
 * Vitest tests for MetricsDatasourceComponent.
 * Tests the datasource metrics display component.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef } from '@angular/core';

import { MetricsDatasourceComponent } from 'app/core/admin/metrics/blocks/metrics-datasource/metrics-datasource.component';
import { Databases } from 'app/core/admin/metrics/metrics.model';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

describe('MetricsDatasourceComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MetricsDatasourceComponent;
    let componentRef: ComponentRef<MetricsDatasourceComponent>;
    let fixture: ComponentFixture<MetricsDatasourceComponent>;

    const mockDatasourceMetrics: Databases = {
        min: { value: 5 },
        max: { value: 20 },
        idle: { value: 10 },
        active: { value: 5 },
        pending: { value: 0 },
        connections: { value: 15 },
        usage: { count: 100, mean: 0.25, max: 0.5, totalTime: 1000, '0.0': 0.1, '0.5': 0.2, '0.75': 0.3, '0.95': 0.4, '0.99': 0.45, '1.0': 0.5 },
        acquire: { count: 100, mean: 5.5, max: 20, totalTime: 550, '0.0': 1, '0.5': 2, '0.75': 3, '0.95': 5, '0.99': 8, '1.0': 15 },
        creation: { count: 50, mean: 10.2, max: 35, totalTime: 510, '0.0': 5, '0.5': 8, '0.75': 10, '0.95': 15, '0.99': 20, '1.0': 30 },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsDatasourceComponent],
        })
            .overrideTemplate(MetricsDatasourceComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsDatasourceComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    it('should create component', () => {
        componentRef.setInput('datasourceMetrics', mockDatasourceMetrics);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should accept datasourceMetrics input', () => {
        componentRef.setInput('datasourceMetrics', mockDatasourceMetrics);
        fixture.detectChanges();
        expect(component.datasourceMetrics()).toEqual(mockDatasourceMetrics);
    });

    it('should have updating default to false', () => {
        componentRef.setInput('datasourceMetrics', mockDatasourceMetrics);
        fixture.detectChanges();
        expect(component.updating()).toBe(false);
    });

    it('should accept updating input as true', () => {
        componentRef.setInput('datasourceMetrics', mockDatasourceMetrics);
        componentRef.setInput('updating', true);
        fixture.detectChanges();
        expect(component.updating()).toBe(true);
    });

    it('should have filterNaN utility available', () => {
        componentRef.setInput('datasourceMetrics', mockDatasourceMetrics);
        fixture.detectChanges();
        expect(component['filterNaN']).toBe(filterNaN);
    });

    it('should handle datasource metrics with zero values', () => {
        const zeroMetrics: Databases = {
            min: { value: 0 },
            max: { value: 0 },
            idle: { value: 0 },
            active: { value: 0 },
            pending: { value: 0 },
            connections: { value: 0 },
            usage: { count: 0, mean: 0, max: 0, totalTime: 0, '0.0': 0, '0.5': 0, '0.75': 0, '0.95': 0, '0.99': 0, '1.0': 0 },
            acquire: { count: 0, mean: 0, max: 0, totalTime: 0, '0.0': 0, '0.5': 0, '0.75': 0, '0.95': 0, '0.99': 0, '1.0': 0 },
            creation: { count: 0, mean: 0, max: 0, totalTime: 0, '0.0': 0, '0.5': 0, '0.75': 0, '0.95': 0, '0.99': 0, '1.0': 0 },
        };
        componentRef.setInput('datasourceMetrics', zeroMetrics);
        fixture.detectChanges();
        expect(component.datasourceMetrics().active.value).toBe(0);
        expect(component.datasourceMetrics().usage.mean).toBe(0);
    });

    it('should handle high usage datasource metrics', () => {
        const highUsageMetrics: Databases = {
            ...mockDatasourceMetrics,
            usage: { count: 100, mean: 0.95, max: 1.0, totalTime: 9500, '0.0': 0.8, '0.5': 0.9, '0.75': 0.95, '0.95': 0.98, '0.99': 0.99, '1.0': 1.0 },
            active: { value: 19 },
            idle: { value: 1 },
        };
        componentRef.setInput('datasourceMetrics', highUsageMetrics);
        fixture.detectChanges();
        expect(component.datasourceMetrics().usage.mean).toBe(0.95);
    });
});
