/**
 * Vitest tests for JvmMemoryComponent.
 * Tests the JVM memory metrics display component.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentRef } from '@angular/core';

import { JvmMemoryComponent } from 'app/core/admin/metrics/blocks/jvm-memory/jvm-memory.component';
import { JvmMetrics } from 'app/core/admin/metrics/metrics.model';

describe('JvmMemoryComponent', () => {
    setupTestBed({ zoneless: true });

    let component: JvmMemoryComponent;
    let componentRef: ComponentRef<JvmMemoryComponent>;
    let fixture: ComponentFixture<JvmMemoryComponent>;

    const mockJvmMemoryMetrics: { [key: string]: JvmMetrics } = {
        heap: {
            committed: 536870912,
            max: 1073741824,
            used: 268435456,
        },
        'non-heap': {
            committed: 134217728,
            max: -1,
            used: 67108864,
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [JvmMemoryComponent],
        })
            .overrideTemplate(JvmMemoryComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(JvmMemoryComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
    });

    it('should create component', () => {
        componentRef.setInput('jvmMemoryMetrics', mockJvmMemoryMetrics);
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should accept jvmMemoryMetrics input', () => {
        componentRef.setInput('jvmMemoryMetrics', mockJvmMemoryMetrics);
        fixture.detectChanges();
        expect(component.jvmMemoryMetrics()).toEqual(mockJvmMemoryMetrics);
    });

    it('should have updating default to false', () => {
        componentRef.setInput('jvmMemoryMetrics', mockJvmMemoryMetrics);
        fixture.detectChanges();
        expect(component.updating()).toBe(false);
    });

    it('should accept updating input as true', () => {
        componentRef.setInput('jvmMemoryMetrics', mockJvmMemoryMetrics);
        componentRef.setInput('updating', true);
        fixture.detectChanges();
        expect(component.updating()).toBe(true);
    });

    it('should handle metrics with multiple memory types', () => {
        const metricsWithMultipleTypes = {
            ...mockJvmMemoryMetrics,
            'PS Eden Space': { committed: 100000, max: 200000, used: 50000 },
            'PS Survivor Space': { committed: 10000, max: 20000, used: 5000 },
        };
        componentRef.setInput('jvmMemoryMetrics', metricsWithMultipleTypes);
        fixture.detectChanges();
        expect(Object.keys(component.jvmMemoryMetrics())).toHaveLength(4);
    });

    it('should handle empty metrics object', () => {
        componentRef.setInput('jvmMemoryMetrics', {});
        fixture.detectChanges();
        expect(Object.keys(component.jvmMemoryMetrics())).toHaveLength(0);
    });
});
