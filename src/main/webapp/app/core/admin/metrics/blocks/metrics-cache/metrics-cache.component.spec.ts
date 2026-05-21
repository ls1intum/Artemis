/**
 * Vitest tests for MetricsCacheComponent.
 * Tests the cache metrics display component.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef } from '@angular/core';

import { MetricsCacheComponent } from 'app/core/admin/metrics/blocks/metrics-cache/metrics-cache.component';
import { CacheMetrics } from 'app/core/admin/metrics/metrics.model';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

describe('MetricsCacheComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MetricsCacheComponent;
    let componentRef: ComponentRef<MetricsCacheComponent>;
    let fixture: ComponentFixture<MetricsCacheComponent>;

    const mockCacheMetrics: { [key: string]: CacheMetrics } = {
        usersByLogin: {
            'cache.gets.hit': 1500,
            'cache.gets.miss': 200,
            'cache.puts': 300,
            'cache.removals': 50,
            'cache.evictions': 100,
        },
        coursesByShortName: {
            'cache.gets.hit': 2500,
            'cache.gets.miss': 150,
            'cache.puts': 500,
            'cache.removals': 25,
            'cache.evictions': 75,
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsCacheComponent],
        })
            .overrideTemplate(MetricsCacheComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsCacheComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    it('should create component', () => {
        componentRef.setInput('cacheMetrics', mockCacheMetrics);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should accept cacheMetrics input', () => {
        componentRef.setInput('cacheMetrics', mockCacheMetrics);
        fixture.detectChanges();
        expect(component.cacheMetrics()).toEqual(mockCacheMetrics);
    });

    it('should have updating default to false', () => {
        componentRef.setInput('cacheMetrics', mockCacheMetrics);
        fixture.detectChanges();
        expect(component.updating()).toBe(false);
    });

    it('should accept updating input as true', () => {
        componentRef.setInput('cacheMetrics', mockCacheMetrics);
        componentRef.setInput('updating', true);
        fixture.detectChanges();
        expect(component.updating()).toBe(true);
    });

    it('should have filterNaN utility available', () => {
        componentRef.setInput('cacheMetrics', mockCacheMetrics);
        fixture.detectChanges();
        expect(component['filterNaN']).toBe(filterNaN);
    });

    it('should handle empty cache metrics', () => {
        componentRef.setInput('cacheMetrics', {});
        fixture.detectChanges();
        expect(Object.keys(component.cacheMetrics())).toHaveLength(0);
    });

    it('should handle metrics with various cache entries', () => {
        const metricsWithManyCaches = {
            ...mockCacheMetrics,
            exercisesByTitle: { 'cache.gets.hit': 500, 'cache.gets.miss': 50, 'cache.puts': 100, 'cache.removals': 10, 'cache.evictions': 20 },
        };
        componentRef.setInput('cacheMetrics', metricsWithManyCaches);
        fixture.detectChanges();
        expect(Object.keys(component.cacheMetrics())).toHaveLength(3);
    });
});
