/**
 * Vitest tests for MetricsGarbageCollectorComponent.
 * Tests the garbage collector metrics display component.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef } from '@angular/core';

import { MetricsGarbageCollectorComponent } from 'app/core/admin/metrics/blocks/metrics-garbagecollector/metrics-garbagecollector.component';
import { GarbageCollector } from 'app/core/admin/metrics/metrics.model';

describe('MetricsGarbageCollectorComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MetricsGarbageCollectorComponent;
    let componentRef: ComponentRef<MetricsGarbageCollectorComponent>;
    let fixture: ComponentFixture<MetricsGarbageCollectorComponent>;

    const mockGarbageCollectorMetrics: GarbageCollector = {
        'jvm.gc.max.data.size': 1073741824,
        'jvm.gc.live.data.size': 536870912,
        'jvm.gc.memory.promoted': 134217728,
        'jvm.gc.memory.allocated': 268435456,
        'jvm.gc.pause': { count: 10, mean: 50, max: 200, totalTime: 500, '0.0': 10, '0.5': 40, '0.75': 60, '0.95': 100, '0.99': 150, '1.0': 200 },
        classesLoaded: 15000,
        classesUnloaded: 500,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsGarbageCollectorComponent],
        })
            .overrideTemplate(MetricsGarbageCollectorComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(MetricsGarbageCollectorComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    it('should create component', () => {
        componentRef.setInput('garbageCollectorMetrics', mockGarbageCollectorMetrics);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should accept garbageCollectorMetrics input', () => {
        componentRef.setInput('garbageCollectorMetrics', mockGarbageCollectorMetrics);
        fixture.detectChanges();
        expect(component.garbageCollectorMetrics()).toEqual(mockGarbageCollectorMetrics);
    });

    it('should have updating default to false', () => {
        componentRef.setInput('garbageCollectorMetrics', mockGarbageCollectorMetrics);
        fixture.detectChanges();
        expect(component.updating()).toBe(false);
    });

    it('should accept updating input as true', () => {
        componentRef.setInput('garbageCollectorMetrics', mockGarbageCollectorMetrics);
        componentRef.setInput('updating', true);
        fixture.detectChanges();
        expect(component.updating()).toBe(true);
    });

    it('should handle metrics with zero values', () => {
        const zeroMetrics: GarbageCollector = {
            'jvm.gc.max.data.size': 0,
            'jvm.gc.live.data.size': 0,
            'jvm.gc.memory.promoted': 0,
            'jvm.gc.memory.allocated': 0,
            'jvm.gc.pause': { count: 0, mean: 0, max: 0, totalTime: 0, '0.0': 0, '0.5': 0, '0.75': 0, '0.95': 0, '0.99': 0, '1.0': 0 },
            classesLoaded: 0,
            classesUnloaded: 0,
        };
        componentRef.setInput('garbageCollectorMetrics', zeroMetrics);
        fixture.detectChanges();
        expect(component.garbageCollectorMetrics().classesLoaded).toBe(0);
    });

    it('should handle metrics with large values', () => {
        const largeMetrics: GarbageCollector = {
            'jvm.gc.max.data.size': 17179869184, // 16 GB
            'jvm.gc.live.data.size': 8589934592, // 8 GB
            'jvm.gc.memory.promoted': 4294967296, // 4 GB
            'jvm.gc.memory.allocated': 34359738368, // 32 GB
            'jvm.gc.pause': { count: 1000, mean: 100, max: 500, totalTime: 100000, '0.0': 20, '0.5': 80, '0.75': 120, '0.95': 200, '0.99': 400, '1.0': 500 },
            classesLoaded: 100000,
            classesUnloaded: 5000,
        };
        componentRef.setInput('garbageCollectorMetrics', largeMetrics);
        fixture.detectChanges();
        expect(component.garbageCollectorMetrics()['jvm.gc.max.data.size']).toBe(17179869184);
    });
});
